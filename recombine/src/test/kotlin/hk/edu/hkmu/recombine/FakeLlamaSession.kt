package hk.edu.hkmu.recombine

/**
 * Tier A fake [LlamaSession]: records each [generate] call and streams scripted pieces, so the
 * engine's lazy-load, opt-out, streaming and failure wiring can be asserted with no native library
 * or GGUF model.
 */
internal class FakeLlamaSession(
    private val pieces: List<String> = listOf("Hel", "lo", " ", "world"),
    private val errorCode: Int = 0
) : LlamaSession {

    data class Call(
        val prompt: String,
        val maxTokens: Int,
        val seed: Int,
        val temperature: Float
    )

    val calls: MutableList<Call> = mutableListOf()

    var closed: Boolean = false
        private set

    override fun generate(
        prompt: String,
        maxTokens: Int,
        seed: Int,
        temperature: Float,
        onToken: (String) -> Unit
    ): Int {
        calls += Call(prompt, maxTokens, seed, temperature)
        if (errorCode < 0) return errorCode
        pieces.forEach(onToken)
        return pieces.size
    }

    override fun close() {
        closed = true
    }
}
