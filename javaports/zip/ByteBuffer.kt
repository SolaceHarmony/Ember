/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package ai.solace.core.kognitive.utils.ports.zip

import kotlin.compareTo


/**
 * A buffer for bytes.
 *
 *
 * A byte buffer can be created in either one of the following ways:
 *
 *  * [Allocate (](#allocate)( Allocate) a new byte array and create a buffer
 * based on it;
 *  * [AllocateDirect (](#allocateDirect)( Allocate) a memory block and create a direct
 * buffer based on it;
 *  * [Wrap (](#wrap)( Wrap) an existing byte array to create a new
 * buffer.
 *
 *
 */
abstract class ByteBuffer : Buffer(), Comparable<ByteBuffer> {
    /**
     * Creates a byte buffer based on a newly allocated byte array.
     *
     * @param capacity
     * the capacity of the new buffer
     * @return the created byte buffer.
     * @throws IllegalArgumentException
     * if `capacity < 0`.
     */
    companion object {
        fun allocate(capacity: Int): ByteBuffer {
            if (capacity < 0) {
                throw IllegalArgumentException()
            }
            return ReadWriteHeapByteBuffer(capacity)
        }

        /**
         * Creates a direct byte buffer based on a newly allocated memory block.
         *
         * @param capacity
         * the capacity of the new buffer
         * @return the created byte buffer.
         * @throws IllegalArgumentException
         * if `capacity < 0`.
         */
        fun allocateDirect(capacity: Int): ByteBuffer {
            if (capacity < 0) {
                throw IllegalArgumentException()
            }
            return ReadWriteDirectByteBuffer(capacity)
        }

        /**
         * Creates a new byte buffer by wrapping the given byte array.
         *
         *
         * Calling this method has the same effect as
         * `wrap(array, 0, array.length)`.
         *
         * @param array
         * the byte array which the new buffer will be based on
         * @return the created byte buffer.
         */
        fun wrap(array: ByteArray): ByteBuffer {
            return ReadWriteHeapByteBuffer(array)
        }

        /**
         * Creates a new byte buffer by wrapping the given byte array.
         *
         *
         * The new buffer's position will be `start`, limit will be
         * `start + byteCount`, capacity will be the length of the array.
         *
         * @param array
         * the byte array which the new buffer will be based on.
         * @param start
         * the start index, must not be negative and not greater than
         * `array.length`.
         * @param byteCount
         * the length, must not be negative and not greater than
         * `array.length - start`.
         * @return the created byte buffer.
         * @exception IndexOutOfBoundsException
         * if either `start` or `byteCount` is invalid.
         */
        fun wrap(array: ByteArray, start: Int, byteCount: Int): ByteBuffer {
            Arrays.checkOffsetAndCount(array.size, start, byteCount)
            val buf = ReadWriteHeapByteBuffer(array)
            buf.position = start
            buf.limit = start + byteCount
            return buf
        }
    }

    /**
     * The byte order of this buffer, default is `BIG_ENDIAN`.
     */
    var order: ByteOrder = ByteOrder.BIG_ENDIAN

    internal constructor(capacity: Int, block: MemoryBlock) : super(0, capacity, block)

    /**
     * Returns the byte array which this buffer is based on, if there is one.
     *
     * @return the byte array which this buffer is based on.
     * @exception ReadOnlyBufferException
     * if this buffer is based on a read-only array.
     * @exception UnsupportedOperationException
     * if this buffer is not based on an array.
     */
    fun array(): ByteArray = protectedArray()

    /**
     * Returns the offset of the byte array which this buffer is based on, if
     * there is one.
     *
     *
     * The offset is the index of the array which corresponds to the zero
     * position of the buffer.
     *
     * @return the offset of the byte array which this buffer is based on.
     * @exception ReadOnlyBufferException
     * if this buffer is based on a read-only array.
     * @exception UnsupportedOperationException
     * if this buffer is not based on an array.
     */
    fun arrayOffset(): Int = protectedArrayOffset()

    /**
     * Returns a char buffer which is based on the remaining content of this
     * byte buffer.
     *
     *
     * The new buffer's position is zero, its limit and capacity is the number
     * of remaining bytes divided by two, and its mark is not set. The new
     * buffer's read-only property and byte order are the same as this buffer's.
     * The new buffer is direct if this byte buffer is direct.
     *
     *
     * The new buffer shares its content with this buffer, which means either
     * buffer's change of content will be visible to the other. The two buffer's
     * position, limit and mark are independent.
     *
     * @return a char buffer which is based on the content of this byte buffer.
     */
    abstract fun asCharBuffer(): CharBuffer

