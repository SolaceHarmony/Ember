/* Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.solace.core.kognitive.utils.ports.zip


/**
 * HeapByteBuffer, ReadWriteHeapByteBuffer and ReadOnlyHeapByteBuffer compose
 * the implementation of array based byte buffers.
 * <p>
 * ReadWriteHeapByteBuffer extends HeapByteBuffer with all the write methods.
 * </p>
 * <p>
 * This class is marked final for runtime performance.
 * </p>
 *
 */
class ReadWriteHeapByteBuffer : HeapByteBuffer {
    companion object {
        fun copy(other: HeapByteBuffer, markOfOther: Int): ReadWriteHeapByteBuffer {
            val buf = ReadWriteHeapByteBuffer(other.backingArray, other.capacity(), other.offset)
            buf.limit = other.limit
            buf.position = other.position()
            buf.mark = markOfOther
            return buf
        }
    }

    constructor(backingArray: ByteArray) : super(backingArray)
    constructor(capacity: Int) : super(capacity)
    constructor(backingArray: ByteArray, capacity: Int, arrayOffset: Int) : super(backingArray, capacity, arrayOffset)

    override fun asReadOnlyBuffer(): ByteBuffer {
        return ReadOnlyHeapByteBuffer.copy(this, mark)
    }

    override fun compact(): ByteBuffer {
        System.arraycopy(backingArray, position + offset, backingArray, offset, remaining())
        position = limit - position
        limit = capacity
        mark = UNSET_MARK
        return this
    }

    override fun duplicate(): ByteBuffer {
        return copy(this, mark)
    }

    override fun isReadOnly(): Boolean {
        return false
    }

    override fun protectedArray(): ByteArray {
        return backingArray
    }

    override fun protectedArrayOffset(): Int {
        return offset
    }

    override fun protectedHasArray(): Boolean {
        return true
    }

    override fun put(b: Byte): ByteBuffer {
        if (position == limit) {
            throw Exception()
        }
        backingArray[offset + position++] = b
        return this
    }

    override fun put(index: Int, b: Byte): ByteBuffer {
        checkIndex(index)
        backingArray[offset + index] = b
        return this
    }

    override fun put(src: ByteArray, srcOffset: Int, byteCount: Int): ByteBuffer {
        checkPutBounds(1, src.size, srcOffset, byteCount)
        System.arraycopy(src, srcOffset, backingArray, offset + position, byteCount)
        position += byteCount
        return this
    }

    fun put(src: CharArray, srcOffset: Int, charCount: Int) {
        val byteCount = checkPutBounds(SizeOf.CHAR, src.size, srcOffset, charCount)
        Memory.unsafeBulkPut(backingArray, offset + position, byteCount, src, srcOffset, SizeOf.CHAR, order.needsSwap)
        position += byteCount
    }

    fun put(src: DoubleArray, srcOffset: Int, doubleCount: Int) {
        val byteCount = checkPutBounds(SizeOf.DOUBLE, src.size, srcOffset, doubleCount)
        Memory.unsafeBulkPut(backingArray, offset + position, byteCount, src, srcOffset, SizeOf.DOUBLE, order.needsSwap)
        position += byteCount
    }

    fun put(src: FloatArray, srcOffset: Int, floatCount: Int) {
        val byteCount = checkPutBounds(SizeOf.FLOAT, src.size, srcOffset, floatCount)
        Memory.unsafeBulkPut(backingArray, offset + position, byteCount, src, srcOffset, SizeOf.FLOAT, order.needsSwap)
        position += byteCount
    }

    fun put(src: IntArray, srcOffset: Int, intCount: Int) {
        val byteCount = checkPutBounds(SizeOf.INT, src.size, srcOffset, intCount)
        Memory.unsafeBulkPut(backingArray, offset + position, byteCount, src, srcOffset, SizeOf.INT, order.needsSwap)
        position += byteCount
    }

    fun put(src: LongArray, srcOffset: Int, longCount: Int) {
        val byteCount = checkPutBounds(SizeOf.LONG, src.size, srcOffset, longCount)
        Memory.unsafeBulkPut(backingArray, offset + position, byteCount, src, srcOffset, SizeOf.LONG, order.needsSwap)
        position += byteCount
    }

    fun put(src: ShortArray, srcOffset: Int, shortCount: Int) {
        val byteCount = checkPutBounds(SizeOf.SHORT, src.size, srcOffset, shortCount)
        Memory.unsafeBulkPut(backingArray, offset + position, byteCount, src, srcOffset, SizeOf.SHORT, order.needsSwap)
        position += byteCount
    }

    override fun putChar(index: Int, value: Char): ByteBuffer {
        checkIndex(index, SizeOf.CHAR)
        Memory.pokeShort(backingArray, offset + index, value.code.toShort(), order)
        return this
    }

    override fun putChar(value: Char): ByteBuffer {
        val newPosition = position + SizeOf.CHAR
        if (newPosition > limit) {
            throw Exception()
        }
        Memory.pokeShort(backingArray, offset + position, value.code.toShort(), order)
        position = newPosition
        return this
    }

    override fun putDouble(value: Double): ByteBuffer {
        return putLong(value.toRawBits())
    }

    override fun putDouble(index: Int, value: Double): ByteBuffer {
        return putLong(index, value.toRawBits())
    }

    override fun putFloat(value: Float): ByteBuffer {
        return putInt(value.toRawBits())
    }

    override fun putFloat(index: Int, value: Float): ByteBuffer {
        return putInt(index, value.toRawBits())
    }

    override fun putInt(value: Int): ByteBuffer {
        val newPosition = position + SizeOf.INT
        if (newPosition > limit) {
            throw Exception()
        }
        Memory.pokeInt(backingArray, offset + position, value, order)
        position = newPosition
        return this
    }

    override fun putInt(index: Int, value: Int): ByteBuffer {
        checkIndex(index, SizeOf.INT)
        Memory.pokeInt(backingArray, offset + index, value, order)
        return this
    }

    override fun putLong(index: Int, value: Long): ByteBuffer {
        checkIndex(index, SizeOf.LONG)
        Memory.pokeLong(backingArray, offset + index, value, order)
        return this
    }

    override fun putLong(value: Long): ByteBuffer {
        val newPosition = position + SizeOf.LONG
        if (newPosition > limit) {
            throw Exception()
        }
        Memory.pokeLong(backingArray, offset + position, value, order)
        position = newPosition
        return this
    }

    override fun putShort(index: Int, value: Short): ByteBuffer {
        checkIndex(index, SizeOf.SHORT)
        Memory.pokeShort(backingArray, offset + index, value, order)
        return this
    }

    override fun putShort(value: Short): ByteBuffer {
        val newPosition = position + SizeOf.SHORT
        if (newPosition > limit) {
            throw Exception()
        }
        Memory.pokeShort(backingArray, offset + position, value, order)
        position = newPosition
        return this
    }

    override fun slice(): ByteBuffer {
        return ReadWriteHeapByteBuffer(backingArray, remaining(), offset + position)
    }
}