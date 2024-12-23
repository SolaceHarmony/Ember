/*
 * Copyright (c) 2006, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.InputStream
import java.io.IOException

/**
 * Implements an input stream filter for compressing data in the "deflate"
 * compression format.
 *
 * @since       1.6
 * @author      David R Tribble (david@tribble.com)
 *
 * @see DeflaterOutputStream
 * @see InflaterOutputStream
 * @see InflaterInputStream
 */

open class DeflaterInputStream(
    in: InputStream?,
    protected val def: Deflater,
    private val bufLen: Int = 512
) : FilterInputStream(in) {

    /** Input buffer for reading compressed data. */
    protected val buf: ByteArray = ByteArray(bufLen)

    /** Temporary read buffer. */
    private var rbuf = ByteArray(1)

    /** Default compressor is used. */
    private var usesDefaultDeflater = false

    /** End of the underlying input stream has been reached. */
    private var reachEOF = false

    init {
        // Sanity checks
        require(bufLen > 0) { "Buffer size < 1" }
    }

    /**
     * Check to make sure that this stream has not been closed.
     */
    @Throws(IOException::class)
    private fun ensureOpen() {
        if ( in == null) {
            throw IOException("Stream closed")
        }
    }

    /**
     * Creates a new input stream with a default compressor and buffer
     * size.
     *
     * @param in input stream to read the uncompressed data to
     * @throws NullPointerException if {@code in} is null
     */
    @JvmOverloads
    constructor(in: InputStream?) : this( in, Deflater()) {
        usesDefaultDeflater = true
    }

    /**
     * Closes this input stream and its underlying input stream, discarding
     * any pending uncompressed data.
     *
     * @throws IOException if an I/O error occurs
     */
    @Throws(IOException::class)
    override fun close() {
        if ( in != null) {
            try {
                // Clean up
                if (usesDefaultDeflater) {
                    def.end()
                }
                super.close()
            } finally {
                ( in as InputStream ?) = null
            }
        }
    }

    /**
     * Reads a single byte of compressed data from the input stream.
     * This method will block until some input can be read and compressed.
     *
     * @return a single byte of compressed data, or -1 if the end of the
     * uncompressed input stream is reached
     * @throws IOException if an I/O error occurs or if this stream is
     * already closed
     */
    @Throws(IOException::class)
    override fun read(): Int {
        // Read a single byte of compressed data
        val len = read(rbuf, 0, 1)
        return if (len <= 0) -1 else (rbuf[0].toInt() and 0xFF)
    }

    /**
     * Reads compressed data into a byte array.
     * This method will block until some input can be read and compressed.
     *
     * @param b buffer into which the data is read
     * @param off starting offset of the data within {@code b}
     * @param len maximum number of compressed bytes to read into {@code b}
     * @return the actual number of bytes read, or -1 if the end of the
     * uncompressed input stream is reached
     * @throws NullPointerException if {@code b} is null
     * @throws IndexOutOfBoundsException  if {@code len > b.length - off}
     * @throws IOException if an I/O error occurs or if this input stream is
     * already closed
     */
    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        // Sanity checks
        ensureOpen()
        Objects.checkFromIndexSize(off, len, b.size)
        if (len == 0) return 0

        // Read and compress (deflate) input data bytes
        var cnt = 0
        var n: Int
        var localLen = len
        var localOff = off

        while (localLen > 0 && !def.finished()) {
            // Read data from the input stream
            if (def.needsInput()) {
                n = in.read(buf, 0, buf.size)
                if (n < 0) {
                    // End of the input stream reached
                    def.finish()
                } else if (n > 0) {
                    def.setInput(buf, 0, n)
                }
            }

            // Compress the input data, filling the read buffer
            n = def.deflate(b, localOff, localLen)
            cnt += n
            localOff += n
            localLen -= n
        }

        if (cnt == 0 && def.finished()) {
            reachEOF = true
            cnt = -1
        }

        return cnt
    }

    /**
     * Skips over and discards data from the input stream.
     * This method may block until the specified number of bytes are skipped
     * or end of stream is reached.
     *
     * @implNote
     * This method skips at most {@code Integer.MAX_VALUE} bytes.
     *
     * @param n number of bytes to be skipped. If {@code n} is zero then no bytes are skipped.
     * @return the actual number of bytes skipped, which might be zero
     * @throws IOException if an I/O error occurs or if this stream is
     *                     already closed
     * @throws IllegalArgumentException if {@code n < 0}
     */
    @Throws(IOException::class)
    override fun skip(n: Long): Long {
        require(n >= 0) { "negative skip length" }
        ensureOpen()

        // Skip bytes by repeatedly decompressing small blocks
        if (rbuf.size < 512) rbuf = ByteArray(512)

        var total = n.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        var cnt: Long = 0

        while (total > 0) {
            // Read a small block of uncompressed bytes
            val len = read(rbuf, 0, if (total <= rbuf.size) total else rbuf.size)

            if (len < 0) break
            cnt += len
            total -= len
        }
        return cnt
    }

    /**
     * Returns 0 after EOF has been reached, otherwise always return 1.
     * <p>
     * Programs should not count on this method to return the actual number
     * of bytes that could be read without blocking
     * @return zero after the end of the underlying input stream has been
     * reached, otherwise always returns 1
     * @throws IOException if an I/O error occurs or if this stream is
     * already closed
     */
    @Throws(IOException::class)
    override fun available(): Int {
        ensureOpen()
        return if (reachEOF) 0 else 1
    }

    /**
     * Always returns {@code false} because this input stream does not support
     * the {@link #mark mark()} and {@link #reset reset()} methods.
     *
     * @return false, always
     */
    override fun markSupported(): Boolean {
        return false
    }

    /**
     * <i>This operation is not supported</i>.
     *
     * @param limit maximum bytes that can be read before invalidating the position marker
     */
    override fun mark(limit: Int) {
        // Operation not supported
    }

    /**
     * <i>This operation is not supported</i>.
     *
     * @throws IOException always thrown
     */
    @Throws(IOException::class)
    override fun reset() {
        throw IOException("mark/reset not supported")
    }
}