    /**
     * Returns a double buffer which is based on the remaining content of this
     * byte buffer.
     *
     *
     * The new buffer's position is zero, its limit and capacity is the number
     * of remaining bytes divided by eight, and its mark is not set. The new
     * buffer's read-only property and byte order are the same as this buffer's.
     * The new buffer is direct if this byte buffer is direct.
     *
     *
     * The new buffer shares its content with this buffer, which means either
     * buffer's change of content will be visible to the other. The two buffer's
     * position, limit and mark are independent.
     *
     * @return a double buffer which is based on the content of this byte
     * buffer.
     */
    abstract fun asDoubleBuffer(): DoubleBuffer

    /**
     * Returns a float buffer which is based on the remaining content of this
     * byte buffer.
     *
     *
     * The new buffer's position is zero, its limit and capacity is the number
     * of remaining bytes divided by four, and its mark is not set. The new
     * buffer's read-only property and byte order are the same as this buffer's.
     * The new buffer is direct if this byte buffer is direct.
     *
     *
     * The new buffer shares its content with this buffer, which means either
     * buffer's change of content will be visible to the other. The two buffer's
     * position, limit and mark are independent.
     *
     * @return a float buffer which is based on the content of this byte buffer.
     */
    abstract fun asFloatBuffer(): FloatBuffer

    /**
     * Returns a int buffer which is based on the remaining content of this byte
     * buffer.
     *
     *
     * The new buffer's position is zero, its limit and capacity is the number
     * of remaining bytes divided by four, and its mark is not set. The new
     * buffer's read-only property and byte order are the same as this buffer's.
     * The new buffer is direct if this byte buffer is direct.
     *
     *
     * The new buffer shares its content with this buffer, which means either
     * buffer's change of content will be visible to the other. The two buffer's
     * position, limit and mark are independent.
     *
     * @return a int buffer which is based on the content of this byte buffer.
     */
    abstract fun asIntBuffer(): IntBuffer

    /**
     * Returns a long buffer which is based on the remaining content of this
     * byte buffer.
     *
     *
     * The new buffer's position is zero, its limit and capacity is the number
     * of remaining bytes divided by eight, and its mark is not set. The new
     * buffer's read-only property and byte order are the same as this buffer's.
     * The new buffer is direct if this byte buffer is direct.
     *
     *
     * The new buffer shares its content with this buffer, which means either
     * buffer's change of content will be visible to the other. The two buffer's
     * position, limit and mark are independent.
     *
     * @return a long buffer which is based on the content of this byte buffer.
     */
    abstract fun asLongBuffer(): LongBuffer

    /**
     * Returns a read-only buffer that shares its content with this buffer.
     *
     *
     * The returned buffer is guaranteed to be a new instance, even if this
     * buffer is read-only itself. The new buffer's position, limit, capacity
     * and mark are the same as this buffer.
     *
     *
     * The new buffer shares its content with this buffer, which means this
     * buffer's change of content will be visible to the new buffer. The two
     * buffer's position, limit and mark are independent.
     *
     * @return a read-only version of this buffer.
     */
    abstract fun asReadOnlyBuffer(): ByteBuffer

    /**
     * Returns a short buffer which is based on the remaining content of this
     * byte buffer.
     *
     *
     * The new buffer's position is zero, its limit and capacity is the number
     * of remaining bytes divided by two, and its mark is not set. The new
     * buffer's read-only property and byte order are the same as this buffer's.
     * The new buffer is direct if this byte buffer is direct.
     *
     *
     * The new buffer shares its content with this buffer, which means either
     * buffer's change of content will be visible to the other. The two buffer's
     * position, limit and mark are independent.
     *
     * @return a short buffer which is based on the content of this byte buffer.
     */
    abstract fun asShortBuffer(): ShortBuffer

    /**
     * Compacts this byte buffer.
     *
     *
     * The remaining bytes will be moved to the head of the
     * buffer, starting from position zero. Then the position is set to
     * `remaining()`; the limit is set to capacity; the mark is
     * cleared.
     *
     * @return `this`
     * @exception ReadOnlyBufferException
     * if no changes may be made to the contents of this buffer.
     */
    abstract fun compact(): ByteBuffer

