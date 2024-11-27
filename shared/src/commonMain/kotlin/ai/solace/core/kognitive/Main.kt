package ai.solace.core.kognitive

import ai.solace.core.kognitive.model.matrix.*
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.sin

class App {

    suspend fun startApp() = coroutineScope {
        // Model configuration
        val inputSize = 8    // Start small for testing
        val hiddenSize = 16
        val batchSize = 1    // Single batch for now
        val seqLen = 4       // Short sequence for initial testing

        println("Initializing model with:")
        println("- Input size: $inputSize")
        println("- Hidden size: $hiddenSize")
        println("- Batch size: $batchSize")
        println("- Sequence length: $seqLen")

        // Initialize model
        val model = TransformerLNN(
            inputSize = inputSize,
            hiddenSize = hiddenSize
        )

        // Create a simple test input
        val input = CPUMatrix.create(batchSize, seqLen * inputSize).also { matrix ->
            // Generate a simple sine wave pattern for testing
            for (i in 0 until seqLen) {
                for (j in 0 until inputSize) {
                    val t = (i * inputSize + j).toFloat() / (seqLen * inputSize)
                    matrix.data[i * inputSize + j] = sin(2f * PI.toFloat() * t)
                }
            }
        }

        println("\nProcessing input...")
        val output = model.forward(input)

        // Basic output analysis
        println("\nOutput analysis:")
        println("- Shape: ${output.rows} x ${output.cols}")
        println("- Mean: ${output.data.average()}")
        println("- Min: ${output.data.minOrNull()}")
        println("- Max: ${output.data.maxOrNull()}")

        // Simple validation
        require(output.rows == input.rows) { "Batch size mismatch" }
        require(output.cols == input.cols) { "Sequence length mismatch" }
        println("\nValidation passed: Output dimensions match input dimensions")
    }
}
fun main()
{
    val app = App()
    runBlocking {
        app.startApp()
    }
}