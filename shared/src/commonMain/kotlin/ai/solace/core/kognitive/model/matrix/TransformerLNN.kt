package ai.solace.core.kognitive.model.matrix

import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class TransformerLNN(
    private val inputSize: Int,
    private val hiddenSize: Int
) {
    private fun debugDimensions(prefix: String, matrix: CPUMatrix<Float>) {
        println("$prefix - Shape: (${matrix.rows}, ${matrix.cols})")
    }

    // Input projection
    private val inputProj = CPUMatrix.create(inputSize, hiddenSize).also { matrix ->
        for (i in 0 until matrix.data.size) {
            matrix.data[i] = kotlin.random.Random.nextFloat() * 0.02f - 0.01f
        }
    }

    // Core components
    private val attention = MultiHeadAttention(hiddenSize, numHeads = 4)
    private val ltc = LiquidTimeConstant(hiddenSize, hiddenSize)

    // Output projection
    private val outputProj = CPUMatrix.create(hiddenSize, inputSize).also { matrix ->
        for (i in 0 until matrix.data.size) {
            matrix.data[i] = kotlin.random.Random.nextFloat() * 0.02f - 0.01f
        }
    }

    // Layer normalization parameters
    private val norm1Scale = CPUMatrix.create(1, hiddenSize).also { matrix ->
        matrix.data.fill(1.0f)
    }

    private val norm1Bias = CPUMatrix.create(1, hiddenSize).also { matrix ->
        matrix.data.fill(0.0f)
    }

    private val norm2Scale = CPUMatrix.create(1, hiddenSize).also { matrix ->
        matrix.data.fill(1.0f)
    }

    private val norm2Bias = CPUMatrix.create(1, hiddenSize).also { matrix ->
        matrix.data.fill(0.0f)
    }

    suspend fun forward(
        x: CPUMatrix<Float>,
        times: CPUMatrix<Float>? = null,
        mask: CPUMatrix<Float>? = null
    ): CPUMatrix<Float> {
        val batchSize = x.rows
        val seqLen = x.cols / inputSize

        println("\nForward pass dimensions:")
        debugDimensions("Input x", x)

        // Reshape input to (batchSize * seqLen, inputSize)
        val reshapedInput = reshapeToSequence(x, batchSize, seqLen, inputSize)
        debugDimensions("Reshaped input", reshapedInput)

        // Generate time information if none provided
        val timeInfo = times ?: CPUMatrix.create(batchSize, seqLen).also { matrix ->
            for (i in 0 until matrix.data.size) {
                matrix.data[i] = (i % seqLen).toFloat()
            }
        }
        debugDimensions("Time info", timeInfo)

        // Project input to higher dimension
        var h = reshapedInput.matmul(inputProj)  // Now (batchSize * seqLen, hiddenSize)
        debugDimensions("After input projection", h)

        // Reshape back to (batchSize, seqLen * hiddenSize)
        h = reshapeFromSequence(h, batchSize, seqLen, hiddenSize)
        debugDimensions("After reshape", h)

        // Transformer path: Pattern Recognition
        val hAtt = attention.forward(h, h, h, mask)
        debugDimensions("Attention output", hAtt)
        debugDimensions("Before addition h", h)

        // Ensure dimensions match before addition
        require(h.rows == hAtt.rows && h.cols == hAtt.cols) {
            "Dimension mismatch in attention addition: h(${h.rows}, ${h.cols}) + hAtt(${hAtt.rows}, ${hAtt.cols})"
        }

        h = layerNorm(h.add(hAtt), norm1Scale, norm1Bias)
        debugDimensions("After attention and norm", h)

        // LTC path: Time-Aware Processing
        var ltcState = CPUMatrix.create(batchSize, hiddenSize)
        val outputs = mutableListOf<CPUMatrix<Float>>()

        // Process sequence step by step
        for (t in 0 until seqLen) {
            val ltcIn = extractTimeStep(h, t, batchSize, hiddenSize)
            val timeStep = extractTimeStep(timeInfo, t, batchSize, 1)
            debugDimensions("LTC input step $t", ltcIn)
            debugDimensions("Time step $t", timeStep)

            val (ltcOut, newState) = ltc.forward(ltcIn, ltcState, timeStep)
            outputs.add(ltcOut)
            ltcState = newState
            debugDimensions("LTC output step $t", ltcOut)
        }

        // Combine time-aware features
        val ltcOutput = stackOutputs(outputs, batchSize, seqLen, hiddenSize)
        debugDimensions("Stacked LTC output", ltcOutput)
        debugDimensions("Before final addition h", h)

        // Reshape LTC output to match h dimensions
        val reshapedLtcOutput = reshapeFromSequence(ltcOutput, batchSize, seqLen, hiddenSize)
        debugDimensions("Reshaped LTC output", reshapedLtcOutput)

        // Ensure dimensions match before addition
        require(h.rows == reshapedLtcOutput.rows && h.cols == reshapedLtcOutput.cols) {
            "Dimension mismatch in LTC addition: h(${h.rows}, ${h.cols}) + ltcOutput(${reshapedLtcOutput.rows}, ${reshapedLtcOutput.cols})"
        }

        h = layerNorm(reshapedLtcOutput.add(h), norm2Scale, norm2Bias)
        debugDimensions("After LTC and norm", h)

        // Project back to input size
        val reshapedH = reshapeToSequence(h, batchSize, seqLen, hiddenSize)
        debugDimensions("Before final projection", reshapedH)

        val output = reshapedH.matmul(outputProj)
        debugDimensions("After final projection", output)

        val finalOutput = reshapeFromSequence(output, batchSize, seqLen, inputSize)
        debugDimensions("Final output", finalOutput)

        return finalOutput
    }

    private suspend fun layerNorm(
        input: CPUMatrix<Float>,
        scale: CPUMatrix<Float>,
        bias: CPUMatrix<Float>,
        eps: Float = 1e-5f
    ): CPUMatrix<Float> {
        require(scale.cols == input.cols / (input.cols / hiddenSize)) {
            "Scale dimension mismatch: ${scale.cols} vs ${input.cols / (input.cols / hiddenSize)}"
        }

        val result = FloatArray(input.data.size)
        val seqLen = input.cols / hiddenSize

        withContext(MatrixDispatcher.scope.coroutineContext) {
            val jobs = List(input.rows) { row ->
                async {
                    // Process each sequence position independently
                    for (seqPos in 0 until seqLen) {
                        val start = row * input.cols + seqPos * hiddenSize
                        val end = start + hiddenSize

                        // Calculate mean
                        var mean = 0.0f
                        for (i in start until end) {
                            mean += input.data[i]
                        }
                        mean /= hiddenSize

                        // Calculate variance
                        var variance = 0.0f
                        for (i in start until end) {
                            val diff = input.data[i] - mean
                            variance += diff * diff
                        }
                        variance /= hiddenSize

                        // Normalize and scale
                        val std = sqrt(variance + eps)
                        for (i in 0 until hiddenSize) {
                            val inputIdx = start + i
                            val scaleIdx = i % scale.cols
                            result[inputIdx] = ((input.data[inputIdx] - mean) / std) *
                                    scale.data[scaleIdx] +
                                    bias.data[scaleIdx]
                        }
                    }
                }
            }
            jobs.forEach { it.await() }
        }

        return CPUMatrix.fromArray(result, input.rows, input.cols)
    }

    private fun extractTimeStep(
        input: CPUMatrix<Float>,
        step: Int,
        batchSize: Int,
        stepSize: Int
    ): CPUMatrix<Float> {
        val result = FloatArray(batchSize * stepSize)
        for (b in 0 until batchSize) {
            input.data.copyInto(
                destination = result,
                destinationOffset = b * stepSize,
                startIndex = b * input.cols + step * stepSize,
                endIndex = b * input.cols + (step + 1) * stepSize
            )
        }
        return CPUMatrix.fromArray(result, batchSize, stepSize)
    }

    private fun stackOutputs(
        outputs: List<CPUMatrix<Float>>,
        batchSize: Int,
        seqLen: Int,
        hiddenSize: Int
    ): CPUMatrix<Float> {
        // Initialize result array with correct dimensions
        val result = FloatArray(batchSize * seqLen * hiddenSize)

        // Stack the outputs in sequence order
        for (s in 0 until seqLen) {
            val output = outputs[s]
            for (b in 0 until batchSize) {
                // Copy each batch's output into the corresponding sequence position
                output.data.copyInto(
                    destination = result,
                    destinationOffset = b * seqLen * hiddenSize + s * hiddenSize,
                    startIndex = b * hiddenSize,
                    endIndex = (b + 1) * hiddenSize
                )
            }
        }

        // Return as matrix with shape (batchSize * seqLen, hiddenSize)
        return CPUMatrix.fromArray(result, batchSize * seqLen, hiddenSize)
    }

    private fun reshapeToSequence(
        input: CPUMatrix<Float>,
        batchSize: Int,
        seqLen: Int,
        featureSize: Int
    ): CPUMatrix<Float> {
        val result = FloatArray(input.data.size)
        for (b in 0 until batchSize) {
            for (s in 0 until seqLen) {
                for (f in 0 until featureSize) {
                    val fromIdx = b * seqLen * featureSize + s * featureSize + f
                    val toIdx = (b * seqLen + s) * featureSize + f
                    result[toIdx] = input.data[fromIdx]
                }
            }
        }
        return CPUMatrix.fromArray(result, batchSize * seqLen, featureSize)
    }

    private fun reshapeFromSequence(
        input: CPUMatrix<Float>,
        batchSize: Int,
        seqLen: Int,
        featureSize: Int
    ): CPUMatrix<Float> {
        val result = FloatArray(input.data.size)
        for (b in 0 until batchSize) {
            for (s in 0 until seqLen) {
                for (f in 0 until featureSize) {
                    val fromIdx = (b * seqLen + s) * featureSize + f
                    val toIdx = b * seqLen * featureSize + s * featureSize + f
                    result[toIdx] = input.data[fromIdx]
                }
            }
        }
        return CPUMatrix.fromArray(result, batchSize, seqLen * featureSize)
    }
}