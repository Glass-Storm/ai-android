@file:Suppress("MagicNumber")

package hk.edu.hkmu.demo.scenario

import hk.edu.hkmu.core.engine.AsrEngine
import hk.edu.hkmu.core.engine.AudioChunk
import hk.edu.hkmu.core.engine.TranscriptionSegment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList

/**
 * Scenario 6: cold-flow semantics.
 *
 * Every engine returns a cold [Flow], so nothing is loaded until it is collected. [RecordingAsrEngine]
 * records when its flow body actually runs; the scenario prints the record before and after
 * collection to show the body (where a real engine would load its model) is deferred.
 */
object ColdFlowScenario {
    /** Builds a cold flow, reads the record, then collects and reads it again. */
    suspend fun run(): String {
        val engine = RecordingAsrEngine()
        val coldFlow = engine.transcribe(flowOf(AudioChunk(floatArrayOf(0f))))
        val beforeCollect = engine.events.toList()
        val emitted = coldFlow.toList().map { it.text }

        return buildString {
            appendLine("A cold Flow does no work until it is collected.")
            appendLine("  created flow via engine.transcribe(...)")
            appendLine("  before collect: recorded events = $beforeCollect (empty => no model work started)")
            appendLine("  collected: emitted = $emitted")
            append("  after collect: recorded events = ${engine.events}")
        }
    }
}

/** A cold [AsrEngine] that records creation only when its flow body runs, i.e. on collection. */
private class RecordingAsrEngine : AsrEngine {

    /** Creation events, appended when (and only when) the cold flow body executes. */
    val events: MutableList<String> = mutableListOf()

    override fun transcribe(audio: Flow<AudioChunk>): Flow<TranscriptionSegment> = flow {
        events += "flow body ran: a real engine would load its model here"
        audio.collect { }
        emit(TranscriptionSegment("cold-start", 0L, 10L))
    }
}
