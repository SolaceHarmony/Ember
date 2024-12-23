package ai.solace.core.kognitive.utils.ports.zip


open class HeapByteBuffer(internal val backingArray: ByteArray, capacity: Int, private val offset: Int = 0) {

    var position: Int = 0
    var limit: Int = capacity

    init {
        if (offset + capacity > backingArray.size)
            throw Exception("backingArray.length=${backingArray.size}, capacity=$capacity, offset=$offset")
    }

    fun arrayCopy(src: ByteArray, srcPos: Int, dst: ByteArray, dstPos: Int, length: Int) {
        src.copyInto(dst, dstPos, srcPos, srcPos + length)
    }

    fun checkGetBounds(elementSize: Int, dstSize: Int, dstOffset: Int, count: Int): Int {
        val byteCount = elementSize * count
        if (dstOffset < 0 || count < 0 || dstOffset + byteCount > dstSize) {
            throw Exception("Invalid bounds: dstOffset = $dstOffset, count = $count, dstSize = $dstSize")
        }
        return byteCount
    }

    fun get(dst: ByteArray, dstOffset: Int, byteCount: Int): HeapByteBuffer {
        checkGetBounds(1, dst.size, dstOffset, byteCount)
        arrayCopy(backingArray, offset + position, dst, dstOffset, byteCount)
        position += byteCount
        return this
    }

    fun get(): Byte {
        if (position >= limit) {
            throw BufferUnderflowException()
        }
        return backingArray[offset + position++]
    }

    fun get(index: Int): Byte {
        checkIndex(index)
        return backingArray[offset + index]
    }

    private fun checkIndex(index: Int, elementSize: Int = 1) {
        if (index < 0 || index + elementSize > limit) {
            throw Exception("Index: $index, Limit: $limit, Element Size: $elementSize")
        }
    }

    fun isDirect(): Boolean {
        return false
    }
}
open class RuntimeException : Exception {
    constructor(): super()

    constructor(detailMessage: String) : super(detailMessage)

    constructor(detailMessage: String, throwable: Throwable) : super(detailMessage, throwable)

    constructor(throwable: Throwable) : super(throwable)

    companion object {
        private const val serialVersionUID: Long = -7034897190745766939L
    }
}
class BufferUnderflowException : RuntimeException() {
    companion object {
        private const val serialVersionUID: Long = -1713313658691622206L
    }
}
