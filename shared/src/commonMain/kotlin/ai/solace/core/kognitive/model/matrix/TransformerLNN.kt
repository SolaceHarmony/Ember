package ai.solace.core.kognitive.model.matrix

import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class TransformerLNN(
    private val inputSize: Int,
    private val hiddenSize: Int
) {
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

        // Generate time information if none provided
        val timeInfo = times ?: CPUMatrix.create(batchSize, seqLen).also { matrix ->
            for (i in 0 until matrix.data.size) {
                matrix.data[i] = (i % seqLen).toFloat()
            }
        }

        // Project input to higher dimension
        var h = x.matmul(inputProj)

        // Transformer path: Pattern Recognition
        val hAtt = attention.forward(h, h, h, mask)
        h = layerNorm(h.add(hAtt), norm1Scale, norm1Bias)

        // LTC path: Time-Aware Processing
        var ltcState = CPUMatrix.create(batchSize, hiddenSize)
        val outputs = mutableListOf<CPUMatrix<Float>>()

        // Process sequence step by step
        for (t in 0 until seqLen) {
            val ltcIn = extractTimeStep(h, t, batchSize, hiddenSize)
            val timeStep = extractTimeStep(timeInfo, t, batchSize, 1)

            val (ltcOut, newState) = ltc.forward(ltcIn, ltcState, timeStep)
            outputs.add(ltcOut)
            ltcState = newState
        }

        // Combine time-aware features
        val ltcOutput = stackOutputs(outputs, batchSize, seqLen, hiddenSize)
        h = layerNorm(ltcOutput.add(h), norm2Scale, norm2Bias)

        // Project back to input size
        return h.matmul(outputProj)
    }
    private suspend fun layerNorm(
        input: CPUMatrix<Float>,
        scale: CPUMatrix<Float>,
        bias: CPUMatrix<Float>,
        eps: Float = 1e-5f
    ): CPUMatrix<Float> {
        val result = FloatArray(input.data.size)

        withContext(MatrixDispatcher.scope.coroutineContext) {
            val jobs = List(input.rows) { row ->
                async {
                    val start = row * input.cols
                    val end = start + input.cols

                    var mean = 0.0f
                    for (i in start until end) {
                        mean += input.data[i]
                    }
                    mean /= input.cols

                    var variance = 0.0f
                    for (i in start until end) {
                        val diff = input.data[i] - mean
                        variance += diff * diff
                    }
                    variance /= input.cols

                    val std = sqrt(variance + eps)
                    for (i in start until end) {
                        val normalized = (input.data[i] - mean) / std
                        result[i] = normalized * scale.data[i % input.cols] + bias.data[i % input.cols]
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
        val result = FloatArray(batchSize * seqLen * hiddenSize)
        for (b in 0 until batchSize) {
            for (s in 0 until seqLen) {
                outputs[s].data.copyInto(
                    destination = result,
                    destinationOffset = b * seqLen * hiddenSize + s * hiddenSize,
                    startIndex = b * hiddenSize,
                    endIndex = (b + 1) * hiddenSize
                )
            }
        }
        return CPUMatrix.fromArray(result, batchSize * seqLen, hiddenSize)
    }
}