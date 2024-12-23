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


/**
 * This class provides support for general purpose compression using the
 * popular ZLIB compression library. The ZLIB compression library was
 * initially developed as part of the PNG graphics standard and is not
 * protected by patents. It is fully described in the specifications at
 * the <a href="package-summary.html#package-description">java.util.zip
 * package description</a>.
 * <p>
 * Unless otherwise noted, passing a {@code null} argument to a method
 * in this class will cause a {@link NullPointerException} to be
 * thrown.
 * <p>
 * This class deflates sequences of bytes into ZLIB compressed data format.
 * The input byte sequence is provided in either byte array or byte buffer,
 * via one of the {@code setInput()} methods. The output byte sequence is
 * written to the output byte array or byte buffer passed to the
 * {@code deflate()} methods.
 * <p>
 * The following code fragment demonstrates a trivial compression
 * and decompression of a string using {@code Deflater} and
 * {@code Inflater}.
 * {@snippet id="compdecomp" lang="java" class="Snippets" region="DeflaterInflaterExample"}
 *
 * @apiNote
 * To release resources used by this {@code Deflater}, the {@link #end()} method
 * should be called explicitly. Subclasses are responsible for the cleanup of resources
 * acquired by the subclass. Subclasses that override {@link #finalize()} in order
 * to perform cleanup should be modified to use alternative cleanup mechanisms such
 * as {@link java.lang.ref.Cleaner} and remove the overriding {@code finalize} method.
 *
 * @see         Inflater
 * @author      David Connelly
 * @since 1.1
 */
class Deflater {
    private val zsRef: DeflaterZStreamRef
    private var input: ByteBuffer = ZipUtils.defaultBuf
    private var inputArray: ByteArray? = null
    private var inputPos = 0
    private var inputLim = 0
    private var level = 0
    private var strategy = 0
    private var setParams = false
    private var finish = false
    private var finished = false
    private var bytesRead: Long = 0
    private var bytesWritten: Long = 0

    /**
     * Compression method for the deflate algorithm (the only one currently
     * supported).
     */
    companion object {
        const val DEFLATED = 8

        /**
         * Compression level for no compression.
         */
        const val NO_COMPRESSION = 0

        /**
         * Compression level for fastest compression.
         */
        const val BEST_SPEED = 1

        /**
         * Compression level for best compression.
         */
        const val BEST_COMPRESSION = 9

        /**
         * Default compression level.
         */
        const val DEFAULT_COMPRESSION = -1

        /**
         * Compression strategy best used for data consisting mostly of small
         * values with a somewhat random distribution. Forces more Huffman coding
         * and less string matching.
         */
        const val FILTERED = 1

        /**
         * Compression strategy for Huffman coding only.
         */
        const val HUFFMAN_ONLY = 2

        /**
         * Default compression strategy.
         */
        const val DEFAULT_STRATEGY = 0

        /**
         * Compression flush mode used to achieve best compression result.
         *
         * @see Deflater.deflate
         * @since 1.7
         */
        const val NO_FLUSH = 0

        /**
         * Compression flush mode used to flush out all pending output; may
         * degrade compression for some compression algorithms.
         *
         * @see Deflater.deflate
         * @since 1.7
         */
        const val SYNC_FLUSH = 2

        /**
         * Compression flush mode used to flush out all pending output and
         * reset the deflater. Using this mode too often can seriously degrade
         * compression.
         *
         * @see Deflater.deflate
         * @since 1.7
         */
        const val FULL_FLUSH = 3

        /**
         * Flush mode to use at the end of output.  Can only be provided by the
         * user by way of {@link #finish()}.
         */
        private const val FINISH = 4

        init {
            ZipUtils.loadLibrary()
        }
    }

    /**
     * Creates a new compressor using the specified compression level.
     * If 'nowrap' is true then the ZLIB header and checksum fields will
     * not be used in order to support the compression format used in
     * both GZIP and PKZIP.
     * @param level the compression level (0-9)
     * @param nowrap if true then use GZIP compatible compression
     */
    @Suppress("this-escape")
    constructor(level: Int, nowrap: Boolean) {
        this.level = level
        this.strategy = DEFAULT_STRATEGY
        this.zsRef = DeflaterZStreamRef(this, init(level, DEFAULT_STRATEGY, nowrap))
    }

