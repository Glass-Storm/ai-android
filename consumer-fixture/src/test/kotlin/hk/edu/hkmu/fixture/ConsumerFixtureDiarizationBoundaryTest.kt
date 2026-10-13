package hk.edu.hkmu.fixture

import hk.edu.hkmu.diarize.DiarizationModelSpec
import hk.edu.hkmu.diarize.DiarizationModels
import hk.edu.hkmu.diarize.SherpaDiarizationEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Proves the `:diarize` public API is reachable from the unpublished consumer fixture using only its
 * published surface — no internal or sherpa binding class is touched (mirrors how the fixture
 * exercises the `:sign` and `:translate` public API elsewhere).
 *
 * Declaring `:diarize` as an `implementation` dependency also applies its shipped
 * `consumer-rules.pro` to the minified fixture APK, so the diarizer's JNI keep rules are verified
 * alongside the other engines'.
 */
class ConsumerFixtureDiarizationBoundaryTest {

    @Test
    fun `diarize public API resolves from the fixture`() {
        val spec: DiarizationModelSpec = DiarizationModels.Pyannote3Dspeaker

        assertEquals(DiarizationModels.PYANNOTE_3DSPEAKER_ID, SherpaDiarizationEngine.MODEL_ID)
        assertEquals(spec.id, SherpaDiarizationEngine.MODEL_ID)
        assertEquals(DiarizationModels.PYANNOTE_SEGMENTATION_DIRECTORY, spec.segmentationDirectory)
        assertEquals(DiarizationModels.PYANNOTE_SEGMENTATION_FILE, spec.segmentationFile)
        assertEquals(DiarizationModels.EMBEDDING_FILE, spec.embeddingFile)
    }
}
