/*
 * Copyright (c) 1994, 2022, Oracle and/or its affiliates. All rights reserved.
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

import ai.solace.core.kognitive.utils.ports.Closeable
import kotlinx.io.IOException
import kotlin.concurrent.Volatile


/**
 * This abstract class is the superclass of all classes representing
 * an output stream of bytes. An output stream accepts output bytes
 * and sends them to some sink.
 *
 * Applications that need to define a subclass of
 * `OutputStream` must always provide at least a method
 * that writes one byte of output.
 *
 * @author  Arthur van Hoff
 * @see     java.io.BufferedOutputStream
 * @see     java.io.ByteArrayOutputStream
 * @see     java.io.DataOutputStream
 * @see     java.io.FilterOutputStream
 * @see     java.io.InputStream
 * @see     OutputStream#write(int)
 * @since   1.0
 */
abstract class OutputStream : Closeable, Flushable {
    /**
     * Constructor for subclasses to call.
     */
    constructor()

    /**
     * Returns a new `OutputStream` which discards all bytes. The
     * returned stream is initially open. The stream is closed by calling
     * the `close()` method. Subsequent calls to `close()` have
     * no effect.
     *
     *
     * While the stream is open, the `write(int)`, `write(byte[])`, and `write(byte[], int, int)` methods do nothing.
     * After the stream has been closed, these methods all throw `IOException`.
     *
     *
     * The `flush()` method does nothing.
     *
     * @return an `OutputStream` which discards all bytes
     *
     * @since 11
     */
    companion object {
        fun nullOutputStream(): OutputStream {
            return object : OutputStream() {
                @Volatile
                private var closed = false

                private fun ensureOpen() {
                    if (closed) {
                        throw IOException("Stream closed")
                    }
                }

                override fun write(b: Int) {
                    ensureOpen()
                }

                override fun write(b: ByteArray, off: Int, len: Int) {
                    Objects.checkFromIndexSize(off, len, b.size)
                    ensureOpen()
                }

                override fun close() {
                    closed = true
                }
            }
        }
    }

    /**
     * Writes the specified byte to this output stream. The general
     * contract for `write` is that one byte is written
     * to the output stream. The byte to be written is the eight
     * low-order bits of the argument `b`. The 24
     * high-order bits of `b` are ignored.
     *
     * @param      b   the `byte`.
     * @throws     IOException  if an I/O error occurs. In particular,
     * an `IOException` may be thrown if the
     * output stream has been closed.
     */
    abstract fun write(b: Int)

    /**
     * Writes `b.length` bytes from the specified byte array
     * to this output stream. The general contract for `write(b)`
     * is that it should have exactly the same effect as the call
     * `write(b, 0, b.length)`.
     *
     * @param      b   the data.
     * @throws     IOException  if an I/O error occurs.
     * @see    OutputStream.write
     */
    open fun write(b: ByteArray) {
        write(b, 0, b.size)
    }

    /**
     * Writes `len` bytes from the specified byte array
     * starting at offset `off` to this output stream.
     * The general contract for `write(b, off, len)` is that
     * some of the bytes in the array `b` are written to the
     * output stream in order; element `b[off]` is the first
     * byte written and `b[off+len-1]` is the last byte written
     * by this operation.
     *
     *
     * If `b` is `null`, a
     * `NullPointerException` is thrown.
     *
     * If `off` is negative, or `len` is negative, or
     * `off+len` is greater than the length of the array
     * `b`, then an `IndexOutOfBoundsException` is thrown.
     *
     * @implSpec
     * The `write` method of `OutputStream` calls
     * the write method of one argument on each of the bytes to be
     * written out.
     *
     * @apiNote
     * Subclasses are encouraged to override this method and
     * provide a more efficient implementation.
     *
     * @param      b     the data.
     * @param      off   the start offset in the data.
     * @param      len   the number of bytes to write.
     * @throws     IOException  if an I/O error occurs. In particular,
     * an `IOException` is thrown if the output
     * stream is closed.
     * @throws     IndexOutOfBoundsException If `off` is negative,
     * `len` is negative, or `len` is greater than
     * `b.length - off`
     */
    open fun write(b: ByteArray, off: Int, len: Int) {
        Objects.checkFromIndexSize(off, len, b.size)
        // len == 0 condition implicitly handled by loop bounds
        for (i in 0 until len) {
            write(b[off + i].toInt())
        }
    }

    /**
     * Flushes this output stream and forces any buffered output bytes
     * to be written out. The general contract of `flush` is
     * that calling it is an indication that, if any bytes previously
     * written have been buffered by the implementation of the output
     * stream, such bytes should immediately be written to their
     * intended destination.
     *
     * If the intended destination of this stream is an abstraction provided by
     * the underlying operating system, for example a file, then flushing the
     * stream guarantees only that bytes previously written to the stream are
     * passed to the operating system for writing; it does not guarantee that
     * they are actually written to a physical device such as a disk drive.
     *
     * @implSpec
     * The `flush` method of `OutputStream` does nothing.
     *
     * @throws     IOException  if an I/O error occurs.
     */
    override fun flush() {
    }

    /**
     * Closes this output stream and releases any system resources
     * associated with this stream. The general contract of `close`
     * is that it closes the output stream. A closed stream cannot perform
     * output operations and cannot be reopened.
     *
     * @implSpec
     * The `close` method of `OutputStream` does nothing.
     *
     * @throws     IOException  if an I/O error occurs.
     */
    override fun close() {
    }
}