    /**
     * Creates a new compressor using the specified compression level.
     * Compressed data will be generated in ZLIB format.
     * @param level the compression level (0-9)
     */
    constructor(level: Int) : this(level, false)

    /**
     * Creates a new compressor with the default compression level.
     * Compressed data will be generated in ZLIB format.
     */
    constructor() : this(DEFAULT_COMPRESSION, false)

    /**
     * Sets input data for compression.
     * <p>
     * One of the {@code setInput()} methods should be called whenever
     * {@code needsInput()} returns true indicating that more input data
     * is required.
     * @param input the input data bytes
     * @param off the start offset of the data
     * @param len the length of the data
     * @see Deflater.needsInput
     */
    fun setInput(input: ByteArray, off: Int, len: Int) {
        Preconditions.checkFromIndexSize(off, len, input.size, Preconditions.AIOOBE_FORMATTER)
        synchronized(zsRef) {
            this.input = null
            this.inputArray = input
            this.inputPos = off
            this.inputLim = off + len
        }
    }

    /**
     * Sets input data for compression.
     * <p>
     * One of the {@code setInput()} methods should be called whenever
     * {@code needsInput()} returns true indicating that more input data
     * is required.
     * @param input the input data bytes
     * @see Deflater.needsInput
     */
    fun setInput(input: ByteArray) {
        setInput(input, 0, input.size)
    }

    /**
     * Sets input data for compression.
     * <p>
     * One of the {@code setInput()} methods should be called whenever
     * {@code needsInput()} returns true indicating that more input data
     * is required.
     * <p>
     * The given buffer's position will be advanced as deflate
     * operations are performed, up to the buffer's limit.
     * The input buffer may be modified (refilled) between deflate
     * operations; doing so is equivalent to creating a new buffer
     * and setting it with this method.
     * <p>
     * Modifying the input buffer's contents, position, or limit
     * concurrently with an deflate operation will result in
     * undefined behavior, which may include incorrect operation
     * results or operation failure.
     *
     * @param input the input data bytes
     * @see Deflater.needsInput
     * @since 11
     */
    fun setInput(input: ByteBuffer) {
        Objects.requireNonNull(input)
        synchronized(zsRef) {
            this.input = input
            this.inputArray = null
        }
    }

    /**
     * Sets preset dictionary for compression. A preset dictionary is used
     * when the history buffer can be predetermined. When the data is later
     * uncompressed with Inflater.inflate(), Inflater.getAdler() can be called
     * in order to get the Adler-32 value of the dictionary required for
     * decompression.
     * @param dictionary the dictionary data bytes
     * @param off the start offset of the data
     * @param len the length of the data
     * @see Inflater.inflate
     * @see Inflater.getAdler
     */
    fun setDictionary(dictionary: ByteArray, off: Int, len: Int) {
        Preconditions.checkFromIndexSize(off, len, dictionary.size, Preconditions.AIOOBE_FORMATTER)
        synchronized(zsRef) {
            ensureOpen()
            setDictionary(zsRef.address(), dictionary, off, len)
        }
    }

    /**
     * Sets preset dictionary for compression. A preset dictionary is used
     * when the history buffer can be predetermined. When the data is later
     * uncompressed with Inflater.inflate(), Inflater.getAdler() can be called
     * in order to get the Adler-32 value of the dictionary required for
     * decompression.
     * @param dictionary the dictionary data bytes
     * @see Inflater.inflate
     * @see Inflater.getAdler
     */
    fun setDictionary(dictionary: ByteArray) {
        setDictionary(dictionary, 0, dictionary.size)
    }

    /**
     * Sets preset dictionary for compression. A preset dictionary is used
     * when the history buffer can be predetermined. When the data is later
     * uncompressed with Inflater.inflate(), Inflater.getAdler() can be called
     * in order to get the Adler-32 value of the dictionary required for
     * decompression.
     * <p>
     * The bytes in given byte buffer will be fully consumed by this method.  On
     * return, its position will equal its limit.
     *
     * @param dictionary the dictionary data bytes
     * @see Inflater.inflate
     * @see Inflater.getAdler
     *
     * @since 11
     */
    fun setDictionary(dictionary: ByteBuffer) {
        synchronized(zsRef) {
            val position = dictionary.position()
            val remaining = maxOf(dictionary.limit() - position, 0)
            ensureOpen()
            if (dictionary.isDirect) {
                NIO_ACCESS.acquireSession(dictionary)
                try {
                    val address = (dictionary as DirectBuffer).address()
                    setDictionaryBuffer(zsRef.address(), address + position, remaining)
                } finally {
                    NIO_ACCESS.releaseSession(dictionary)
                }
            } else {
                val array = ZipUtils.getBufferArray(dictionary)
                val offset = ZipUtils.getBufferOffset(dictionary)
                setDictionary(zsRef.address(), array, offset + position, remaining)
            }
            dictionary.position(position + remaining)
        }
    }

