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

import java.lang.ref.Cleaner.Cleanable
import java.nio.ReadOnlyBufferException
import jdk.internal.ref.CleanerFactory
import jdk.internal.util.Preconditions
import sun.nio.ch.DirectBuffer
import java.util.zip.DataFormatException
import java.util.zip.ZipUtils.NIO_ACCESS

/**
 * This class provides support for general purpose decompression using the
 * popular ZLIB compression library. The ZLIB compression library was
 * initially developed as part of the PNG graphics standard and is not
 * protected by patents. It is fully described in the specifications at
 * the <a href="package-summary.html#package-description">java.util.zip
 * package description</a>.
 * <p>
 * Unless otherwise noted, passing a `null` argument to a constructor
 * or method in this class will cause a {@link NullPointerException} to be
 * thrown.
 * <p>
 * This class inflates sequences of ZLIB compressed bytes. The input byte
 * sequence is provided in either byte array or byte buffer, via one of the
 * `setInput()` methods. The output byte sequence is written to the
 * output byte array or byte buffer passed to the `inflate()` methods.
 * <p>
 * The following code fragment demonstrates a trivial compression
 * and decompression of a string using `Deflater` and
 * `Inflater`.
 * {@snippet id="compdecomp" lang="java" class="Snippets" region="DeflaterInflaterExample"}
 *
 * @apiNote
 * To release resources used by this `Inflater`, the {@link #end()} method
 * should be called explicitly. Subclasses are responsible for the cleanup of resources
 * acquired by the subclass. Subclasses that override {@link #finalize()} in order
 * to perform cleanup should be modified to use alternative cleanup mechanisms such
 * as {@link java.lang.ref.Cleaner} and remove the overriding `finalize` method.
 *
 * @see         Deflater
 * @author      David Connelly
 * @since 1.1
 *
 */

class Inflater {

    private val zsRef: InflaterZStreamRef
    private var input: ByteBuffer? = ZipUtils.defaultBuf
    private var inputArray: ByteArray? = null
    private var inputPos = 0
    private var inputLim = 0
    private var finished = false
    private var pendingOutput = false
    private var needDict = false
    private var bytesRead: Long = 0
    private var bytesWritten: Long = 0

    /*
     * These fields are used as an "out" parameter from JNI when a
     * DataFormatException is thrown during the inflate operation.
     */
    private var inputConsumed = 0
    private var outputConsumed = 0

    init {
        ZipUtils.loadLibrary()
        initIDs()
    }

    /**
     * Creates a new decompressor. If the parameter 'nowrap' is true then
     * the ZLIB header and checksum fields will not be used. This provides
     * compatibility with the compression format used by both GZIP and PKZIP.
     * <p>
     * Note: When using the 'nowrap' option it is also necessary to provide
     * an extra "dummy" byte as input. This is required by the ZLIB native
     * library in order to support certain optimizations.
     *
     * @param nowrap if true then support GZIP compatible compression
     */
    @SuppressWarnings("this-escape")
    constructor(nowrap: Boolean) {
        this.zsRef = InflaterZStreamRef(this, init(nowrap))
    }

    /**
     * Creates a new decompressor.
     */
    constructor() {
        this.zsRef = InflaterZStreamRef(this, init(false))
    }

    /**
     * Sets input data for decompression.
     * <p>
     * One of the `setInput()` methods should be called whenever
     * `needsInput()` returns true indicating that more input data
     * is required.
     *
     * @param input the input data bytes
     * @param off the start offset of the input data
     * @param len the length of the input data
     * @see Inflater#needsInput
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
     * Sets input data for decompression.
     * <p>
     * One of the `setInput()` methods should be called whenever
     * `needsInput()` returns true indicating that more input data
     * is required.
     *
     * @param input the input data bytes
     * @see Inflater#needsInput
     */
    fun setInput(input: ByteArray) {
        setInput(input, 0, input.size)
    }

