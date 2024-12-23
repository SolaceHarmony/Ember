package ai.solace.core.kognitive.utils.ports

interface AutoCloseable {
    @Throws(IOException::class)
    fun close()
}
interface Closeable : AutoCloseable {
    @Throws(IOException::class)
    override fun close()
}