    /**
     * Sets the compression strategy to the specified value.
     *
     * <p> If the compression strategy is changed, the next invocation
     * of {@code deflate} will compress the input available so far with
     * the old strategy (and may be flushed); the new strategy will take
     * effect only after that invocation.
     *
     * @param strategy the new compression strategy
     * @throws    IllegalArgumentException if the compression strategy is
     *                                     invalid
     */
    fun setStrategy(strategy: Int) {
        when (strategy) {
            DEFAULT_STRATEGY, FILTERED, HUFFMAN_ONLY -> {}
            else -> throw IllegalArgumentException()
        }
        synchronized(zsRef) {
            if (this.strategy != strategy) {
                this.strategy = strategy
                setParams = true
            }
        }
    }

    /**
     * Sets the compression level to the specified value.
     *
     * <p> If the compression level is changed, the next invocation
     * of {@code deflate} will compress the input available so far
     * with the old level (and may be flushed); the new level will
     * take effect only after that invocation.
     *
     * @param level the new compression level (0-9)
     * @throws    IllegalArgumentException if the compression level is invalid
     */
    fun setLevel(level: Int) {
        if (level in 0..9 || level == DEFAULT_COMPRESSION) {
            synchronized(zsRef) {
                if (this.level != level) {
                    this.level = level
                    setParams = true
                }
            }
        } else {
            throw IllegalArgumentException("invalid compression level")
        }
    }

    /**
     * Returns true if no data remains in the input buffer. This can
     * be used to determine if one of the {@code setInput()} methods should be
     * called in order to provide more input.
     *
     * @return true if the input data buffer is empty and setInput()
     * should be called in order to provide more input
     */
    fun needsInput(): Boolean {
        synchronized(zsRef) {
            val input = this.input
            return input?.hasRemaining() != true && inputLim == inputPos
        }
    }

    /**
     * When called, indicates that compression should end with the current
     * contents of the input buffer.
     */
    fun finish() {
        synchronized(zsRef) {
            finish = true
        }
    }

    /**
     * Returns true if the end of the compressed data output stream has
     * been reached.
     * @return true if the end of the compressed data output stream has
     * been reached
     */
    fun finished(): Boolean {
        synchronized(zsRef) {
            return finished
        }
    }

    /**
     * Compresses the input data and fills specified buffer with compressed
     * data. Returns actual number of bytes of compressed data. A return value
     * of 0 indicates that {@link #needsInput() needsInput} should be called
     * in order to determine if more input data is required.
     *
     * <p>This method uses {@link #NO_FLUSH} as its compression flush mode.
     * An invocation of this method of the form {@code deflater.deflate(b, off, len)}
     * yields the same result as the invocation of
     * {@code deflater.deflate(b, off, len, Deflater.NO_FLUSH)}.
     *
     * @param output the buffer for the compressed data
     * @param off the start offset of the data
     * @param len the maximum number of bytes of compressed data
     * @return the actual number of bytes of compressed data written to the
     *         output buffer
     */
    fun deflate(output: ByteArray, off: Int, len: Int): Int {
        return deflate(output, off, len, NO_FLUSH)
    }

    /**
     * Compresses the input data and fills specified buffer with compressed
     * data. Returns actual number of bytes of compressed data. A return value
     * of 0 indicates that {@link #needsInput() needsInput} should be called
     * in order to determine if more input data is required.
     *
     * <p>This method uses {@link #NO_FLUSH} as its compression flush mode.
     * An invocation of this method of the form {@code deflater.deflate(b)}
     * yields the same result as the invocation of
     * {@code deflater.deflate(b, 0, b.length, Deflater.NO_FLUSH)}.
     *
     * @param output the buffer for the compressed data
     * @return the actual number of bytes of compressed data written to the
     *         output buffer
     */
    fun deflate(output: ByteArray): Int {
        return deflate(output, 0, output.size, NO_FLUSH)
    }