    /**
     * Sets input data for decompression.
     * <p>
     * One of the `setInput()` methods should be called whenever
     * `needsInput()` returns true indicating that more input data
     * is required.
     * <p>
     * The given buffer's position will be advanced as inflate
     * operations are performed, up to the buffer's limit.
     * The input buffer may be modified (refilled) between inflate
     * operations; doing so is equivalent to creating a new buffer
     * and setting it with this method.
     * <p>
     * Modifying the input buffer's contents, position, or limit
     * concurrently with an inflate operation will result in
     * undefined behavior, which may include incorrect operation
     * results or operation failure.
     *
     * @param input the input data bytes
     * @see Inflater#needsInput
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
     * Sets the preset dictionary to the given array of bytes. Should be
     * called when inflate() returns 0 and needsDictionary() returns true
     * indicating that a preset dictionary is required. The method getAdler()
     * can be used to get the Adler-32 value of the dictionary needed.
     * @param dictionary the dictionary data bytes
     * @param off the start offset of the data
     * @param len the length of the data
     * @see Inflater#needsDictionary
     * @see Inflater#getAdler
     */
    fun setDictionary(dictionary: ByteArray, off: Int, len: Int) {
        Preconditions.checkFromIndexSize(off, len, dictionary.size, Preconditions.AIOOBE_FORMATTER)
        synchronized(zsRef) {
            ensureOpen()
            setDictionary(zsRef.address(), dictionary, off, len)
            needDict = false
        }
    }

    /**
     * Sets the preset dictionary to the given array of bytes. Should be
     * called when inflate() returns 0 and needsDictionary() returns true
     * indicating that a preset dictionary is required. The method getAdler()
     * can be used to get the Adler-32 value of the dictionary needed.
     * @param dictionary the dictionary data bytes
     * @see Inflater#needsDictionary
     * @see Inflater#getAdler
     */
    fun setDictionary(dictionary: ByteArray) {
        setDictionary(dictionary, 0, dictionary.size)
    }

    /**
     * Sets the preset dictionary to the bytes in the given buffer. Should be
     * called when inflate() returns 0 and needsDictionary() returns true
     * indicating that a preset dictionary is required. The method getAdler()
     * can be used to get the Adler-32 value of the dictionary needed.
     * <p>
     * The bytes in given byte buffer will be fully consumed by this method.  On
     * return, its position will equal its limit.
     *
     * @param dictionary the dictionary data bytes
     * @see Inflater#needsDictionary
     * @see Inflater#getAdler
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
            needDict = false
        }
    }

    /**
     * Returns the total number of bytes remaining in the input buffer.
     * This can be used to find out what bytes still remain in the input
     * buffer after decompression has finished.
     * @return the total number of bytes remaining in the input buffer
     */
    fun getRemaining(): Int {
        synchronized(zsRef) {
            val input = this.input
            return input?.remaining() ?: (inputLim - inputPos)
        }
    }

    /**
     * Returns true if no data remains in the input buffer. This can
     * be used to determine if one of the `setInput()` methods should be
     * called in order to provide more input.
     *
     * @return true if no data remains in the input buffer
     */
    fun needsInput(): Boolean {
        synchronized(zsRef) {
            val input = this.input
            return input?.hasRemaining()?.not() ?: (inputLim == inputPos)
        }
    }

    /**
     * `return true if a preset dictionary is needed for decompression`
     * @see Inflater#setDictionary
     */
    fun needsDictionary(): Boolean {
        synchronized(zsRef) {
            return needDict
        }
    }

    /**
     * `return true if the end of the compressed data stream has been
     * reached`
     */
    fun finished(): Boolean {
        synchronized(zsRef) {
            return finished
        }
    }

