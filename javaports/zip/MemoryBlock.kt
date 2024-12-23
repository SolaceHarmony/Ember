/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.unsafe.memory

import ai.solace.core.kognitive.utils.ports.zip.MemoryLocation

/**
 * A consecutive block of memory, starting at a {@link MemoryLocation} with a fixed size.
 */
open class MemoryBlock(
    obj: Any?,
    offset: Long,
    private val length: Long
) : MemoryLocation(obj, offset) {

    /** Special `pageNumber` value for pages which were not allocated by TaskMemoryManagers */
    var pageNumber = NO_PAGE_NUMBER

    /**
     * Returns the size of the memory block.
     */
    fun size(): Long {
        return length
    }

    /**
     * Fills the memory block with the specified byte value.
     */
    fun fill(value: Byte) {
        Platform.setMemory(obj, offset, length, value)
    }

    companion object {
        /** Special `pageNumber` value for pages which were not allocated by TaskMemoryManagers */
        const val NO_PAGE_NUMBER = -1

        /**
         * Special `pageNumber` value for marking pages that have been freed in the TaskMemoryManager.
         * We set `pageNumber` to this value in TaskMemoryManager.freePage() so that MemoryAllocator
         * can detect if pages which were allocated by TaskMemoryManager have been freed in the TMM
         * before being passed to MemoryAllocator.free() (it is an error to allocate a page in
         * TaskMemoryManager and then directly free it in a MemoryAllocator without going through
         * the TMM freePage() call).
         */
        const val FREED_IN_TMM_PAGE_NUMBER = -2

        /**
         * Special `pageNumber` value for pages that have been freed by the MemoryAllocator. This allows
         * us to detect double-frees.
         */
        const val FREED_IN_ALLOCATOR_PAGE_NUMBER = -3

        /**
         * Creates a memory block pointing to the memory used by the long array.
         */
        fun fromLongArray(array: LongArray): MemoryBlock {
            return MemoryBlock(array, Platform.LONG_ARRAY_OFFSET, array.size * 8L)
        }
    }
}