    /**
     * Compresses the input data and fills specified buffer with compressed
     * data. Returns actual number of bytes of compressed data. A return value
     * of 0 indicates that {@link #needsInput() needsInput} should be called
     * in order to determine if more input data is required.
     *
     * <p>This method uses {@link #NO_FLUSH} as its compression flush mode.
     * An invocation of this method of the form {@code deflater.deflate(output)}
     * yields the same result as the invocation of
     * {@code deflater.deflate(output, Deflater.NO_FLUSH)}.
     *
     * @param output the buffer for the compressed data
     * @return the actual number of bytes of compressed data written to the
     *         output buffer
     * @throws ReadOnlyBufferException if the given output buffer is read-only
     * @since 11
     */
    fun deflate(output: ByteBuffer): Int {
        return deflate(output, NO_FLUSH)
    }

    /**
     * Compresses the input data and fills the specified buffer with compressed
     * data. Returns actual number of bytes of data compressed.
     *
     * <p>Compression flush mode is one of the following three modes:
     *
     * <ul>
     * <li>{@link #NO_FLUSH}: allows the deflater to decide how much data
     * to accumulate, before producing output, in order to achieve the best
     * compression (should be used in normal use scenario). A return value
     * of 0 in this flush mode indicates that {@link #needsInput()} should
     * be called in order to determine if more input data is required.
     *
     * <li>{@link #SYNC_FLUSH}: all pending output in the deflater is flushed,
     * to the specified output buffer, so that an inflater that works on
     * compressed data can get all input data available so far (In particular
     * the {@link #needsInput()} returns {@code true} after this invocation
     * if enough output space is provided). Flushing with {@link #SYNC_FLUSH}
     * may degrade compression for some compression algorithms and so it
     * should be used only when necessary.
     *
     * <li>{@link #FULL_FLUSH}: all pending output is flushed out as with
     * {@link #SYNC_FLUSH}. The compression state is reset so that the inflater
     * that works on the compressed output data can restart from this point
     * if previous compressed data has been damaged or if random access is
     * desired. Using {@link #FULL_FLUSH} too often can seriously degrade
     * compression.
     * </ul>
     *
     * <p>In the case of {@link #FULL_FLUSH} or {@link #SYNC_FLUSH}, if
     * the return value is {@code len}, the space available in output
     * buffer {@code b}, this method should be invoked again with the same
     * {@code flush} parameter and more output space. Make sure that
     * {@code len} is greater than 6 to avoid flush marker (5 bytes) being
     * repeatedly output to the output buffer every time this method is
     * invoked.
     *
     * <p>If the {@link #setInput(ByteBuffer)} method was called to provide a buffer
     * for input, the input buffer's position will be advanced by the number of bytes
     * consumed by this operation.
     *
     * @param output the buffer for the compressed data
     * @param off the start offset of the data
     * @param len the maximum number of bytes of compressed data
     * @param flush the compression flush mode
     * @return the actual number of bytes of compressed data written to
     *         the output buffer
     *
     * @throws IllegalArgumentException if the flush mode is invalid
     * @since 1.7
     */
    fun deflate(output: ByteArray, off: Int, len: Int, flush: Int): Int {
        Preconditions.checkFromIndexSize(off, len, output.size, Preconditions.AIOOBE_FORMATTER)
        if (flush != NO_FLUSH && flush != SYNC_FLUSH && flush != FULL_FLUSH) {
            throw IllegalArgumentException()
        }
        synchronized(zsRef) {
            ensureOpen()

            val input = this.input
            if (finish) {
                flush = FINISH
            }
            var params = if (setParams) {
                1 or (strategy shl 1) or (level shl 3)
            } else {
                0
            }
            val inputPos: Int
            val result: Long
            if (input == null) {
                inputPos = this.inputPos
                result = deflateBytesBytes(
                    zsRef.address(),
                    inputArray!!, inputPos, inputLim - inputPos,
                    output, off, len,
                    flush, params
                )
            } else {
                inputPos = input.position()
                val inputRem = maxOf(input.limit() - inputPos, 0)
                if (input.isDirect) {
                    NIO_ACCESS.acquireSession(input)
                    try {
                        val inputAddress = (input as DirectBuffer).address()
                        result = deflateBufferBytes(
                            zsRef.address(),
                            inputAddress + inputPos, inputRem,
                            output, off, len,
                            flush, params
                        )
                    } finally {
                        NIO_ACCESS.releaseSession(input)
                    }
                } else {
                    val inputArray = ZipUtils.getBufferArray(input)
                    val inputOffset = ZipUtils.getBufferOffset(input)
                    result = deflateBytesBytes(
                        zsRef.address(),
                        inputArray, inputOffset + inputPos, inputRem,
                        output, off, len,
                        flush, params
                    )
                }
            }
            val read = (result and 0x7fff_ffffL).toInt()
            val written = (result ushr 31 and 0x7fff_ffffL).toInt()
            if ((result ushr 62 and 1) != 0L) {
                finished = true
            }
            if (params != 0 && (result ushr 63 and 1) == 0L) {
                setParams = false
            }
            if (input != null) {
                input.position(inputPos + read)
            } else {
                this.inputPos = inputPos + read
            }
            bytesWritten += written.toLong()
            bytesRead += read.toLong()
            return written
        }
    }

