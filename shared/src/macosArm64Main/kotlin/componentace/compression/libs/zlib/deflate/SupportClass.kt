package componentace.compression.libs.zlib.deflate

import kotlinx.io.*
import kotlinx.cinterop.*
import platform.CoreServices.BigEndianUnsignedLong

public final class BigEndianUnsignedLong : CStructVar
class SupportClass {

    companion object {
        fun identity(literal: Long) = literal
        fun identity(literal: BigEndianUnsignedLong) = literal
        fun identity(literal: Float) = literal
        fun identity(literal: Double) = literal

        fun urShift(number: Int, bits: Int): Int {
            return if (number >= 0) number shr bits else (number shr bits) + (2 shl bits.inv())
        }

        fun urShift(number: Long, bits: Int): Long {
            return if (number >= 0) number shr bits else (number shr bits) + (2L shl bits.inv())
        }

        fun readInputFile(filePath: String, bufferSize: Int): ByteArray {
            val file = fopen(filePath, "r") ?: throw IllegalArgumentException("Cannot open file: $filePath")
            try {
                val buffer = ByteArray(bufferSize)
                fread(buffer.refTo(0), 1.convert(), buffer.size.convert(), file).toInt()
                return buffer
            } finally {
                fclose(file)
            }
        }

        fun toByteArray(sourceString: String): ByteArray {
            return sourceString.encodeToByteArray()
        }

        fun toCharArray(byteArray: ByteArray): CharArray {
            return byteArray.toString(Charset.forName("UTF-8")).toCharArray()
        }
    }
}