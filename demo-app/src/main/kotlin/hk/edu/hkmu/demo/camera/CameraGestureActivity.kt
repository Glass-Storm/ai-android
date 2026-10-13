@file:Suppress("MagicNumber", "TooManyFunctions")

package hk.edu.hkmu.demo.camera

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.demo.DemoEnvironment
import hk.edu.hkmu.demo.media.CameraFrameEncoder
import hk.edu.hkmu.demo.model.AssetProvisioner
import hk.edu.hkmu.demo.model.ModelCatalog
import hk.edu.hkmu.sign.gesture.GesturePrediction
import hk.edu.hkmu.sign.gesture.MediaPipeGestureRecognizer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val TAG = "CameraGesture"
private const val REQUEST_CAMERA = 41
private const val PADDING_DP = 16
private const val LABEL_TEXT_SIZE_SP = 28f
private const val STATUS_TEXT_SIZE_SP = 13f
private const val PREVIEW_WEIGHT = 1f
private const val PERCENT = 100
private const val NO_GESTURE = "—"
private const val HEADER =
    "Live camera: CameraX ImageAnalysis → JPEG still → BitmapFactory.decodeByteArray → " +
        "MediaPipeGestureRecognizer.recognize(bitmap). Per-image static gestures, not sentences. " +
        "MediaPipe native is arm64-v8a only, so this screen runs on an arm64 device with a camera."
private const val NATIVE_UNAVAILABLE =
    "Gesture recognizer native unavailable: MediaPipe Tasks Vision ships arm64-v8a only; " +
        "run this on an arm64 phone."

/**
 * Live-camera counterpart of [hk.edu.hkmu.demo.scenario.GestureScenario]: the mp4 demo decodes a
 * bundled clip, this screen sources frames from the device camera. It is the same per-image
 * MediaPipe path — each analysis frame is encoded to a JPEG still, decoded back with
 * `BitmapFactory.decodeByteArray`, and handed to `MediaPipeGestureRecognizer.recognize(bitmap)` —
 * so the recognised label updates live under the preview.
 *
 * A CameraX analyser runs on its own executor and must not block on the UI, yet unbounded queueing
 * of frames would both leak memory and misreport latency. The §3 recipe is therefore applied
 * verbatim: the analyser hands each JPEG to a **bounded** channel (capacity 1) with
 * `trySendBlocking`, so a slow recognizer throttles the camera; CameraX then drops superseded
 * frames. Every `ImageProxy` is closed in the same pass.
 */