    /**
     * Uncompresses bytes into specified buffer. Returns actual number
     * of bytes uncompressed. A return value of 0 indicates that
     * needsInput() or needsDictionary() should be called in order to
     * determine if more input data or a preset dictionary is required.
     * In the latter case, getAdler() can be used to get the Adler-32
     * value of the dictionary required.
     * <p>
     * If the {@link #setInput(ByteBuffer)} method was called to provide a buffer
     * for input, the input buffer's position will be advanced by the number of bytes
     * consumed by this operation, even in the event that a {@link DataFormatException}
     * is thrown.
     * <p>
     * The {@linkplain #getRemaining() remaining byte count} will be reduced by
     * the number of consumed input bytes.  If the {@link #setInput(ByteBuffer)}
     * method was called to provide a buffer for input, the input buffer's position
     * will be advanced the number of consumed bytes.
     * <p>
     * These byte totals, as well as
     * the {@linkplain #getBytesRead() total bytes read}
     * and the {@linkplain #getBytesWritten() total bytes written}
     * values, will be updated even in the event that a {@link DataFormatException}
     * is thrown to reflect the amount of data consumed and produced before the
     * exception occurred.
     *
     * @param output the buffer for the uncompressed data
     * @param off the start offset of the data
     * @param len the maximum number of uncompressed bytes
     * @return the actual number of uncompressed bytes
     * @throws java.util.zip.DataFormatException if the compressed data format is invalid
     * @see Inflater#needsInput
     * @see Inflater#needsDictionary
     */
    @Throws(DataFormatException::class)
    fun inflate(output: ByteArray, off: Int, len: Int): Int {
        Preconditions.checkFromIndexSize(off, len, output.size, Preconditions.AIOOBE_FORMATTER)
        synchronized(zsRef) {
            ensureOpen()
            val input = this.input
            val result: Long
            val inputPos: Int
            try {
                if (input == null) {
                    inputPos = this.inputPos
                    try {
                        result = inflateBytesBytes(
                            zsRef.address(),
                            inputArray!!, inputPos, inputLim - inputPos,
                            output, off, len
                        )
                    } catch (e: DataFormatException) {
                        this.inputPos = inputPos + inputConsumed
                        throw e
                    }
                } else {
                    inputPos = input.position()
                    try {
                        val inputRem = maxOf(input.limit() - inputPos, 0)
                        if (input.isDirect) {
                            NIO_ACCESS.acquireSession(input)
                            try {
                                val inputAddress = (input as DirectBuffer).address()
                                result = inflateBufferBytes(
                                    zsRef.address(),
                                    inputAddress + inputPos, inputRem,
                                    output, off, len
                                )
                            } finally {
                                NIO_ACCESS.releaseSession(input)
                            }
                        } else {
                            val inputArray = ZipUtils.getBufferArray(input)
                            val inputOffset = ZipUtils.getBufferOffset(input)
                            result = inflateBytesBytes(
                                zsRef.address(),
                                inputArray, inputOffset + inputPos, inputRem,
                                output, off, len
                            )
                        }
                    } catch (e: DataFormatException) {
                        input.position(inputPos + inputConsumed)
                        throw e
                    }
                }
            } catch (e: DataFormatException) {
                bytesRead += inputConsumed.toLong()
                inputConsumed = 0
                val written = outputConsumed
                bytesWritten += written.toLong()
                outputConsumed = 0
                throw e
            }
            val read = (result and 0x7fff_ffffL).toInt()
            val written = (result ushr 31 and 0x7fff_ffffL).toInt()
            if (result ushr 62 and 1 != 0L) {
                finished = true
            }
            if (written == len && !finished) {
                pendingOutput = true
            } else {
                pendingOutput = false
            }
            if (result ushr 63 and 1 != 0L) {
                needDict = true
            }
            if (input != null) {
                if (read > 0) {
                    input.position(inputPos + read)
                }
            } else {
                this.inputPos = inputPos + read
            }
            bytesWritten += written.toLong()
            bytesRead += read.toLong()
            return written
        }
    }

