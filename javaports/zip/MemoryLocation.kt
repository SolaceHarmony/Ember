package ai.solace.core.kognitive.utils.ports.zip

/**
 * A memory location. Tracked either by a memory address (with off-heap allocation),
 * or by an offset from a specific object (on-heap allocation).
 */
internal class MemoryLocation(
    var obj: Any? = null,
    var offset: Long = 0
) {

    fun setObjAndOffset(newObj: Any?, newOffset: Long) {
        this.obj = newObj
        this.offset = newOffset
    }

    fun getBaseObject(): Any? {
        return obj
    }

    fun getBaseOffset(): Long {
        return offset
    }
}