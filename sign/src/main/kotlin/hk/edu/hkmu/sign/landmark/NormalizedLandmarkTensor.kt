package hk.edu.hkmu.sign.landmark

/**
 * The normalized input tensor for one classifier window.
 *
 * [data] is the flattened, row-major payload matching [shape]; [dtype] is what the classifier graph
 * expects. The payload is pure JVM state: no native handle, no Android type, so it can be asserted
 * directly in a host test.
 *
 * @property spec The [ClassifierInputSpec] this tensor was produced for.
 * @property shape Tensor shape including the leading batch dimension.
 * @property dtype Element type.
 * @property data Flattened float payload (`elementCount` entries).
 */
class NormalizedLandmarkTensor(
    val spec: ClassifierInputSpec,
    val shape: List<Int>,
    val dtype: TensorDtype,
    val data: FloatArray
) {
    /** Number of values in [data]. */
    val elementCount: Int get() = data.size

    /** Inclusive `[min, max]` over [data], or `0f..0f` when empty. */
    fun valueRange(): ClosedFloatingPointRange<Float> {
        if (data.isEmpty()) {
            return 0f..0f
        }
        var min = data[0]
        var max = data[0]
        for (value in data) {
            if (value < min) min = value
            if (value > max) max = value
        }
        return min..max
    }
}
