/* Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.solace.core.kognitive.utils.ports.zip

import org.apache.spark.unsafe.memory.MemoryBlock

/**
 * Serves as the root of other byte buffer impl classes, implements common
 * methods that can be shared by child classes.
 */
abstract class BaseByteBuffer(capacity: Int, block: MemoryBlock) : ByteBuffer(capacity, block) {
    override fun asCharBuffer(): CharBuffer {
        return CharToByteBufferAdapter.asCharBuffer(this)
    }

    override fun asDoubleBuffer(): DoubleBuffer {
        return DoubleToByteBufferAdapter.asDoubleBuffer(this)
    }

    override fun asFloatBuffer(): FloatBuffer {
        return FloatToByteBufferAdapter.asFloatBuffer(this)
    }

    override fun asIntBuffer(): IntBuffer {
        return IntToByteBufferAdapter.asIntBuffer(this)
    }

    override fun asLongBuffer(): LongBuffer {
        return LongToByteBufferAdapter.asLongBuffer(this)
    }

    override fun asShortBuffer(): ShortBuffer {
        return ShortToByteBufferAdapter.asShortBuffer(this)
    }

    override fun getChar(): Char {
        return getShort().toChar()
    }

    override fun getChar(index: Int): Char {
        return getShort(index).toChar()
    }

    override fun putChar(value: Char): ByteBuffer {
        return putShort(value.code.toShort())
    }

    override fun putChar(index: Int, value: Char): ByteBuffer {
        return putShort(index, value.code.toShort())
    }
}