    /**
     * Compresses the input data and fills the specified buffer with compressed
     * data. Returns actual number of bytes of data compressed.
     *
     * <p>Compression flush mode is one of the following three modes:
     *
     * <ul>
     * <li>{@link #NO_FLUSH}: allows the deflater to decide how much data
     * to accumulate, before producing output, in order to achieve the best
     * compression (should be used in normal use scenario). A return value
     * of 0 in this flush mode indicates that {@link #needsInput()} should
     * be called in order to determine if more input data is required.
     *
     * <li>{@link #SYNC_FLUSH}: all pending output in the deflater is flushed,
     * to the specified output buffer, so that an inflater that works on
     * compressed data can get all input data available so far (In particular
     * the {@link #needsInput()} returns {@code true} after this invocation
     * if enough output space is provided). Flushing with {@link #SYNC_FLUSH}
     * may degrade compression for some compression algorithms and so it
     * should be used only when necessary.
     *
     * <li>{@link #FULL_FLUSH}: all pending output is flushed out as with
     * {@link #SYNC_FLUSH}. The compression state is reset so that the inflater
     * that works on the compressed output data can restart from this point
     * if previous compressed data has been damaged or if random access is
     * desired. Using {@link #FULL_FLUSH} too often can seriously degrade
     * compression.
     * </ul>
     *
     * <p>In the case of {@link #FULL_FLUSH} or {@link #SYNC_FLUSH}, if
     * the return value is equal to the {@linkplain ByteBuffer#remaining() remaining space}
     * of the buffer, this method should be invoked again with the same
     * {@code flush} parameter and more output space. Make sure that
     * the buffer has at least 6 bytes of remaining space to avoid the
     * flush marker (5 bytes) being repeatedly output to the output buffer
     * every time this method is invoked.
     *
     * <p>On success, the position of the given {@code output} byte buffer will be
     * advanced by as many bytes as were produced by the operation, which is equal
     * to the number returned by this method.
     *
     * <p>If the {@link #setInput(ByteBuffer)} method was called to provide a buffer
     * for input, the input buffer's position will be advanced by the number of bytes
     * consumed by this operation.
     *
     * @param output the buffer for the compressed data
     * @param flush the compression flush mode
     * @return the actual number of bytes of compressed data written to
     *         the output buffer
     *
     * @throws IllegalArgumentException if the flush mode is invalid
     * @throws ReadOnlyBufferException if the given output buffer is read-only
     * @since 11
     */
    fun deflate(output: ByteBuffer, flush: Int): Int {
        if (output.isReadOnly) {
            throw ReadOnlyBufferException()
        }
        if (flush != NO_FLUSH && flush != SYNC_FLUSH && flush != FULL_FLUSH) {
            throw IllegalArgumentException()
        }
        synchronized(zsRef) {
            ensureOpen()

            val input = this.input
            if (finish) {
                flush = FINISH
            }
            var params = if (setParams) {
                1 or (strategy shl 1) or (level shl 3)
            } else {
                0
            }
            val outputPos = output.position()
            val outputRem = maxOf(output.limit() - outputPos, 0)
            val inputPos: Int
            val result: Long
            if (input == null) {
                inputPos = this.inputPos
                if (output.isDirect) {
                    NIO_ACCESS.acquireSession(output)
                    try {
                        val outputAddress = (output as DirectBuffer).address()
                        result = deflateBytesBuffer(
                            zsRef.address(),
                            inputArray!!, inputPos, inputLim - inputPos,
                            outputAddress + outputPos, outputRem,
                            flush, params
                        )
                    } finally {
                        NIO_ACCESS.releaseSession(output)
                    }
                } else {
                    val outputArray = ZipUtils.getBufferArray(output)
                    val outputOffset = ZipUtils.getBufferOffset(output)
                    result = deflateBytesBytes(
                        zsRef.address(),
                        inputArray!!, inputPos, inputLim - inputPos,
                        outputArray, outputOffset + outputPos, outputRem,
                        flush, params
                    )
                }
            } else {
                inputPos = input.position()
                val inputRem = maxOf(input.limit() - inputPos, 0)
                if (input.isDirect) {
                    NIO_ACCESS.acquireSession(input)
                    try {
                        val inputAddress = (input as DirectBuffer).address()
                        if (output.isDirect) {
                            NIO_ACCESS.acquireSession(output)
                            try {
                                val outputAddress = outputPos + (output as DirectBuffer).address()
                                result = deflateBufferBuffer(
                                    zsRef.address(),
                                    inputAddress + inputPos, inputRem,
                                    outputAddress, outputRem,
                                    flush, params
                                )
                            } finally {
                                NIO_ACCESS.releaseSession(output)
                            }
                        } else {
                            val outputArray = ZipUtils.getBufferArray(output)
                            val outputOffset = ZipUtils.getBufferOffset(output)
                            result = deflateBufferBytes(
                                zsRef.address(),
                                inputAddress + inputPos, inputRem,
                                outputArray, outputOffset + outputPos, outputRem,
                                flush, params
                            )
                        }
                    } finally {
                        NIO_ACCESS.releaseSession(input)
                    }
                } else {
                    val inputArray = ZipUtils.getBufferArray(input)
                    val inputOffset = ZipUtils.getBufferOffset(input)
                    if (output.isDirect) {
                        NIO_ACCESS.acquireSession(output)
                        try {
                            val outputAddress = (output as DirectBuffer).address()
                            result = deflateBytesBuffer(
                                zsRef.address(),
                                inputArray, inputOffset + inputPos, inputRem,
                                outputAddress + outputPos, outputRem,
                                flush, params
                            )
                        } finally {
                            NIO_ACCESS.releaseSession(output)
                        }
                    } else {
                        val outputArray = ZipUtils.getBufferArray(output)
                        val outputOffset = ZipUtils.getBufferOffset(output)
                        result = deflateBytesBytes(
                            zsRef.address(),
                            inputArray, inputOffset + inputPos, inputRem,
                            outputArray, outputOffset + outputPos, outputRem,
                            flush, params
                        )
                    }
                }
            }
            val read = (result and 0x7fff_ffffL).toInt()
            val written = (result ushr 31 and 0x7fff_ffffL).toInt()
            if ((result ushr 62 and 1) != 0L) {
                finished = true
            }
            if (params != 0 && (result ushr 63 and 1) == 0L) {
                setParams = false
            }
            if (input != null) {
                input.position(inputPos + read)
            } else {
                this.inputPos = inputPos + read
            }
            output.position(outputPos + written)
            bytesWritten += written.toLong()
            bytesRead += read.toLong()
            return written
        }
    }

