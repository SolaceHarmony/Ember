package ai.solace.core.kognitive.model.matrix

import kotlinx.coroutines.*
import kotlin.math.sqrt

class MultiHeadAttention(
    private val hiddenSize: Int,
    private val numHeads: Int
) {
    init {
        require(hiddenSize % numHeads == 0) {
            "Hidden size ($hiddenSize) must be divisible by number of heads ($numHeads)"
        }
    }

    private val headDim = hiddenSize / numHeads

    // Initialize weight matrices for Q, K, V
    private val wQuery = CPUMatrix.create(hiddenSize, hiddenSize).also { matrix ->
        for (i in 0 until matrix.data.size) {
            matrix.data[i] = kotlin.random.Random.nextFloat() * 0.02f - 0.01f
        }
    }

    private val wKey = CPUMatrix.create(hiddenSize, hiddenSize).also { matrix ->
        for (i in 0 until matrix.data.size) {
            matrix.data[i] = kotlin.random.Random.nextFloat() * 0.02f - 0.01f
        }
    }

    private val wValue = CPUMatrix.create(hiddenSize, hiddenSize).also { matrix ->
        for (i in 0 until matrix.data.size) {
            matrix.data[i] = kotlin.random.Random.nextFloat() * 0.02f - 0.01f
        }
    }

    private val wOut = CPUMatrix.create(hiddenSize, hiddenSize).also { matrix ->
        for (i in 0 until matrix.data.size) {
            matrix.data[i] = kotlin.random.Random.nextFloat() * 0.02f - 0.01f
        }
    }

    suspend fun forward(
        query: CPUMatrix<Float>,
        key: CPUMatrix<Float>,
        value: CPUMatrix<Float>,
        mask: CPUMatrix<Float>? = null
    ): CPUMatrix<Float> {
        val batchSize = query.rows
        val seqLen = query.cols / hiddenSize

        println("\nMultiHeadAttention forward:")
        println("Input shapes - Q:(${query.rows}, ${query.cols}), K:(${key.rows}, ${key.cols}), V:(${value.rows}, ${value.cols})")

        // Project and reshape for multi-head attention
        val q = projectAndReshape(query, wQuery, batchSize, seqLen)
        val k = projectAndReshape(key, wKey, batchSize, seqLen)
        val v = projectAndReshape(value, wValue, batchSize, seqLen)

        println("After projection - Q:(${q.rows}, ${q.cols}), K:(${k.rows}, ${k.cols}), V:(${v.rows}, ${v.cols})")

        // Compute attention scores
        val scores = computeAttentionScores(q, k, mask, batchSize, seqLen)
        println("Attention scores shape: (${scores.rows}, ${scores.cols})")

        // Apply attention to values
        val attended = applyAttentionToValues(scores, v, batchSize, seqLen)
        println("Attended values shape: (${attended.rows}, ${attended.cols})")

        // Reshape back and project
        val output = projectOutput(attended, batchSize, seqLen)
        println("Output shape: (${output.rows}, ${output.cols})")

        // Ensure output matches input dimensions
        require(output.rows == query.rows && output.cols == query.cols) {
            "Output dimensions (${output.rows}, ${output.cols}) don't match input dimensions (${query.rows}, ${query.cols})"
        }

        return output
    }

    private suspend fun projectAndReshape(
        input: CPUMatrix<Float>,
        weights: CPUMatrix<Float>,
        batchSize: Int,
        seqLen: Int
    ): CPUMatrix<Float> {
        // First reshape input from (batchSize, seqLen*hiddenSize) to (batchSize*seqLen, hiddenSize)
        val reshapedInput = reshapeToSequence(input, batchSize, seqLen, hiddenSize)
        println("Reshaped input shape: (${reshapedInput.rows}, ${reshapedInput.cols})")

        // Project using weights
        val projected = reshapedInput.matmul(weights)
        println("Projected shape: (${projected.rows}, ${projected.cols})")

        // Reshape for multi-head attention: (batchSize*numHeads, seqLen, headDim)
        val result = FloatArray(batchSize * numHeads * seqLen * headDim)

        for (b in 0 until batchSize) {
            for (h in 0 until numHeads) {
                for (s in 0 until seqLen) {
                    for (d in 0 until headDim) {
                        val srcIdx = b * seqLen * hiddenSize + s * hiddenSize + h * headDim + d
                        val dstIdx = (b * numHeads + h) * seqLen * headDim + s * headDim + d
                        result[dstIdx] = projected.data[srcIdx]
                    }
                }
            }
        }

        return CPUMatrix.fromArray(result, batchSize * numHeads, seqLen * headDim)
    }

    private suspend fun computeAttentionScores(
        query: CPUMatrix<Float>,
        key: CPUMatrix<Float>,
        mask: CPUMatrix<Float>?,
        batchSize: Int,
        seqLen: Int
    ): CPUMatrix<Float> {
        // Compute QK^T
        val keyTransposed = key.transpose()
        println("Key transposed shape: (${keyTransposed.rows}, ${keyTransposed.cols})")

        val scores = query.matmul(keyTransposed)
        println("Raw scores shape: (${scores.rows}, ${scores.cols})")

        // Scale scores
        val scaleFactor = sqrt(headDim.toFloat())
        val scaledScores = FloatArray(scores.data.size) { scores.data[it] / scaleFactor }

        // Apply mask if provided
        if (mask != null) {
            for (i in scaledScores.indices) {
                scaledScores[i] = if (mask.data[i] == 0.0f) {
                    Float.NEGATIVE_INFINITY
                } else {
                    scaledScores[i]
                }
            }
        }

        // Apply softmax
        return applySoftmax(CPUMatrix.fromArray(scaledScores, scores.rows, scores.cols))
    }

    private suspend fun applyAttentionToValues(
        scores: CPUMatrix<Float>,
        values: CPUMatrix<Float>,
        batchSize: Int,
        seqLen: Int
    ): CPUMatrix<Float> {
        val attended = scores.matmul(values)
        println("Attended shape before reshape: (${attended.rows}, ${attended.cols})")
        return attended
    }

    private suspend fun projectOutput(
        attended: CPUMatrix<Float>,
        batchSize: Int,
        seqLen: Int
    ): CPUMatrix<Float> {
        // Reshape from (batchSize*numHeads, seqLen*headDim) to (batchSize*seqLen, hiddenSize)
        val reshaped = reshapeFromMultiHead(attended, batchSize, seqLen)
        println("Reshaped attended shape: (${reshaped.rows}, ${reshaped.cols})")

        // Final projection
        val projected = reshaped.matmul(wOut)
        println("Projected output shape: (${projected.rows}, ${projected.cols})")

        // Reshape back to (batchSize, seqLen*hiddenSize)
        return reshapeFromSequence(projected, batchSize, seqLen, hiddenSize)
    }

    private suspend fun applySoftmax(input: CPUMatrix<Float>): CPUMatrix<Float> {
        val result = FloatArray(input.data.size)

        withContext(MatrixDispatcher.scope.coroutineContext) {
            val jobs = List(input.rows) { row ->
                async {
                    val start = row * input.cols
                    val end = start + input.cols

                    var maxVal = Float.NEGATIVE_INFINITY
                    for (i in start until end) {
                        if (input.data[i] > maxVal) maxVal = input.data[i]
                    }

                    var sum = 0.0f
                    for (i in start until end) {
                        val exp = kotlin.math.exp(input.data[i] - maxVal)
                        result[i] = exp
                        sum += exp
                    }

                    for (i in start until end) {
                        result[i] /= sum
                    }
                }
            }
            jobs.forEach { it.await() }
        }

        return CPUMatrix.fromArray(result, input.rows, input.cols)
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

    private fun reshapeFromMultiHead(
        input: CPUMatrix<Float>,
        batchSize: Int,
        seqLen: Int
    ): CPUMatrix<Float> {
        val result = FloatArray(batchSize * seqLen * hiddenSize)

        for (b in 0 until batchSize) {
            for (h in 0 until numHeads) {
                for (s in 0 until seqLen) {
                    for (d in 0 until headDim) {
                        val srcIdx = (b * numHeads + h) * seqLen * headDim + s * headDim + d
                        val dstIdx = b * seqLen * hiddenSize + s * hiddenSize + h * headDim + d
                        result[dstIdx] = input.data[srcIdx]
                    }
                }
            }
        }

        return CPUMatrix.fromArray(result, batchSize * seqLen, hiddenSize)
    }
}