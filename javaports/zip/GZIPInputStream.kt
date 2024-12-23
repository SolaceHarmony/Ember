/*
 * Copyright (c) 1996, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.SequenceInputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.EOFException

/**
 * This class implements a stream filter for reading compressed data in
 * the GZIP file format.
 *
 * @see         InflaterInputStream
 * @author      David Connelly
 * @since 1.1
 *
 */
public class GZIPInputStream : InflaterInputStream {

    /**
     * CRC-32 for uncompressed data.
     */
    protected var crc = CRC32()

    /**
     * Indicates end of input stream.
     */
    protected var eos = false

    private var closed = false

    /**
     * Check to make sure that this stream has not been closed
     */
    private fun ensureOpen() {
        if (closed) {
            throw IOException("Stream closed")
        }
    }

    /**
     * Creates a new input stream with the specified buffer size.
     * @param in the input stream
     * @param size the input buffer size
     *
     * @throws    ZipException if a GZIP format error has occurred or the
     *                         compression method used is unsupported
     * @throws    NullPointerException if {@code in} is null
     * @throws    IOException if an I/O error has occurred
     * @throws    IllegalArgumentException if {@code size <= 0}
     */
    constructor(`in`: InputStream, size: Int) : super(`in`, createInflater(`in`, size), size) {
        usesDefaultInflater = true
        try {
            readHeader(`in`)
        } catch (ioe: IOException) {
            inf.end()
            throw ioe
        }
    }

    /*
     * Creates and returns an Inflater only if the input stream is not null and the
     * buffer size is > 0.
     * If the input stream is null, then this method throws a
     * NullPointerException. If the size is <= 0, then this method throws
     * an IllegalArgumentException
     */
    private companion object {
        private fun createInflater(`in`: InputStream, size: Int): Inflater {
            Objects.requireNonNull(`in`)
            if (size <= 0) {
                throw IllegalArgumentException("buffer size <= 0")
            }
            return Inflater(true)
        }
    }

    /**
     * Creates a new input stream with a default buffer size.
     * @param in the input stream
     *
     * @throws    ZipException if a GZIP format error has occurred or the
     *                         compression method used is unsupported
     * @throws    NullPointerException if {@code in} is null
     * @throws    IOException if an I/O error has occurred
     */
    constructor(`in`: InputStream) : this(`in`, 512)

    /**
     * Reads uncompressed data into an array of bytes, returning the number of inflated
     * bytes. If {@code len} is not zero, the method will block until some input can be
     * decompressed; otherwise, no bytes are read and {@code 0} is returned.
     * <p>
     * If this method returns a nonzero integer <i>n</i> then {@code buf[off]}
     * through {@code buf[off+}<i>n</i>{@code -1]} contain the uncompressed
     * data.  The content of elements {@code buf[off+}<i>n</i>{@code ]} through
     * {@code buf[off+}<i>len</i>{@code -1]} is undefined, contrary to the
     * specification of the {@link java.io.InputStream InputStream} superclass,
     * so an implementation is free to modify these elements during the inflate
     * operation. If this method returns {@code -1} or throws an exception then
     * the content of {@code buf[off]} through {@code buf[off+}<i>len</i>{@code
     * -1]} is undefined.
     *
     * @param buf the buffer into which the data is read
     * @param off the start offset in the destination array {@code buf}
     * @param len the maximum number of bytes read
     * @return  the actual number of bytes inflated, or -1 if the end of the
     *          compressed input stream is reached
     *
     * @throws     NullPointerException If {@code buf} is {@code null}.
     * @throws     IndexOutOfBoundsException If {@code off} is negative,
     * {@code len} is negative, or {@code len} is greater than
     * {@code buf.length - off}
     * @throws    ZipException if the compressed input data is corrupt.
     * @throws    IOException if an I/O error has occurred.
     *
     */
    @Throws(IOException::class)
    override fun read(buf: ByteArray, off: Int, len: Int): Int {
        ensureOpen()
        if (eos) {
            return -1
        }
        val n = super.read(buf, off, len)
        if (n == -1) {
            if (readTrailer())
                eos = true
            else
                return this.read(buf, off, len)
        } else {
            crc.update(buf, off, n)
        }
        return n
    }

    /**
     * Closes this input stream and releases any system resources associated
     * with the stream.
     * @throws    IOException if an I/O error has occurred
     */
    @Throws(IOException::class)
    override fun close() {
        if (!closed) {
            super.close()
            eos = true
            closed = true
        }
    }

