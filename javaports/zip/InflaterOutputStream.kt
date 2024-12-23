/*
 * Copyright (c) 2006, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FilterOutputStream
import java.io.IOException
import java.util.zip.DataFormatException

/**
 * Implements an output stream filter for uncompressing data stored in the
 * "deflate" compression format.
 *
 * @since       1.6
 * @author      David R Tribble (david@tribble.com)
 *
 * @see InflaterInputStream
 * @see DeflaterInputStream
 * @see DeflaterOutputStream
 */

open class InflaterOutputStream @JvmOverloads constructor(
    out: OutputStream,
    protected val inf: Inflater = Inflater(),
    bufLen: Int = 512
) : FilterOutputStream(out) {

    /** Output buffer for writing uncompressed data. */
    protected val buf: ByteArray

    /** Temporary write buffer. */
    private val wbuf = ByteArray(1)

    /** Default decompressor is used. */
    private var usesDefaultInflater = false

    /** true iff [close] has been called. */
    private var closed = false

    init {
        // Sanity checks
        if (out == null)
            throw NullPointerException("Null output")
        if (infl == null)
            throw NullPointerException("Null inflater")
        if (bufLen <= 0)
            throw IllegalArgumentException("Buffer size < 1")

        // Initialize
        buf = ByteArray(bufLen)
    }

    /**
     * Checks to make sure that this stream has not been closed.
     */
    @Throws(IOException::class)
    private fun ensureOpen() {
        if (closed) {
            throw IOException("Stream closed")
        }
    }

    /**
     * Creates a new output stream with a default decompressor and buffer
     * size.
     *
     * @param out output stream to write the uncompressed data to
     * @throws NullPointerException if [out] is null
     */
    constructor(out: OutputStream) : this(out, out?.let { Inflater() } ?: throw NullPointerException()) {
        usesDefaultInflater = true
    }

    /**
     * Writes any remaining uncompressed data to the output stream and closes
     * the underlying output stream.
     *
     * @throws IOException if an I/O error occurs
     */
    @Throws(IOException::class)
    override fun close() {
        if (!closed) {
            // Complete the uncompressed output
            try {
                finish()
            } finally {
                out.close()
                closed = true
            }
        }
    }

    /**
     * Flushes this output stream, forcing any pending buffered output bytes to be
     * written.
     *
     * @throws IOException if an I/O error occurs or this stream is already
     * closed
     */
    @Throws(IOException::class)
    override fun flush() {
        ensureOpen()

        // Finish decompressing and writing pending output data
        if (!inf.finished()) {
            try {
                while (!inf.finished() && !inf.needsInput()) {
                    val n: Int

                    // Decompress pending output data
                    n = inf.inflate(buf, 0, buf.size)
                    if (n < 1) {
                        break
                    }

                    // Write the uncompressed output data block
                    out.write(buf, 0, n)
                }
                super.flush()
            } catch (ex: DataFormatException) {
                // Improperly formatted compressed (ZIP) data
                var msg = ex.message
                if (msg == null) {
                    msg = "Invalid ZLIB data format"
                }
                throw ZipException(msg)
            }
        }
    }

    /**
     * Finishes writing uncompressed data to the output stream without closing
     * the underlying stream.  Use this method when applying multiple filters in
     * succession to the same output stream.
     *
     * @throws IOException if an I/O error occurs or this stream is already
     * closed
     */
    @Throws(IOException::class)
    fun finish() {
        ensureOpen()

        // Finish decompressing and writing pending output data
        flush()
        if (usesDefaultInflater) {
            inf.end()
        }
    }

    /**
     * Writes a byte to the uncompressed output stream.
     *
     * @param b a single byte of compressed data to decompress and write to
     * the output stream
     * @throws IOException if an I/O error occurs or this stream is already
     * closed
     * @throws ZipException if a compression (ZIP) format error occurs
     */
    @Throws(IOException::class)
    override fun write(b: Int) {
        // Write a single byte of data
        wbuf[0] = b.toByte()
        write(wbuf, 0, 1)
    }

    /**
     * Writes an array of bytes to the uncompressed output stream.
     *
     * @param b buffer containing compressed data to decompress and write to
     * the output stream
     * @param off starting offset of the compressed data within [b]
     * @param len number of bytes to decompress from [b]
     * @throws IndexOutOfBoundsException if [off] < 0, or if
     * [len] < 0, or if [len] > b.length - off
     * @throws IOException if an I/O error occurs or this stream is already
     * closed
     * @throws NullPointerException if [b] is null
     * @throws ZipException if a compression (ZIP) format error occurs
     */
    @Throws(IOException::class)
    override fun write(b: ByteArray?, off: Int, len: Int) {
        // Sanity checks
        ensureOpen()
        if (b == null) {
            throw NullPointerException("Null buffer for read")
        }
        Objects.checkFromIndexSize(off, len, b.size)
        if (len == 0) {
            return
        }

        // Write uncompressed data to the output stream
        try {
            while (true) {
                var n: Int

                // Fill the decompressor buffer with output data
                if (inf.needsInput()) {
                    inf.setInput(b, off, len)
                    // Only use input buffer once.
                    break
                }

                // Decompress and write blocks of output data
                do {
                    n = inf.inflate(buf, 0, buf.size)
                    if (n > 0) {
                        out.write(buf, 0, n)
                    }
                } while (n > 0)

                // Check for missing dictionary first
                if (inf.needsDictionary()) {
                    throw ZipException("ZLIB dictionary missing")
                }
                // Check the decompressor
                if (inf.finished() || len == 0 /* no more input */) {
                    break
                }
            }
        } catch (ex: DataFormatException) {
            // Improperly formatted compressed (ZIP) data
            var msg = ex.message
            if (msg == null) {
                msg = "Invalid ZLIB data format"
            }
            throw ZipException(msg)
        }
    }
}