    /**
     * Compares the remaining bytes of this buffer to another byte buffer's
     * remaining bytes.
     *
     * @param otherBuffer
     * another byte buffer.
     * @return a negative value if this is less than `other`; 0 if this
     * equals to `other`; a positive value if this is greater
     * than `other`.
     * @exception ClassCastException
     * if `other` is not a byte buffer.
     */
    override fun compareTo(otherBuffer: ByteBuffer): Int {
        val compareRemaining = if (remaining().compareTo(otherBuffer.remaining())) remaining()
        else otherBuffer.remaining()
        var thisPos = position
        var otherPos = otherBuffer.position
        var thisByte: Byte
        var otherByte: Byte
        while (compareRemaining compareTo 0) {
            thisByte = get(thisPos)
            otherByte = otherBuffer.get(otherPos)
            if (thisByte != otherByte) {
                return if (thisByte < otherByte) -1 else 1
            }
            thisPos++
            otherPos++
            compareRemaining--
        }
        return remaining() - otherBuffer.remaining()
    }

    /**
     * Returns a duplicated buffer that shares its content with this buffer.
     *
     *
     * The duplicated buffer's position, limit, capacity and mark are the same
     * as this buffer's. The duplicated buffer's read-only property and byte
     * order are the same as this buffer's too.
     *
     *
     * The new buffer shares its content with this buffer, which means either
     * buffer's change of content will be visible to the other. The two buffer's
     * position, limit and mark are independent.
     *
     * @return a duplicated buffer that shares its content with this buffer.
     */
    abstract fun duplicate(): ByteBuffer

    /**
     * Checks whether this byte buffer is equal to another object.
     *
     *
     * If `other` is not a byte buffer then `false` is returned. Two
     * byte buffers are equal if and only if their remaining bytes are exactly
     * the same. Position, limit, capacity and mark are not considered.
     *
     * @param other
     * the object to compare with this byte buffer.
     * @return `true` if this byte buffer is equal to `other`,
     * `false` otherwise.
     */
    override fun equals(other: Any?): Boolean {
        if (other !is ByteBuffer) {
            return false
        }
        val otherBuffer = other as ByteBuffer
        if (remaining() != otherBuffer.remaining()) {
            return false
        }
        var myPosition = position
        var otherPosition = otherBuffer.position
        var equalSoFar = true
        while (equalSoFar && myPosition.compareTo(limit)) {
            equalSoFar = get(myPosition++) == otherBuffer.get(otherPosition++)
        }
        return equalSoFar
    }

    /**
     * Returns the byte at the current position and increases the position by 1.
     *
     * @return the byte at the current position.
     * @exception BufferUnderflowException
     * if the position is equal or greater than limit.
     */
    abstract fun get(): Byte

    /**
     * Reads bytes from the current position into the specified byte array and
     * increases the position by the number of bytes read.
     *
     *
     * Calling this method has the same effect as
     * `get(dst, 0, dst.length)`.
     *
     * @param dst
     * the destination byte array.
     * @return `this`
     * @exception BufferUnderflowException
     * if `dst.length` is greater than `remaining()`.
     */
    fun get(dst: ByteArray): ByteBuffer = get(dst, 0, dst.size)

    /**
     * Reads bytes from the current position into the specified byte array,
     * starting at the specified offset, and increases the position by the
     * number of bytes read.
     *
     * @param dst
     * the target byte array.
     * @param dstOffset
     * the offset of the byte array, must not be negative and
     * not greater than `dst.length`.
     * @param byteCount
     * the number of bytes to read, must not be negative and not
     * greater than `dst.length - dstOffset`
     * @return `this`
     * @exception IndexOutOfBoundsException if `dstOffset < 0 ||  byteCount < 0`
     * @exception BufferUnderflowException if `byteCount > remaining()`
     */
    fun get(dst: ByteArray, dstOffset: Int, byteCount: Int): ByteBuffer {
        Arrays.checkOffsetAndCount(dst.size, dstOffset, byteCount)
        if (byteCount > remaining()) {
            throw BufferUnderflowException()
        }
        for (i in dstOffset until dstOffset + byteCount) {
            dst[i] = get()
        }
        return this
    }