    /**
     * Uncompresses bytes into specified buffer. Returns actual number
     * of bytes uncompressed. A return value of 0 indicates that
     * needsInput() or needsDictionary() should be called in order to
     * determine if more input data or a preset dictionary is required.
     * In the latter case, getAdler() can be used to get the Adler-32
     * value of the dictionary required.
     * <p>
     * The {@linkplain #getRemaining() remaining byte count} will be reduced by
     * the number of consumed input bytes.  If the {@link #setInput(ByteBuffer)}
     * method was called to provide a buffer for input, the input buffer's position
     * will be advanced the number of consumed bytes.
     * <p>
     * These byte totals, as well as
     * the {@linkplain #getBytesRead() total bytes read}
     * and the {@linkplain #getBytesWritten() total bytes written}
     * values, will be updated even in the event that a {@link DataFormatException}
     * is thrown to reflect the amount of data consumed and produced before the
     * exception occurred.
     *
     * @param output the buffer for the uncompressed data
     * @return the actual number of uncompressed bytes
     * @throws DataFormatException if the compressed data format is invalid
     * @see Inflater#needsInput
     * @see Inflater#needsDictionary
     */
    @Throws(DataFormatException::class)
    fun inflate(output: ByteArray): Int {
        return inflate(output, 0, output.size)
    }