    /**
     * GZIP header magic number.
     */
    companion object {
        const val GZIP_MAGIC = 0x8b1f
    }

    /*
     * File header flags.
     */
    private val FTEXT = 1 // Extra text
    private val FHCRC = 2 // Header CRC
    private val FEXTRA = 4 // Extra field
    private val FNAME = 8 // File name
    private val FCOMMENT = 16 // File comment

    /*
     * Reads GZIP member header and returns the total byte number
     * of this member header.
     */
    @Throws(IOException::class)
    private fun readHeader(this_in: InputStream): Int {
        val `in` = CheckedInputStream(this_in, crc)
        crc.reset()
        // Check header magic
        if (readUShort(`in`) != GZIP_MAGIC) {
            throw ZipException("Not in GZIP format")
        }
        // Check compression method
        if (readUByte(`in`) != 8) {
            throw ZipException("Unsupported compression method")
        }
        // Read flags
        val flg = readUByte(`in`)
        // Skip MTIME, XFL, and OS fields
        skipBytes(`in`, 6)
        var n = 2 + 2 + 6
        // Skip optional extra field
        if (flg and FEXTRA == FEXTRA) {
            val m = readUShort(`in`)
            skipBytes(`in`, m)
            n += m + 2
        }
        // Skip optional file name
        if (flg and FNAME == FNAME) {
            do {
                n++
            } while (readUByte(`in`) != 0)
        }
        // Skip optional file comment
        if (flg and FCOMMENT == FCOMMENT) {
            do {
                n++
            } while (readUByte(`in`) != 0)
        }
        // Check optional header CRC
        if (flg and FHCRC == FHCRC) {
            val v = (crc.value.toInt() and 0xffff)
            if (readUShort(`in`) != v) {
                throw ZipException("Corrupt GZIP header")
            }
            n += 2
        }
        crc.reset()
        return n
    }

    /*
     * Reads GZIP member trailer and returns true if the eos
     * reached, false if there are more (concatenated gzip
     * data set)
     */
    @Throws(IOException::class)
    private fun readTrailer(): Boolean {
        var `in` = this.`in`
        val n = inf.remaining
        if (n > 0) {
            `in` = SequenceInputStream(
                ByteArrayInputStream(buf, len - n, n),
                object : FilterInputStream(`in`) {
                    @Throws(IOException::class)
                    override fun close() {
                    }
                })
        }
        // Uses left-to-right evaluation order
        if ((readUInt(`in`) != crc.value) ||
            // rfc1952; ISIZE is the input size modulo 2^32
            (readUInt(`in`) != (inf.bytesWritten and 0xffffffffL))
        ) throw ZipException("Corrupt GZIP trailer")

        // try concatenated case
        var m = 8 // this.trailer
        try {
            m += readHeader(`in`) // next.header
        } catch (ze: IOException) {
            return true // ignore any malformed, do nothing
        }
        inf.reset()
        if (n > m)
            inf.setInput(buf, len - n + m, n - m)
        return false
    }

    /*
     * Reads unsigned integer in Intel byte order.
     */
    @Throws(IOException::class)
    private fun readUInt(`in`: InputStream): Long {
        val s = readUShort(`in`)
        return (readUShort(`in`).toLong() shl 16) or s.toLong()
    }

    /*
     * Reads unsigned short in Intel byte order.
     */
    @Throws(IOException::class)
    private fun readUShort(`in`: InputStream): Int {
        val b = readUByte(`in`)
        return (readUByte(`in`) shl 8) or b
    }

    /*
     * Reads unsigned byte.
     */
    @Throws(IOException::class)
    private fun readUByte(`in`: InputStream): Int {
        val b = `in`.read()
        if (b == -1) {
            throw EOFException()
        }
        if (b < -1 || b > 255) {
            // Report on this.in, not argument in; see read{Header, Trailer}.
            throw IOException(
                this.`in`.javaClass.name
                        + ".read() returned value out of range -1..255: " + b
            )
        }
        return b
    }

    private val tmpbuf = ByteArray(128)

    /*
     * Skips bytes of input data blocking until all bytes are skipped.
     * Does not assume that the input stream is capable of seeking.
     */
    @Throws(IOException::class)
    private fun skipBytes(`in`: InputStream, n: Int) {
        var remaining = n
        while (remaining > 0) {
            val len = `in`.read(tmpbuf, 0, if (remaining < tmpbuf.size) remaining else tmpbuf.size)
            if (len == -1) {
                throw EOFException()
            }
            remaining -= len
        }
    }
}