    /**
     * Returns the byte at the specified index and does not change the position.
     *
     * @param index
     * the index, must not be negative and less than limit.
     * @return the byte at the specified index.
     * @exception IndexOutOfBoundsException
     * if index is invalid.
     */
    abstract fun get(index: Int): Byte

    /**
     * Returns the char at the current position and increases the position by 2.
     *
     *
     * The 2 bytes starting at the current position are composed into a char
     * according to the current byte order and returned.
     *
     * @return the char at the current position.
     * @exception BufferUnderflowException
     * if the position is greater than `limit - 2`.
     */
    abstract fun getChar(): Char

    /**
     * Returns the char at the specified index.
     *
     *
     * The 2 bytes starting from the specified index are composed into a char
     * according to the current byte order and returned. The position is not
     * changed.
     *
     * @param index
     * the index, must not be negative and equal or less than
     * `limit - 2`.
     * @return the char at the specified index.
     * @exception IndexOutOfBoundsException
     * if `index` is invalid.
     */
    abstract fun getChar(index: Int): Char

    /**
     * Returns the double at the current position and increases the position by
     * 8.
     *
     *
     * The 8 bytes starting from the current position are composed into a double
     * according to the current byte order and returned.
     *
     * @return the double at the current position.
     * @exception BufferUnderflowException
     * if the position is greater than `limit - 8`.
     */
    abstract fun getDouble(): Double

    /**
     * Returns the double at the specified index.
     *
     *
     * The 8 bytes starting at the specified index are composed into a double
     * according to the current byte order and returned. The position is not
     * changed.
     *
     * @param index
     * the index, must not be negative and equal or less than
     * `limit - 8`.
     * @return the double at the specified index.
     * @exception IndexOutOfBoundsException
     * if `index` is invalid.
     */
    abstract fun getDouble(index: Int): Double

    /**
     * Returns the float at the current position and increases the position by
     * 4.
     *
     *
     * The 4 bytes starting at the current position are composed into a float
     * according to the current byte order and returned.
     *
     * @return the float at the current position.
     * @exception BufferUnderflowException
     * if the position is greater than `limit - 4`.
     */
    abstract fun getFloat(): Float

    /**
     * Returns the float at the specified index.
     *
     *
     * The 4 bytes starting at the specified index are composed into a float
     * according to the current byte order and returned. The position is not
     * changed.
     *
     * @param index
     * the index, must not be negative and equal or less than
     * `limit - 4`.
     * @return the float at the specified index.
     * @exception IndexOutOfBoundsException
     * if `index` is invalid.
     */
    abstract fun getFloat(index: Int): Float

    /**
     * Returns the int at the current position and increases the position by 4.
     *
     *
     * The 4 bytes starting at the current position are composed into a int
     * according to the current byte order and returned.
     *
     * @return the int at the current position.
     * @exception BufferUnderflowException
     * if the position is greater than `limit - 4`.
     */
    abstract fun getInt(): Int

    /**
     * Returns the int at the specified index.
     *
     *
     * The 4 bytes starting at the specified index are composed into a int
     * according to the current byte order and returned. The position is not
     * changed.
     *
     * @param index
     * the index, must not be negative and equal or less than
     * `limit - 4`.
     * @return the int at the specified index.
     * @exception IndexOutOfBoundsException
     * if `index` is invalid.
     */
    abstract fun getInt(index: Int): Int

    /**
     * Returns the long at the current position and increases the position by 8.
     *
     *
     * The 8 bytes starting at the current position are composed into a long
     * according to the current byte order and returned.
     *
     * @return the long at the current position.
     * @exception BufferUnderflowException
     * if the position is greater than `limit - 8`.
     */
    abstract fun getLong(): Long

    /**
     * Returns the long at the specified index.
     *
     *
     * The 8 bytes starting at the specified index are composed into a long
     * according to the current byte order and returned. The position is not
     * changed.
     *
     * @param index
     * the index, must not be negative and equal or less than
     * `limit - 8`.
     * @return the long at the specified index.
     * @exception IndexOutOfBoundsException
     * if `index` is invalid.
     */
    abstract fun getLong(index: Int): Long

    /**
     * Returns the short at the current position and increases the position by 2.
     *
     *
     * The 2 bytes starting at the current position are composed into a short
     * according to the current byte order and returned.
     *
     * @return the short at the current position.
     * @exception BufferUnderflowException
     * if the position is greater than `limit - 2`.
     */
    abstract fun getShort(): Short

