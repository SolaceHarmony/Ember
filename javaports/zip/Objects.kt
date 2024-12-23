/*
 * Copyright (c) 2009, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * This class consists of {@code static} utility methods for operating
 * on objects, or checking certain conditions before operation.  These utilities
 * include {@code null}-safe or {@code null}-tolerant methods for computing the
 * hash code of an object, returning a string for an object, comparing two
 * objects, and checking if indexes or sub-range values are out of bounds.
 *
 * @since 1.7
 */
class Objects private constructor() {
    init {
        throw AssertionError("No java.util.Objects instances for you!")
    }

    companion object {

        /**
         * {@return {@code true} if the arguments are equal to each other
         * and {@code false} otherwise}
         * Consequently, if both arguments are {@code null}, {@code true}
         * is returned.  Otherwise, if the first argument is not {@code
         * null}, equality is determined by calling the {@link
         * Object#equals equals} method of the first argument with the
         * second argument of this method. Otherwise, {@code false} is
         * returned.
         *
         * @param a an object
         * @param b an object to be compared with {@code a} for equality
         * @see Object#equals(Object)
         */
        fun equals(a: Any?, b: Any?): Boolean {
            return a === b || (a != null && a == b)
        }

        /**
         * {@return the result of calling {@code toString} for a
         * non-{@code null} argument and {@code "null"} for a
         * {@code null} argument}
         *
         * @param o an object
         * @see Object#toString
         * @see String#valueOf(Object)
         */
        fun toString(o: Any?): String {
            return o.toString()
        }

        /**
         * {@return the result of calling {@code toString} on the first
         * argument if the first argument is not {@code null} and the
         * second argument otherwise}
         *
         * @param o an object
         * @param nullDefault string to return if the first argument is
         *        {@code null}
         * @see Objects#toString(Object)
         */
        fun toString(o: Any?, nullDefault: String): String {
            return o?.toString() ?: nullDefault
        }

        /**
         * Checks that the specified object reference is not {@code null}. This
         * method is designed primarily for doing parameter validation in methods
         * and constructors, as demonstrated below:
         * <blockquote><pre>
         * public Foo(Bar bar) {
         *     this.bar = Objects.requireNonNull(bar);
         * }
         * </pre></blockquote>
         *
         * @param obj the object reference to check for nullity
         * @param <T> the type of the reference
         * @return {@code obj} if not {@code null}
         * @throws NullPointerException if {@code obj} is {@code null}
         */
        fun <T> requireNonNull(obj: T?): T {
            if (obj == null) throw NullPointerException()
            return obj
        }

        /**
         * Checks that the specified object reference is not {@code null} and
         * throws a customized {@link NullPointerException} if it is. This method
         * is designed primarily for doing parameter validation in methods and
         * constructors with multiple parameters, as demonstrated below:
         * <blockquote><pre>
         * public Foo(Bar bar, Baz baz) {
         *     this.bar = Objects.requireNonNull(bar, "bar must not be null");
         *     this.baz = Objects.requireNonNull(baz, "baz must not be null");
         * }
         * </pre></blockquote>
         *
         * @param obj     the object reference to check for nullity
         * @param message detail message to be used in the event that a {@code
         *                NullPointerException} is thrown
         * @param <T> the type of the reference
         * @return {@code obj} if not {@code null}
         * @throws NullPointerException if {@code obj} is {@code null}
         */
        fun <T> requireNonNull(obj: T?, message: String): T {
            if (obj == null) throw NullPointerException(message)
            return obj
        }

        /**
         * Checks that the specified object reference is not `null` and
         * throws a customized [NullPointerException] if it is.
         *
         * Unlike the method `requireNonNull(obj: T?, message: String)`,
         * this method allows creation of the message to be deferred until
         * after the null check is made. While this may confer a
         * performance advantage in the non-null case, when deciding to
         * call this method care should be taken that the costs of
         * creating the message supplier are less than the cost of just
         * creating the string message directly.
         *
         * @param obj the object reference to check for nullity
         * @param messageSupplier lambda providing the detail message to be
         * used in the event that a [NullPointerException] is thrown
         * @param T the type of the reference
         * @return `obj` if not `null`
         * @throws NullPointerException if `obj` is `null`
         */
        fun <T> requireNonNull(obj: T?, messageSupplier: () -> String): T {
            if (obj == null) throw NullPointerException(messageSupplier())
            return obj
        }
        /**
         * Checks if the sub-range from {@code fromIndex} (inclusive) to
         * {@code fromIndex + size} (exclusive) is within the bounds of range from
         * {@code 0} (inclusive) to {@code length} (exclusive).
         *
         * <p>The sub-range is defined to be out of bounds if any of the following
         * inequalities is true:
         * <ul>
         *  <li>{@code fromIndex < 0}</li>
         *  <li>{@code size < 0}</li>
         *  <li>{@code fromIndex + size > length}, taking into account integer overflow</li>
         *  <li>{@code length < 0}, which is implied from the former inequalities</li>
         * </ul>
         *
         * @param fromIndex the lower-bound (inclusive) of the sub-interval
         * @param size the size of the sub-range
         * @param length the upper-bound (exclusive) of the range
         * @return {@code fromIndex} if the sub-range within bounds of the range
         * @throws IndexOutOfBoundsException if the sub-range is out of bounds
         * @since 9
         */
        fun checkFromIndexSize(fromIndex: Int, size: Int, length: Int): Int {
            if (fromIndex < 0 || size < 0 || fromIndex > length - size) {
                throw IndexOutOfBoundsException("Index out of range: fromIndex=$fromIndex, size=$size, length=$length")
            }
            return fromIndex
        }

        /**
         * Checks if the sub-range from {@code fromIndex} (inclusive) to
         * {@code fromIndex + size} (exclusive) is within the bounds of range from
         * {@code 0} (inclusive) to {@code length} (exclusive).
         *
         * <p>The sub-range is defined to be out of bounds if any of the following
         * inequalities is true:
         * <ul>
         *  <li>{@code fromIndex < 0}</li>
         *  <li>{@code size < 0}</li>
         *  <li>{@code fromIndex + size > length}, taking into account integer overflow</li>
         *  <li>{@code length < 0}, which is implied from the former inequalities</li>
         * </ul>
         *
         * @param fromIndex the lower-bound (inclusive) of the sub-interval
         * @param size the size of the sub-range
         * @param length the upper-bound (exclusive) of the range
         * @return {@code fromIndex} if the sub-range within bounds of the range
         * @throws IndexOutOfBoundsException if the sub-range is out of bounds
         * @since 16
         */
        fun checkFromIndexSize(fromIndex: Long, size: Long, length: Long): Long {
            require(fromIndex >= 0) { "fromIndex must be non-negative, but was $fromIndex" }
            require(size >= 0) { "size must be non-negative, but was $size" }
            require(fromIndex + size <= length) { "fromIndex + size must be less than or equal to length, but was ${fromIndex + size}" }
            return fromIndex
        }
    }
}