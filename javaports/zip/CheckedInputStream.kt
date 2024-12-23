/*
 * Copyright (c) 1996, 2019, Oracle and/or its affiliates. All rights reserved.
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

import kotlin.Throws

/**
 * An input stream that also maintains a checksum of the data being read.
 * The checksum can then be used to verify the integrity of the input data.
 *
 * @see         Checksum
 */
class CheckedInputStream(`in`: InputStream, private var cksum: Checksum) : FilterInputStream(`in`) {

    /**
     * Creates an input stream using the specified Checksum.
     * @param in the input stream
     * @param cksum the Checksum
     */
    init {
        this.cksum = cksum
    }

    /**
     * Reads a byte. Will block if no input is available.
     * @return the byte read, or -1 if the end of the stream is reached.
     * @throws    IOException if an I/O error has occurred
     */
    @Throws(IOException::class)
    override fun read(): Int = `in`.read().also { b ->
        if (b != -1) {
            cksum.update(b)
        }
    }

    /**
     * Reads into an array of bytes. If {@code len} is not zero, the method
     * blocks until some input is available; otherwise, no
     * bytes are read and {@code 0} is returned.
     * @param buf the buffer into which the data is read
     * @param off the start offset in the destination array {@code b}
     * @param len the maximum number of bytes read
     * @return    the actual number of bytes read, or -1 if the end
     *            of the stream is reached.
     * @throws     NullPointerException If {@code buf} is {@code null}.
     * @throws     IndexOutOfBoundsException If {@code off} is negative,
     * {@code len} is negative, or {@code len} is greater than
     * {@code buf.length - off}
     * @throws    IOException if an I/O error has occurred
     */
    @Throws(IOException::class)
    override fun read(buf: ByteArray, off: Int, len: Int): Int {
        return `in`.read(buf, off, len).also { bytesRead ->
            if (bytesRead != -1) {
                cksum.update(buf, off, bytesRead)
            }
        }
    }

    /**
     * Skips specified number of bytes of input.
     * @param n the number of bytes to skip
     * @return the actual number of bytes skipped
     * @throws    IOException if an I/O error has occurred
     */
    @Throws(IOException::class)
    override fun skip(n: Long): Long {
        val buf = ByteArray(512)
        var total = 0L

        while (total < n) {
            val lengthToRead = minOf(n - total, buf.size.toLong()).toInt()
            val bytesRead = read(buf, 0, lengthToRead).takeIf { it != -1 } ?: return total
            total += bytesRead
        }

        return total
    }

    /**
     * Returns the Checksum for this input stream.
     * @return the Checksum value
     */
    fun getChecksum(): Checksum {
        return cksum
    }
}