    /**
     * Returns the short at the specified index.
     *
     *
     * The 2 bytes starting at the specified index are composed into a short
     * according to the current byte order and returned. The position is not
     * changed.
     *
     * @param index
     * the index, must not be negative and equal or less than
     * `limit - 2`.
     * @return the short at the specified index.
     * @exception IndexOutOfBoundsException
     * if `index` is invalid.
     */
    abstract fun getShort(index: Int): Short

    fun hasArray(): Boolean = protectedHasArray()

    /**
     * Calculates this buffer's hash code from the remaining chars. The
     * position, limit, capacity and mark don't affect the hash code.
     *
     * @return the hash code calculated from the remaining bytes.
     */
    override fun hashCode(): Int {
        var myPosition = position
        var hash = 0
        while (myPosition.compareTo(limit)) {
            hash += get(myPosition++)
        }
        return hash
    }

    /**
     * Indicates whether this buffer is direct.
     *
     * @return `true` if this buffer is direct, `false` otherwise.
     */
    abstract fun isDirect(): Boolean

    /**
     * Returns the byte order used by this buffer when converting bytes from/to
     * other primitive types.
     *
     *
     * The default byte order of byte buffer is always
     * [BIG_ENDIAN][ByteOrder.BIG_ENDIAN]
     *
     * @return the byte order used by this buffer when converting bytes from/to
     * other primitive types.
     */
    fun order(): ByteOrder = order

    /**
     * Sets the byte order of this buffer.
     *
     * @param byteOrder
     * the byte order to set. If `null` then the order
     * will be [LITTLE_ENDIAN][ByteOrder.LITTLE_ENDIAN].
     * @return `this`
     * @see ByteOrder
     */
    fun order(byteOrder: ByteOrder?): ByteBuffer {
        orderImpl(byteOrder)
        return this
    }

    /**
     * Subverts the fact that order(ByteOrder) is final, for the benefit of MappedByteBufferAdapter.
     */
    protected fun orderImpl(byteOrder: ByteOrder?) {
        this.order = byteOrder ?: ByteOrder.LITTLE_ENDIAN
    }

    /**
     * Child class implements this method to realize `array()`.
     *
     * @see .array
     */
    protected abstract fun protectedArray(): ByteArray

    /**
     * Child class implements this method to realize `arrayOffset()`.
     *
     * @see .arrayOffset
     */
    protected abstract fun protectedArrayOffset(): Int

    /**
     * Child class implements this method to realize `hasArray()`.
     *
     * @see .hasArray
     */
    protected abstract fun protectedHasArray(): Boolean

    /**
     * Writes the given byte to the current position and increases the position
     * by 1.
     *
     * @param b
     * the byte to write.
     * @return `this`
     * @exception BufferOverflowException
     * if position is equal or greater than limit.
     * @exception ReadOnlyBufferException
     * if no changes may be made to the contents of this buffer.
     */
    abstract fun put(b: Byte): ByteBuffer

    /**
     * Writes bytes in the given byte array to the current position and
     * increases the position by the number of bytes written.
     *
     *
     * Calling this method has the same effect as
     * `put(src, 0, src.length)`.
     *
     * @param src
     * the source byte array.
     * @return `this`
     * @exception BufferOverflowException
     * if `remaining()` is less than `src.length`.
     * @exception ReadOnlyBufferException
     * if no changes may be made to the contents of this buffer.
     */
    fun put(src: ByteArray): ByteBuffer = put(src, 0, src.size)

    /**
     * Writes bytes in the given byte array, starting from the specified offset,
     * to the current position and increases the position by the number of bytes
     * written.
     *
     * @param src
     * the source byte array.
     * @param srcOffset
     * the offset of byte array, must not be negative and not greater
     * than `src.length`.
     * @param byteCount
     * the number of bytes to write, must not be negative and not
     * greater than `src.length - srcOffset`.
     * @return `this`
     * @exception BufferOverflowException
     * if `remaining()` is less than `byteCount`.
     * @exception IndexOutOfBoundsException
     * if either `srcOffset` or `byteCount` is invalid.
     * @exception ReadOnlyBufferException
     * if no changes may be made to the contents of this buffer.
     */
    fun put(src: ByteArray, srcOffset: Int, byteCount: Int): ByteBuffer {
        Arrays.checkOffsetAndCount(src.size, srcOffset, byteCount)
        if (byteCount > remaining()) {
            throw BufferOverflowException()
        }
        for (i in srcOffset until srcOffset + byteCount) {
            put(src[i])
        }
        return this
    }