    /**
     * {@return the ADLER-32 value of the uncompressed data}
     */
    fun getAdler(): Int {
        synchronized(zsRef) {
            ensureOpen()
            return getAdler(zsRef.address())
        }
    }

    /**
     * Returns the total number of uncompressed bytes input so far.
     *
     * @implSpec
     * This method returns the equivalent of {@code (int) getBytesRead()}
     * and therefore cannot return the correct value when it is greater
     * than {@link Integer#MAX_VALUE}.
     *
     * @deprecated Use {@link #getBytesRead()} instead
     *
     * @return the total number of uncompressed bytes input so far
     */
    @Deprecated("Use {@link #getBytesRead()} instead", level = DeprecationLevel.WARNING)
    fun getTotalIn(): Int {
        return getBytesRead().toInt()
    }

    /**
     * Returns the total number of uncompressed bytes input so far.
     *
     * @return the total (non-negative) number of uncompressed bytes input so far
     * @since 1.5
     */
    fun getBytesRead(): Long {
        synchronized(zsRef) {
            ensureOpen()
            return bytesRead
        }
    }

    /**
     * Returns the total number of compressed bytes output so far.
     *
     * @implSpec
     * This method returns the equivalent of {@code (int) getBytesWritten()}
     * and therefore cannot return the correct value when it is greater
     * than {@link Integer#MAX_VALUE}.
     *
     * @deprecated Use {@link #getBytesWritten()} instead
     *
     * @return the total number of compressed bytes output so far
     */
    @Deprecated("Use {@link #getBytesWritten()} instead", level = DeprecationLevel.WARNING)
    fun getTotalOut(): Int {
        return getBytesWritten().toInt()
    }

