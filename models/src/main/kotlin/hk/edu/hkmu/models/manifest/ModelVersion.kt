package hk.edu.hkmu.models.manifest

/**
 * Ordered dotted-numeric model version (e.g. `1`, `1.2`, `2026.10.12`).
 *
 * Comparison is component-wise with shorter versions zero-padded, so `1.2` compares equal to
 * `1.2.0` and `1.10` is newer than `1.9`. Only numeric ordering is modelled; pre-release and build
 * metadata are out of scope.
 */
@ConsistentCopyVisibility
data class ModelVersion private constructor(
    private val segments: List<Int>,
    val raw: String
) : Comparable<ModelVersion> {

    override fun compareTo(other: ModelVersion): Int {
        val width = maxOf(segments.size, other.segments.size)
        for (index in 0 until width) {
            val left = segments.getOrElse(index) { 0 }
            val right = other.segments.getOrElse(index) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }

    override fun toString(): String = raw

    companion object {
        private const val MAX_SEGMENTS = 4

        /**
         * Parses [raw] into an ordered version.
         *
         * @throws IllegalArgumentException when a segment is non-numeric, negative, or the version
         *   has more than [MAX_SEGMENTS] segments.
         */
        fun parse(raw: String): ModelVersion {
            val segments = raw.split('.').map { part ->
                part.toIntOrNull()?.takeIf { it >= 0 }
                    ?: throw IllegalArgumentException("version '$raw' has a non-numeric segment '$part'")
            }
            require(segments.isNotEmpty() && segments.size <= MAX_SEGMENTS) {
                "version '$raw' must have between 1 and $MAX_SEGMENTS numeric segments"
            }
            return ModelVersion(segments, raw)
        }
    }
}