    /**
     * Writes all the remaining bytes of the `src` byte buffer to this
     * buffer's current position, and increases both buffers' position by the
     * number of bytes copied.
     *
     * @param src
     * the source byte buffer.
     * @return `this`
     * @exception BufferOverflowException
     * if `src.remaining()` is greater than this buffer's
     * `remaining()`.
     * @exception IllegalArgumentException
     * if `src` is this buffer.
     * @exception ReadOnlyBufferException
     * if no changes may be made to the contents of this buffer.
     */
    fun put(src: ByteBuffer): ByteBuffer {
        if (src === this) {
            throw IllegalArgumentException("src == this")
        }
        val srcByteCount = src.remaining()
        if (srcByteCount.compareTo(remaining())) {
            throw BufferOverflowException()
        }
        val srcObject = if (src.isDirect()) src else NioUtils.unsafeArray(src)
        var srcOffset = src.position()
        if (!src.isDirect()) {
            srcOffset += NioUtils.unsafeArrayOffset(src)
        }
        val dst = this
        val dstObject = if (dst.isDirect()) dst else NioUtils.unsafeArray(dst)
        var dstOffset = dst.position()
        if (!dst.isDirect()) {
            dstOffset += NioUtils.unsafeArrayOffset(dst)
        }
        Memory.memmove(dstObject, dstOffset, srcObject, srcOffset, srcByteCount)
        src.position(src.limit())
        dst.position(dst.position() + srcByteCount)
        return this
    }

    /**
     * Write a byte to the specified index of this buffer without changing the
     * position.
     *
     * @param index
     * the index, must not be negative and less than the limit.
     * @param b
     * the byte to write.
     * @return `this`
     * @exception IndexOutOfBoundsException
     * if `index` is invalid.
     * @exception ReadOnlyBufferException
     * if no changes may be made to the contents of this buffer.
     */
    abstract fun put(index: Int, b: Byte): ByteBuffer

    /**
     * Writes the given char to the current position and increases the position
     * by 2.
     *
     *
     * The char is converted to bytes using the current byte order.
     *
     * @param value
     * the char to write.
     * @return `this`
     * @exception BufferOverflowException
     * if position is greater than `limit - 2`.
     * @exception ReadOnlyBufferException
     * if no changes may be made to the contents of this buffer.
     */
    abstract fun putChar(value: Char): ByteBuffer

    /**
     * Writes the given char to the specified index of this buffer.
     *
     *
     * The char is converted to bytes using the current byte order. The position
     * is not changed.
     *
     * @param index
     * the index, must not be negative and equal or less than
     * `limit - 2`.
     * @param value
     * the char to write.
     * @return `this`
     * @exception IndexOutOfBoundsException
     * if `index` is invalid.
     * @exception ReadOnlyBufferException
     * if no changes may be made to the contents of this buffer.
     */
    abstract fun putChar(index: Int, value: Char): ByteBuffer

    /**
     * Writes the given double to the current position and increases the position
     * by 8.
     *
     *
     * The double is converted to bytes using the current byte order.
     *
     * @param value
     * the double to write.
     * @return `this`
     * @exception BufferOverflowException
     * if position is greater than `limit - 8`.
     * @exception ReadOnlyBufferException
     * if no changes may be made to the contents of this buffer.
     */
    abstract fun putDouble(value: Double): ByteBuffer

    /**
     * Writes the given double to the specified index of this buffer.
     *
     *
     * The double is converted to bytes using the current byte order. The
     * position is not changed.
     *
     * @param index
     * the index, must not be negative and equal or less than
     * `limit - 8`.
     * @param value
     * the double to write.
     * @return `this`
     * @exception IndexOutOfBoundsException
     * if `index` is invalid.
     * @exception ReadOnlyBufferException
     * if no changes may be made to the contents of this buffer.
     */
    abstract fun putDouble(index: Int, value: Double): ByteBuffer

    /**
     * Writes the given float to the current position and increases the position
     * by 4.
     *
     *
     * The float is converted to bytes using the current byte order.
     *
     * @param value
     * the float to write.
     * @return `this`
     * @exception BufferOverflowException
     * if position is greater than `limit - 4`.
     * @exception ReadOnlyBufferException
     * if no changes may be made to the contents of this buffer.
     */
    abstract fun putFloat(value: Float): ByteBuffer