class CameraGestureActivity : Activity(), LifecycleOwner {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val frames = Channel<ByteArray>(capacity = 1)
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // The framework Activity is not a LifecycleOwner; CameraX binds to this registry. The registry is
    // driven INITIALIZED → CREATED in onCreate, STARTED/RESUMED while the screen is foregrounded, and
    // back to CREATED on stop so CameraX releases the camera when the app is backgrounded, then to
    // DESTROYED on teardown.
    private val lifecycleRegistry = LifecycleRegistry(this)

    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    private lateinit var environment: DemoEnvironment
    private lateinit var labelView: TextView
    private lateinit var statusView: TextView
    private lateinit var previewView: PreviewView
    private var recognizer: MediaPipeGestureRecognizer? = null
    private var cameraController: LifecycleCameraController? = null
    private var consumerJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        environment = DemoEnvironment(this)
        setContentView(buildContentView())
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        prepareGestureModel()
    }

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onResume() {
        super.onResume()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onPause() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        super.onPause()
    }

    override fun onStop() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onStop()
    }

    override fun onDestroy() {
        frames.close()
        scope.cancel()
        analysisExecutor.shutdown()
        // `recognize()` is blocking and non-cancellable, so cancelAndJoin waits for any in-flight
        // recognition to finish before the native model is closed; otherwise the consumer could still
        // be inside the native recognizer when close() tears it down (use-after-teardown).
        runBlocking { consumerJob?.cancelAndJoin() }
        recognizer?.close()
        cameraController?.unbind()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CAMERA) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            statusView.text = "Camera permission denied; cannot show the live gesture example."
        }
    }

    private fun prepareGestureModel() {
        statusView.text = "Preparing gesture model…"
        scope.launch {
            val transcript = AssetProvisioner(assets, environment.modelsRoot)
                .provision(listOf(ModelCatalog.SIGN_GESTURE_RECOGNIZER))
            statusView.text = transcript
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
            }
        }
    }

    private fun startCamera() {
        if (cameraController != null) return
        startConsumer()
        val controller = LifecycleCameraController(this).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            setImageAnalysisAnalyzer(analysisExecutor) { proxy -> submit(proxy) }
        }
        previewView.controller = controller
        cameraController = controller
        runCatching { controller.bindToLifecycle(this) }
            .onFailure { statusView.text = "Camera unavailable: ${it.message}" }
    }

    private fun submit(proxy: ImageProxy) {
        // The §3 recipe: every ImageProxy is closed in the SAME pass, whatever happens. trySendBlocking
        // blocks while the bounded channel is full (backpressure) and can be interrupted on teardown,
        // so the close must live in a finally rather than trailing the send.
        try {
            val jpeg = runCatching { CameraFrameEncoder.toJpeg(proxy) }
                .onFailure { Log.w(TAG, "frame encode failed: ${it.message}") }
                .getOrNull() ?: return
            frames.trySendBlocking(jpeg)
        } finally {
            proxy.close()
        }
    }

    private fun startConsumer() {
        val gestureRecognizer = MediaPipeGestureRecognizer.create(environment.context, environment.dir("gesture"))
        recognizer = gestureRecognizer
        consumerJob = scope.launch(Dispatchers.Default) {
            for (jpeg in frames) {
                if (!render(gestureRecognizer, jpeg)) return@launch
            }
        }
    }

    /** Recognizes one JPEG and returns whether recognition may continue (false ends the consumer). */
    private suspend fun render(gestureRecognizer: MediaPipeGestureRecognizer, jpeg: ByteArray): Boolean {
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return true
        return try {
            val prediction = gestureRecognizer.recognize(bitmap)
            labelView.post { showLabel(prediction) }
            true
        } catch (error: LinkageError) {
            Log.w(TAG, NATIVE_UNAVAILABLE, error)
            statusView.post { statusView.text = NATIVE_UNAVAILABLE }
            false
        } catch (error: AiAndroidException) {
            val detail = "${error.javaClass.simpleName}: ${error.message}"
            statusView.post { statusView.text = "Gesture recognizer unavailable: $detail" }
            false
        } finally {
            bitmap.recycle()
        }
    }

    private fun showLabel(prediction: GesturePrediction?) {
        labelView.text = if (prediction == null) {
            NO_GESTURE
        } else {
            val percent = (prediction.score * PERCENT).roundToInt()
            "${prediction.label}  $percent%"
        }
    }

    private fun buildContentView(): View {
        labelView = TextView(this).apply {
            text = NO_GESTURE
            textSize = LABEL_TEXT_SIZE_SP
            gravity = Gravity.CENTER
            setPadding(0, dp(PADDING_DP), 0, dp(PADDING_DP))
        }
        statusView = TextView(this)
        statusView.textSize = STATUS_TEXT_SIZE_SP
        previewView = PreviewView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, PREVIEW_WEIGHT)
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(PADDING_DP)
            setPadding(pad, pad, pad, pad)
            addView(
                TextView(this@CameraGestureActivity).apply {
                    text = HEADER
                    textSize = STATUS_TEXT_SIZE_SP
                }
            )
            addView(previewView)
            addView(labelView)
            addView(statusView)
            addView(
                Button(this@CameraGestureActivity).apply {
                    text = "Stop camera"
                    setOnClickListener { finish() }
                }
            )
        }
        return column
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
