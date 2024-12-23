/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package ai.solace.core.kognitive.utils.ports.zip

import java.nio.ByteOrder

import jdk.internal.misc.Unsafe
import jdk.internal.util.Preconditions
import jdk.internal.vm.annotation.IntrinsicCandidate
import sun.nio.ch.DirectBuffer

import java.util.zip.ZipUtils.NIO_ACCESS

/**
 * A class that can be used to compute the CRC-32C of a data stream.
 *
 *
 * CRC-32C is defined in [RFC 3720](http://www.ietf.org/rfc/rfc3720.txt): Internet Small Computer Systems Interface (iSCSI).
 *
 *
 * Passing a `null` argument to a method in this class will cause a
 * [NullPointerException] to be thrown.
 *
 * @spec https://www.rfc-editor.org/info/rfc3720
 * RFC 3720: Internet Small Computer Systems Interface (iSCSI)
 * @since 9
 */
class CRC32C : Checksum {

    /*
     * This CRC-32C implementation uses the 'slicing-by-8' algorithm described
     * in the paper "A Systematic Approach to Building High Performance
     * Software-Based CRC Generators" by Michael E. Kounavis and Frank L. Berry,
     * Intel Research and Development
     */

    /**
     * CRC-32C Polynomial
     */
    private val CRC32C_POLY = 0x1EDC6F41
    private val REVERSED_CRC32C_POLY = Integer.reverse(CRC32C_POLY)

    private val UNSAFE: Unsafe = Unsafe.getUnsafe()

    // Lookup tables
    // Lookup table for single byte calculations
    private val byteTable: IntArray

    // Lookup tables for bulk operations in 'slicing-by-8' algorithm
    private val byteTables = Array(8) { IntArray(256) }
    private val byteTable0 = byteTables[0]
    private val byteTable1 = byteTables[1]
    private val byteTable2 = byteTables[2]
    private val byteTable3 = byteTables[3]
    private val byteTable4 = byteTables[4]
    private val byteTable5 = byteTables[5]
    private val byteTable6 = byteTables[6]
    private val byteTable7 = byteTables[7]