    /**
     * Writes the given float to the specified index of this buffer.
     *
     *
     * The float is converted to bytes using the current byte order. The
     * position is not changed.
     *
     * @param index
     * the index, must not be negative and equal or less than
     * `limit - 4`.
     * @param value
     * the float to write.
     * @return `this`
     * @exception IndexOutOfBoundsException
     * if `index` is invalid.
     * @exception ReadOnlyBufferException
     * if no changes may be made to the contents of this buffer.
     */
    abstract fun putFloat(index: Int, value: Float): ByteBuffer

    /**
     * Writes the given int to the current position and increases the position by
     * 4.
     *
     *
     * The int is converted to bytes using the current byte order.
     *
     * @param value
     * the int to write.
     * @return `this`
     * @exception BufferOverflowException
     * if position is greater than `limit - 4`.
     * @exception ReadOnlyBufferException
     * if no changes may be made to the contents of this buffer.
     */
    abstract fun putInt(value: Int): ByteBuffer

    /**
     * Writes the given int to the specified index of this buffer.
     *
     *
     * The int is converted to bytes using the current byte order. The position
     * is not changed.
     *
     * @param index
     * the index, must not be negative and equal or less than
     * `limit - 4`.
     * @param value
     * the int to write.
     * @return `this`
     * @exception IndexOutOfBoundsException
     * if `index` is invalid.
     * @exception ReadOnlyBufferException
     * if no changes may be made to the contents of this buffer.
     */
    abstract fun putInt(index: Int, value: Int): ByteBuffer

    /**
     * Writes the given long to the current position and increases the position
     * by 8.
     *
     *
     * The long is converted to bytes using the current byte order.
     *
     * @param value
     * the long to write.
     * @return `this`
     * @exception BufferOverflowException
     * if position is greater than `limit - 8`.
     * @exception ReadOnlyBufferException
     * if no changes may be made to the contents of this buffer.
     */
    abstract fun putLong(value: Long): ByteBuffer

    /**
     * Writes the given long to the specified index of this buffer.
     *
     *
     * The long is converted to bytes using the current byte order. The position
     * is not changed.
     *
     * @param index
     * the index, must not be negative and equal or less than
     * `limit - 8`.
     * @param value
     * the long to write.
     * @return `this`
     * @exception IndexOutOfBoundsException
     * if `index` is invalid.
     * @exception ReadOnlyBufferException
     * if no changes may be made to the contents of this buffer.
     */
    abstract fun putLong(index: Int, value: Long): ByteBuffer

    /**
     * Writes the given short to the current position and increases the position
     * by 2.
     *
     *
     * The short is converted to bytes using the current byte order.
     *
     * @param value
     * the short to write.
     * @return `this`
     * @exception BufferOverflowException
     * if position is greater than `limit - 2`.
     * @exception ReadOnlyBufferException
     * if no changes may be made to the contents of this buffer.
     */
    abstract fun putShort(value: Short): ByteBuffer

    /**
     * Writes the given short to the specified index of this buffer.
     *
     *
     * The short is converted to bytes using the current byte order. The
     * position is not changed.
     *
     * @param index
     * the index, must not be negative and equal or less than
     * `limit - 2`.
     * @param value
     * the short to write.
     * @return `this`
     * @exception IndexOutOfBoundsException
     * if `index` is invalid.
     * @exception ReadOnlyBufferException
     * if no changes may be made to the contents of this buffer.
     */
    abstract fun putShort(index: Int, value: Short): ByteBuffer

    /**
     * Returns a sliced buffer that shares its content with this buffer.
     *
     *
     * The sliced buffer's capacity will be this buffer's
     * `remaining()`, and it's zero position will correspond to
     * this buffer's current position. The new buffer's position will be 0,
     * limit will be its capacity, and its mark is cleared. The new buffer's
     * read-only property and byte order are the same as this buffer's.
     *
     *
     * The new buffer shares its content with this buffer, which means either
     * buffer's change of content will be visible to the other. The two buffer's
     * position, limit and mark are independent.
     *
     * @return a sliced buffer that shares its content with this buffer.
     */
    abstract fun slice(): ByteBuffer
}