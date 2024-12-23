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

import kotlinx.io.IOException
import kotlin.concurrent.Volatile
/**
 * A [FilterInputStream] wraps some other input stream, which it uses as
 * its basic source of data, possibly transforming the data along the way or
 * providing additional functionality. The class [FilterInputStream]
 * itself simply overrides select methods of [InputStream] with versions
 * that pass all requests to the wrapped input stream. Subclasses of
 * [FilterInputStream] may of course override any methods declared or
 * inherited by [FilterInputStream], and may also provide additional
 * fields and methods.
 *
 * @author  Jonathan Payne
 * @since   1.0
 */
open class FilterInputStream
/**
 * Creates a [FilterInputStream]
 * by assigning the  argument [in]
 * to the field [this. in] so as
 * to remember it for later use.
 *
 * @param   in   the underlying input stream, or `null` if
 *          this instance is to be created without an underlying stream.
 */
constructor(
    /**
     * The input stream to be filtered.
     */
    @Volatile protected var `in`: InputStream?
) : InputStream() {

    /**
     * {@inheritDoc}
     * @implSpec
     * This method simply performs `in.read()` and returns the result.
     *
     * @return     {@inheritDoc}
     * @throws     IOException  {@inheritDoc}
     * @see        java.io.FilterInputStream in
     */
    override fun read(): Int {
        return `in`!!.read()
    }

    /**
     * Reads up to [b.length] bytes of data from this
     * input stream into an array of bytes. This method blocks until some
     * input is available.
     *
     * @implSpec
     * This method simply performs the call
     * `read(b, 0, b.length)` and returns
     * the result. It is important that it does
     * *not* do `in.read(b)` instead;
     * certain subclasses of  [FilterInputStream]
     * depend on the implementation strategy actually
     * used.
     *
     * @param      b   {@inheritDoc}
     * @return     {@inheritDoc}
     * @throws     IOException  if an I/O error occurs.
     * @see        java.io.FilterInputStream.read
     */
    override fun read(b: ByteArray): Int {
        return read(b, 0, b.size)
    }

    /**
     * Reads up to [len] bytes of data from this input stream
     * into an array of bytes. If [len] is not zero, the method
     * blocks until some input is available; otherwise, no
     * bytes are read and `0` is returned.
     *
     * @implSpec
     * This method simply performs `in.read(b, off, len)`
     * and returns the result.
     *
     * @param      b     {@inheritDoc}
     * @param      off   {@inheritDoc}
     * @param      len   {@inheritDoc}
     * @return     {@inheritDoc}
     * @throws     NullPointerException {@inheritDoc}
     * @throws     IndexOutOfBoundsException {@inheritDoc}
     * @throws     IOException  if an I/O error occurs.
     * @see        java.io.FilterInputStream in
     */
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        return `in`!!.read(b, off, len)
    }

    /**
     * Skips over and discards [n] bytes of data from the
     * input stream. The [skip] method may, for a variety of
     * reasons, end up skipping over some smaller number of bytes,
     * possibly `0`. The actual number of bytes skipped is
     * returned.
     *
     * @implSpec
     * This method simply performs `in.skip(n)` and returns the result.
     *
     * @param      n   {@inheritDoc}
     * @return     the actual number of bytes skipped.
     * @throws     IOException  if `in.skip(n)` throws an IOException.
     */
    override fun skip(n: Long): Long {
        return `in`!!.skip(n)
    }

    /**
     * Returns an estimate of the number of bytes that can be read (or
     * skipped over) from this input stream without blocking by the next
     * caller of a method for this input stream. The next caller might be
     * the same thread or another thread.  A single read or skip of this
     * many bytes will not block, but may read or skip fewer bytes.
     *
     * @implSpec
     * This method returns the result of `in.available()`.
     *
     * @return     an estimate of the number of bytes that can be read (or
     *             skipped over) from this input stream without blocking.
     * @throws     IOException  {@inheritDoc}
     */
    override fun available(): Int {
        return `in`!!.available()
    }

    /**
     * {@inheritDoc}
     * @implSpec
     * This method simply performs `in.close()`.
     *
     * @throws     IOException  {@inheritDoc}
     * @see        java.io.FilterInputStream in
     */
    override fun close() {
        `in`!!.close()
    }

    /**
     * Marks the current position in this input stream. A subsequent
     * call to the [reset] method repositions this stream at
     * the last marked position so that subsequent reads re-read the same bytes.
     *
     * The [readlimit] argument tells this input stream to
     * allow that many bytes to be read before the mark position gets
     * invalidated.
     *
     * @implSpec
     * This method simply performs `in.mark(readlimit)`.
     *
     * @param   readlimit   {@inheritDoc}
     * @see     java.io.FilterInputStream in
     * @see     java.io.FilterInputStream.reset
     */
    override fun mark(readlimit: Int) {
        `in`!!.mark(readlimit)
    }

    /**
     * Repositions this stream to the position at the time the
     * [mark] method was last called on this input stream.
     *
     * Stream marks are intended to be used in
     * situations where you need to read ahead a little to see what's in
     * the stream. Often this is most easily done by invoking some
     * general parser. If the stream is of the type handled by the
     * parse, it just chugs along happily. If the stream is not of
     * that type, the parser should toss an exception when it fails.
     * If this happens within readlimit bytes, it allows the outer
     * code to reset the stream and try another parser.
     *
     * @implSpec
     * This method simply performs `in.reset()`.
     *
     * @throws     IOException  {@inheritDoc}
     * @see        java.io.FilterInputStream in
     * @see        java.io.FilterInputStream.mark
     */
    override fun reset() {
        `in`!!.reset()
    }

    /**
     * Tests if this input stream supports the [mark]
     * and [reset] methods.
     *
     * @implSpec
     * This method simply performs `in.markSupported()`.
     *
     * @return  `true` if this stream type supports the
     *          [mark] and [reset] method;
     *          `false` otherwise.
     * @see     java.io.FilterInputStream in
     * @see     java.io.InputStream.mark
     * @see     java.io.InputStream.reset
     */
    override fun markSupported(): Boolean {
        return `in`!!.markSupported()
    }
}