    init {
        // Generate lookup tables
        // High-order polynomial term stored in LSB of r.
        for (index in byteTables[0].indices) {
            var r = index
            repeat(Byte.SIZE_BITS) {
                r = if (r and 1 != 0) {
                    (r ushr 1) xor REVERSED_CRC32C_POLY
                } else {
                    r ushr 1
                }
            }
            byteTables[0][index] = r
        }

        for (index in byteTables[0].indices) {
            var r = byteTables[0][index]

            for (k in 1 until byteTables.size) {
                r = byteTables[0][r and 0xFF] xor (r ushr 8)
                byteTables[k][index] = r
            }
        }

        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            byteTable = byteTables[0]
        } else { // ByteOrder.BIG_ENDIAN
            byteTable = IntArray(byteTable0.size)
            System.arraycopy(byteTable0, 0, byteTable, 0, byteTable0.size)
            for (table in byteTables) {
                for (index in table.indices) {
                    table[index] = Integer.reverseBytes(table[index])
                }
            }
        }
    }

    /**
     * Calculated CRC-32C value
     */
    private var crc = 0xFFFFFFFF.toInt()

    /**
     * Creates a new CRC32C object.
     */
    constructor()

    /**
     * Updates the CRC-32C checksum with the specified byte (the low eight bits
     * of the argument b).
     */
    override fun update(b: Int) {
        crc = (crc ushr 8) xor byteTable[(crc xor (b and 0xFF)) and 0xFF]
    }

    /**
     * Updates the CRC-32C checksum with the specified array of bytes.
     *
     * @throws ArrayIndexOutOfBoundsException
     * if `off` is negative, or `len` is negative, or
     * `off+len` is negative or greater than the length of
     * the array `b`.
     */
    override fun update(b: ByteArray?, off: Int, len: Int) {
        b ?: throw NullPointerException()
        Preconditions.checkFromIndexSize(off, len, b.size, Preconditions.AIOOBE_FORMATTER)
        crc = updateBytes(crc, b, off, off + len)
    }

    /**
     * Updates the CRC-32C checksum with the bytes from the specified buffer.
     *
     * The checksum is updated with the remaining bytes in the buffer, starting
     * at the buffer's position. Upon return, the buffer's position will be
     * updated to its limit; its limit will not have been changed.
     */
    override fun update(buffer: ByteBuffer) {
        val pos = buffer.position()
        val limit = buffer.limit()
        assert(pos <= limit)
        val rem = limit - pos
        if (rem <= 0) {
            return
        }

        if (buffer.isDirect) {
            NIO_ACCESS.acquireSession(buffer)
            try {
                crc = updateDirectByteBuffer(crc, (buffer as DirectBuffer).address(), pos, limit)
            } finally {
                NIO_ACCESS.releaseSession(buffer)
            }
        } else if (buffer.hasArray()) {
            crc = updateBytes(crc, buffer.array(), pos + buffer.arrayOffset(), limit + buffer.arrayOffset())
        } else {
            val b = ByteArray(minOf(buffer.remaining(), 4096))
            while (buffer.hasRemaining()) {
                val length = minOf(buffer.remaining(), b.size)
                buffer.get(b, 0, length)
                update(b, 0, length)
            }
        }
        buffer.position(limit)
    }

    /**
     * Resets CRC-32C to initial value.
     */
    override fun reset() {
        crc = 0xFFFFFFFF.toInt()
    }

    /**
     * Returns CRC-32C value.
     */
    override fun getValue(): Long {
        return crc.toLong().inv() and 0xFFFFFFFFL
    }

    /**
     * Updates the CRC-32C checksum with the specified array of bytes.
     */
    @IntrinsicCandidate
    private fun updateBytes(crc: Int, b: ByteArray, off: Int, end: Int): Int {

        // Do only byte reads for arrays so short they can't be aligned
        // or if bytes are stored with a larger width than one byte.,%
        var crcCopy = crc
        var offCopy = off
        if (end - offCopy >= 8 && Unsafe.ARRAY_BYTE_INDEX_SCALE == 1) {

            // align on 8 bytes
            val alignLength = (8 - ((Unsafe.ARRAY_BYTE_BASE_OFFSET + offCopy) and 0x7)).toInt() and 0x7
            for (alignEnd in offCopy until offCopy + alignLength) {
                crcCopy = (crcCopy ushr 8) xor byteTable[(crcCopy xor b[alignEnd].toInt()) and 0xFF]
            }
            offCopy += alignLength

            if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
                crcCopy = Integer.reverseBytes(crcCopy)
            }

            // slicing-by-8
            while (offCopy < end - Long.SIZE_BYTES + 1) {
                val firstHalf: Int
                val secondHalf: Int
                if (Unsafe.ADDRESS_SIZE == 4) {
                    // On 32 bit platforms read two ints instead of a single 64bit long
                    firstHalf = UNSAFE.getInt(b, Unsafe.ARRAY_BYTE_BASE_OFFSET + offCopy.toLong())
                    secondHalf = UNSAFE.getInt(b, Unsafe.ARRAY_BYTE_BASE_OFFSET + offCopy.toLong() + Integer.BYTES)
                } else {
                    val value = UNSAFE.getLong(b, Unsafe.ARRAY_BYTE_BASE_OFFSET + offCopy.toLong())
                    if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                        firstHalf = value.toInt()
                        secondHalf = (value ushr 32).toInt()
                    } else { // ByteOrder.BIG_ENDIAN
                        firstHalf = (value ushr 32).toInt()
                        secondHalf = value.toInt()
                    }
                }
                crcCopy = crcCopy xor firstHalf
                crcCopy = if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                    byteTable7[crcCopy and 0xFF] xor
                            byteTable6[(crcCopy ushr 8) and 0xFF] xor
                            byteTable5[(crcCopy ushr 16) and 0xFF] xor
                            byteTable4[crcCopy ushr 24] xor
                            byteTable3[secondHalf and 0xFF] xor
                            byteTable2[(secondHalf ushr 8) and 0xFF] xor
                            byteTable1[(secondHalf ushr 16) and 0xFF] xor
                            byteTable0[secondHalf ushr 24]
                } else { // ByteOrder.BIG_ENDIAN
                    byteTable0[secondHalf and 0xFF] xor
                            byteTable1[(secondHalf ushr 8) and 0xFF] xor
                            byteTable2[(secondHalf ushr 16) and 0xFF] xor
                            byteTable3[secondHalf ushr 24] xor
                            byteTable4[crcCopy and 0xFF] xor
                            byteTable5[(crcCopy ushr 8) and 0xFF] xor
                            byteTable6[(crcCopy ushr 16) and 0xFF] xor
                            byteTable7[crcCopy ushr 24]
                }
                offCopy += Long.SIZE_BYTES
            }

            if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
                crcCopy = Integer.reverseBytes(crcCopy)
            }
        }

        // Tail
        for (i in offCopy until end) {
            crcCopy = (crcCopy ushr 8) xor byteTable[(crcCopy xor b[i].toInt()) and 0xFF]
        }

        return crcCopy
    }

    /**
     * Updates the CRC-32C checksum reading from the specified address.
     */
    @IntrinsicCandidate
    private fun updateDirectByteBuffer(crc: Int, address: Long, off: Int, end: Int): Int {

        // Do only byte reads for arrays so short they can't be aligned
        var crcCopy = crc
        var offCopy = off
        if (end - offCopy >= 8) {

            // align on 8 bytes
            val alignLength = (8 - ((address + offCopy) and 0x7)).toInt() and 0x7
            val alignEnd = offCopy + alignLength
            for (i in offCopy until alignEnd) {
                crcCopy = (crcCopy ushr 8) xor byteTable[(crcCopy xor UNSAFE.getByte(address + i).toInt()) and 0xFF]
            }
            offCopy = alignEnd

            if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
                crcCopy = Integer.reverseBytes(crcCopy)
            }

            // slicing-by-8
            while (offCopy <= end - Long.SIZE_BYTES) {
                // Always reading two ints as reading a long followed by
                // shifting and casting was slower.
                val firstHalf = UNSAFE.getInt(address + offCopy)
                val secondHalf = UNSAFE.getInt(address + offCopy + Integer.BYTES)
                crcCopy = crcCopy xor firstHalf
                crcCopy = if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                    byteTable7[crcCopy and 0xFF] xor
                            byteTable6[(crcCopy ushr 8) and 0xFF] xor
                            byteTable5[(crcCopy ushr 16) and 0xFF] xor
                            byteTable4[crcCopy ushr 24] xor
                            byteTable3[secondHalf and 0xFF] xor
                            byteTable2[(secondHalf ushr 8) and 0xFF] xor
                            byteTable1[(secondHalf ushr 16) and 0xFF] xor
                            byteTable0[secondHalf ushr 24]
                } else { // ByteOrder.BIG_ENDIAN
                    byteTable0[secondHalf and 0xFF] xor
                            byteTable1[(secondHalf ushr 8) and 0xFF] xor
                            byteTable2[(secondHalf ushr 16) and 0xFF] xor
                            byteTable3[secondHalf ushr 24] xor
                            byteTable4[crcCopy and 0xFF] xor
                            byteTable5[(crcCopy ushr 8) and 0xFF] xor
                            byteTable6[(crcCopy ushr 16) and 0xFF] xor
                            byteTable7[crcCopy ushr 24]
                }
                offCopy += Long.SIZE_BYTES
            }

            if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
                crcCopy = Integer.reverseBytes(crcCopy)
            }
        }

        // Tail
        for (i in offCopy until end) {
            crcCopy = (crcCopy ushr 8) xor byteTable[(crcCopy xor UNSAFE.getByte(address + i).toInt()) and 0xFF]
        }

        return crcCopy
    }
}