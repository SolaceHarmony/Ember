package ai.solace.core.kognitive

import ai.solace.core.kognitive.model.matrix.*
import componentace.compression.libs.zlib.ZStream
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.sin

class App {

    suspend fun startApp() = coroutineScope {
        //zipDemo()

        // Model configuration
        val inputSize = 8
        val hiddenSize = 16
        val batchSize = 1
        val seqLen = 4

        println("Initializing model with:")
        println("- Input size: $inputSize")
        println("- Hidden size: $hiddenSize")
        println("- Batch size: $batchSize")
        println("- Sequence length: $seqLen")

        val model = TransformerLNN(
            inputSize = inputSize,
            hiddenSize = hiddenSize
        )

        val input = CPUMatrix.create(batchSize, seqLen * inputSize).also { matrix ->
            for (i in 0 until seqLen) {
                for (j in 0 until inputSize) {
                    val t = (i * inputSize + j).toFloat() / (seqLen * inputSize)
                    matrix.data[i * inputSize + j] = sin(2f * PI.toFloat() * t)
                }
            }
        }

        println("\nProcessing input...")
        val output = model.forward(input)

        println("\nOutput analysis:")
        println("- Shape: ${output.rows} x ${output.cols}")
        println("- Mean: ${output.data.average()}")
        println("- Min: ${output.data.minOrNull()}")
        println("- Max: ${output.data.maxOrNull()}")

        require(output.rows == input.rows) { "Batch size mismatch" }
        require(output.cols == input.cols) { "Sequence length mismatch" }
        println("\nValidation passed: Output dimensions match input dimensions")
    }

    fun zipDemo() {
        val inputData: ByteArray = "Example data to be compressed".encodeToByteArray()
        val compressedDataSize = (inputData.size * 1.1 + 12).toInt() // Proper buffer size for zlib
        val compressedData = ByteArray(compressedDataSize)

        val deflater = ZStream()
        var result = deflater.deflateInit(level = 6)

        var actualCompressedSize = 0
        if (result == deflater.Z_OK) {
            deflater.next_in = inputData
            deflater.avail_in = inputData.size

            deflater.next_out = compressedData
            deflater.avail_out = compressedDataSize

            while (deflater.avail_in > 0) {
                result = deflater.deflate(deflater.Z_FINISH)
                if (result == deflater.Z_STREAM_END) {
                    actualCompressedSize = compressedDataSize - deflater.avail_out
                    break
                } else if (result != deflater.Z_OK) {
                    println("Deflation failed: $result")
                    break
                }
            }

            deflater.deflateEnd()
            println("Compressed data size: $actualCompressedSize")
        } else {
            println("Deflate initialization failed: $result")
            return
        }

        val inflater = ZStream()
        result = inflater.inflateInit()

        if (result == inflater.Z_OK) {
            inflater.next_in = compressedData.copyOfRange(0, actualCompressedSize)
            inflater.avail_in = actualCompressedSize

            val decompressedData = ByteArray(inputData.size)
            inflater.next_out = decompressedData
            inflater.avail_out = decompressedData.size

            while (true) {
                result = inflater.inflate(inflater.Z_NO_FLUSH)
                if (result == inflater.Z_STREAM_END) {
                    val actualDecompressedSize = inputData.size - inflater.avail_out
                    println("Decompressed data size: $actualDecompressedSize")
                    println("Decompressed data: ${decompressedData.decodeToString()}")
                    break
                } else if (result != inflater.Z_OK) {
                    println("Inflation failed: $result")
                    break
                }
            }

            inflater.inflateEnd()
        } else {
            println("Inflate initialization failed: $result")
        }
    }
}

fun main() {
    val app = App()
    runBlocking {
        app.startApp()
    }
}