    /**
     * Uncompresses bytes into specified buffer. Returns actual number
     * of bytes uncompressed. A return value of 0 indicates that
     * needsInput() or needsDictionary() should be called in order to
     * determine if more input data or a preset dictionary is required.
     * In the latter case, getAdler() can be used to get the Adler-32
     * value of the dictionary required.
     * <p>
     * On success, the position of the given `output` byte buffer will be
     * advanced by as many bytes as were produced by the operation, which is equal
     * to the number returned by this method.  Note that the position of the
     * `output` buffer will be advanced even in the event that a
     * {@link DataFormatException} is thrown.
     * <p>
     * The {@linkplain #getRemaining() remaining byte count} will be reduced by
     * the number of consumed input bytes.  If the {@link #setInput(ByteBuffer)}
     * method was called to provide a buffer for input, the input buffer's position
     * will be advanced the number of consumed bytes.
     * <p>
     * These byte totals, as well as
     * the {@linkplain #getBytesRead() total bytes read}
     * and the {@linkplain #getBytesWritten() total bytes written}
     * values, will be updated even in the event that a {@link DataFormatException}
     * is thrown to reflect the amount of data consumed and produced before the
     * exception occurred.
     *
     * @param output the buffer for the uncompressed data
     * @return the actual number of uncompressed bytes
     * @throws DataFormatException if the compressed data format is invalid
     * @throws ReadOnlyBufferException if the given output buffer is read-only
     * @see Inflater#needsInput
     * @see Inflater#needsDictionary
     * @since 11
     */
    @Throws(DataFormatException::class, ReadOnlyBufferException::class)
    fun inflate(output: ByteBuffer): Int {
        if (output.isReadOnly) {
            throw ReadOnlyBufferException()
        }
        synchronized(zsRef) {
            ensureOpen()
            val input = this.input
            val result: Long
            val inputPos: Int
            val outputPos = output.position()
            val outputRem = maxOf(output.limit() - outputPos, 0)
            try {
                if (input == null) {
                    inputPos = this.inputPos
                    try {
                        if (output.isDirect) {
                            NIO_ACCESS.acquireSession(output)
                            try {
                                val outputAddress = (output as DirectBuffer).address()
                                result = inflateBytesBuffer(
                                    zsRef.address(),
                                    inputArray!!, inputPos, inputLim - inputPos,
                                    outputAddress + outputPos, outputRem
                                )
                            } finally {
                                NIO_ACCESS.releaseSession(output)
                            }
                        } else {
                            val outputArray = ZipUtils.getBufferArray(output)
                            val outputOffset = ZipUtils.getBufferOffset(output)
                            result = inflateBytesBytes(
                                zsRef.address(),
                                inputArray!!, inputPos, inputLim - inputPos,
                                outputArray, outputOffset + outputPos, outputRem
                            )
                        }
                    } catch (e: DataFormatException) {
                        this.inputPos = inputPos + inputConsumed
                        throw e
                    }
                } else {
                    inputPos = input.position()
                    val inputRem = maxOf(input.limit() - inputPos, 0)
                    try {
                        if (input.isDirect) {
                            NIO_ACCESS.acquireSession(input)
                            try {
                                val inputAddress = (input as DirectBuffer).address()
                                if (output.isDirect) {
                                    NIO_ACCESS.acquireSession(output)
                                    try {
                                        val outputAddress = (output as DirectBuffer).address()
                                        result = inflateBufferBuffer(
                                            zsRef.address(),
                                            inputAddress + inputPos, inputRem,
                                            outputAddress + outputPos, outputRem
                                        )
                                    } finally {
                                        NIO_ACCESS.releaseSession(output)
                                    }
                                } else {
                                    val outputArray = ZipUtils.getBufferArray(output)
                                    val outputOffset = ZipUtils.getBufferOffset(output)
                                    result = inflateBufferBytes(
                                        zsRef.address(),
                                        inputAddress + inputPos, inputRem,
                                        outputArray, outputOffset + outputPos, outputRem
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
                                    result = inflateBytesBuffer(
                                        zsRef.address(),
                                        inputArray, inputOffset + inputPos, inputRem,
                                        outputAddress + outputPos, outputRem
                                    )
                                } finally {
                                    NIO_ACCESS.releaseSession(output)
                                }
                            } else {
                                val outputArray = ZipUtils.getBufferArray(output)
                                val outputOffset = ZipUtils.getBufferOffset(output)
                                result = inflateBytesBytes(
                                    zsRef.address(),
                                    inputArray, inputOffset + inputPos, inputRem,
                                    outputArray, outputOffset + outputPos, outputRem
                                )
                            }
                        }
                    } catch (e: DataFormatException) {
                        input.position(inputPos + inputConsumed)
                        throw e
                    }
                }
            } catch (e: DataFormatException) {
                bytesRead += inputConsumed.toLong()
                inputConsumed = 0
                val written = outputConsumed
                output.position(outputPos + written)
                bytesWritten += written.toLong()
                outputConsumed = 0
                throw e
            }
            val read = (result and 0x7fff_ffffL).toInt()
            val written = (result ushr 31 and 0x7fff_ffffL).toInt()
            if (result ushr 62 and 1 != 0L) {
                finished = true
            }
            if (result ushr 63 and 1 != 0L) {
                needDict = true
            }
            when {
                input != null -> input.position(inputPos + read)
                else -> this.inputPos = inputPos + read
            }
            // Note: this method call also serves to keep the byteBuffer ref alive
            output.position(outputPos + written)
            bytesWritten += written.toLong()
            bytesRead += read.toLong()
            return written
        }
    }

    /**
     * `return the ADLER-32 value of the uncompressed data`
     */
    fun getAdler(): Int {
        synchronized(zsRef) {
            ensureOpen()
            return getAdler(zsRef.address())
        }
    }

    /**
     * Returns the total number of compressed bytes input so far.
     *
     * @implSpec
     * This method returns the equivalent of `(int) getBytesRead()`
     * and therefore cannot return the correct value when it is greater
     * than {@link Integer#MAX_VALUE}.
     *
     * @deprecated Use {@link #getBytesRead()} instead
     *
     * @return the total number of compressed bytes input so far
     */
    @Deprecated("Use {@link #getBytesRead()} instead", ReplaceWith("getBytesRead()"))
    fun getTotalIn(): Int {
        return getBytesRead().toInt()
    }

    /**
     * Returns the total number of compressed bytes input so far.
     *
     * @return the total (non-negative) number of compressed bytes input so far
     * @since 1.5
     */
    fun getBytesRead(): Long {
        synchronized(zsRef) {
            ensureOpen()
            return bytesRead
        }
    }

    /**
     * Returns the total number of uncompressed bytes output so far.
     *
     * @implSpec
     * This method returns the equivalent of `(int) getBytesWritten()`
     * and therefore cannot return the correct value when it is greater
     * than {@link Integer#MAX_VALUE}.
     *
     * @deprecated Use {@link #getBytesWritten()} instead
     *
     * @return the total number of uncompressed bytes output so far
     */
    @Deprecated("Use {@link #getBytesWritten()} instead", ReplaceWith("getBytesWritten()"))
    fun getTotalOut(): Int {
        return getBytesWritten().toInt()
    }

    /**
     * Returns the total number of uncompressed bytes output so far.
     *
     * @return the total (non-negative) number of uncompressed bytes output so far
     * @since 1.5
     */
    fun getBytesWritten(): Long {
        synchronized(zsRef) {
            ensureOpen()
            return bytesWritten
        }
    }

    /**
     * Resets inflater so that a new set of input data can be processed.
     */
    fun reset() {
        synchronized(zsRef) {
            ensureOpen()
            reset(zsRef.address())
            input = ZipUtils.defaultBuf
            inputArray = null
            finished = false
            needDict = false
            bytesRead = 0
            bytesWritten = 0
        }
    }

    /**
     * Closes the decompressor and discards any unprocessed input.
     *
     * This method should be called when the decompressor is no longer
     * being used. Once this method is called, the behavior of the
     * Inflater object is undefined.
     */
    fun end() {
        synchronized(zsRef) {
            zsRef.clean()
            input = ZipUtils.defaultBuf
            inputArray = null
        }
    }

    private fun ensureOpen() {
        assert(Thread.holdsLock(zsRef))
        if (zsRef.address() == 0L)
            throw NullPointerException("Inflater has been closed")
    }

    fun hasPendingOutput(): Boolean {
        return pendingOutput
    }

    private external fun initIDs()
    private external fun init(nowrap: Boolean): Long
    private external fun setDictionary(addr: Long, b: ByteArray, off: Int, len: Int)
    private external fun setDictionaryBuffer(addr: Long, bufAddress: Long, len: Int)

    @Throws(DataFormatException::class)
    private external fun inflateBytesBytes(
        addr: Long,
        inputArray: ByteArray, inputOff: Int, inputLen: Int,
        outputArray: ByteArray, outputOff: Int, outputLen: Int
    ): Long

    @Throws(DataFormatException::class)
    private external fun inflateBytesBuffer(
        addr: Long,
        inputArray: ByteArray, inputOff: Int, inputLen: Int,
        outputAddress: Long, outputLen: Int
    ): Long

    @Throws(DataFormatException::class)
    private external fun inflateBufferBytes(
        addr: Long,
        inputAddress: Long, inputLen: Int,
        outputArray: ByteArray, outputOff: Int, outputLen: Int
    ): Long

    @Throws(DataFormatException::class)
    private external fun inflateBufferBuffer(
        addr: Long,
        inputAddress: Long, inputLen: Int,
        outputAddress: Long, outputLen: Int
    ): Long

    private external fun getAdler(addr: Long): Int
    private external fun reset(addr: Long)
    private external fun end(addr: Long)

    /**
     * A reference to the native zlib's z_stream structure. It also
     * serves as the "cleaner" to clean up the native resource when
     * the Inflater is ended, closed or cleaned.
     */
    class InflaterZStreamRef(owner: Inflater?, addr: Long) : Runnable {

        private var address: Long
        private val cleanable: Cleanable?

        init {
            this.cleanable = owner?.let { CleanerFactory.cleaner().register(it, this) }
            this.address = addr
        }

        fun address(): Long {
            return address
        }

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