    /**
     * Returns the total number of compressed bytes output so far.
     *
     * @return the total (non-negative) number of compressed bytes output so far
     * @since 1.5
     */
    fun getBytesWritten(): Long {
        synchronized(zsRef) {
            ensureOpen()
            return bytesWritten
        }
    }

    /**
     * Resets deflater so that a new set of input data can be processed.
     * Keeps current compression level and strategy settings.
     */
    fun reset() {
        synchronized(zsRef) {
            ensureOpen()
            reset(zsRef.address())
            finish = false
            finished = false
            input = ZipUtils.defaultBuf
            inputArray = null
            bytesRead = 0
            bytesWritten = 0
        }
    }

    /**
     * Closes the compressor and discards any unprocessed input.
     *
     * This method should be called when the compressor is no longer
     * being used. Once this method is called, the behavior of the
     * Deflater object is undefined.
     */
    fun end() {
        synchronized(zsRef) {
            zsRef.clean()
            input = ZipUtils.defaultBuf
        }
    }

    private fun ensureOpen() {
        assert(Thread.holdsLock(zsRef))
        if (zsRef.address() == 0L)
            throw NullPointerException("Deflater has been closed")
    }

    /**
     * Returns the value of 'finish' flag.
     * 'finish' will be set to true if def.finish() method is called.
     */
    fun shouldFinish(): Boolean {
        synchronized(zsRef) {
            return finish
        }
    }

    private external fun deflatorInit(level: Int, strategy: Int, nowrap: Boolean): Long
    private external fun setDictionary(addr: Long, b: ByteArray, off: Int, len: Int)
    private external fun setDictionaryBuffer(addr: Long, bufAddress: Long, len: Int)
    private external fun deflateBytesBytes(
        addr: Long,
        inputArray: ByteArray, inputOff: Int, inputLen: Int,
        outputArray: ByteArray, outputOff: Int, outputLen: Int,
        flush: Int, params: Int
    ): Long

    private external fun deflateBytesBuffer(
        addr: Long,
        inputArray: ByteArray, inputOff: Int, inputLen: Int,
        outputAddress: Long, outputLen: Int,
        flush: Int, params: Int
    ): Long

    private external fun deflateBufferBytes(
        addr: Long,
        inputAddress: Long, inputLen: Int,
        outputArray: ByteArray, outputOff: Int, outputLen: Int,
        flush: Int, params: Int
    ): Long

    private external fun deflateBufferBuffer(
        addr: Long,
        inputAddress: Long, inputLen: Int,
        outputAddress: Long, outputLen: Int,
        flush: Int, params: Int
    ): Long

    private external fun getAdler(addr: Long): Int
    private external fun reset(addr: Long)
    private external fun end(addr: Long)

    /**
     * A reference to the native zlib's z_stream structure. It also
     * serves as the "cleaner" to clean up the native resource when
     * the Deflater is ended, closed or cleaned.
     */
    internal class DeflaterZStreamRef(owner: Deflater?, addr: Long) : Runnable {
        private var address: Long
        private val cleanable: Cleanable?

        init {
            cleanable = owner?.let { CleanerFactory.cleaner().register(it, this) }
            address = addr
        }

        fun address() = address

        fun clean() {
            cleanable?.clean()
        }

        @Synchronized
        override fun run() {
            val addr = address
            address = 0
            if (addr != 0L) {
                end(addr)
            }
        }
    }
}