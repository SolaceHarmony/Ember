package ai.solace.core.kognitive.model.matrix

import kotlinx.coroutines.*
import kotlin.math.sqrt

class MultiHeadAttention(
    private val hiddenSize: Int,
    private val numHeads: Int
) {
    private val headDim = hiddenSize / numHeads

    // Initialize weight matrices
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

        // Linear projections and split into heads
        val q = projectAndReshape(query, wQuery, batchSize, seqLen)
        val k = projectAndReshape(key, wKey, batchSize, seqLen)
        val v = projectAndReshape(value, wValue, batchSize, seqLen)

        // Scaled dot-product attention
        val scaledAttention = computeAttention(q, k, v, mask)

        // Concatenate heads and project back
        val concatAttention = reshapeConcatHeads(scaledAttention, batchSize, seqLen)
        return concatAttention.matmul(wOut)
    }

    private suspend fun projectAndReshape(
        input: CPUMatrix<Float>,
        weights: CPUMatrix<Float>,
        batchSize: Int,
        seqLen: Int
    ): CPUMatrix<Float> {
        val projected = input.matmul(weights)
        val result = FloatArray(batchSize * numHeads * seqLen * headDim)

        for (b in 0 until batchSize) {
            for (h in 0 until numHeads) {
                for (s in 0 until seqLen) {
                    for (d in 0 until headDim) {
                        val fromIdx = b * seqLen * hiddenSize + s * hiddenSize + h * headDim + d
                        val toIdx = b * numHeads * seqLen * headDim + h * seqLen * headDim + s * headDim + d
                        result[toIdx] = projected.data[fromIdx]
                    }
                }
            }
        }

        return CPUMatrix.fromArray(result, batchSize * numHeads, seqLen * headDim)
    }

    private suspend fun computeAttention(
        query: CPUMatrix<Float>,
        key: CPUMatrix<Float>,
        value: CPUMatrix<Float>,
        mask: CPUMatrix<Float>?
    ): CPUMatrix<Float> {
        val scores = query.matmul(key.transpose())
        val scaleFactor = sqrt(headDim.toFloat())
        val scaledScores = FloatArray(scores.data.size) { scores.data[it] / scaleFactor }

        if (mask != null) {
            for (i in scaledScores.indices) {
                scaledScores[i] = if (mask.data[i] == 0.0f) {
                    Float.NEGATIVE_INFINITY
                } else {
                    scaledScores[i]
                }
            }
        }

        val attentionWeights = applySoftmax(
            CPUMatrix.fromArray(scaledScores, scores.rows, scores.cols)
        )

        return attentionWeights.matmul(value)
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

    private fun reshapeConcatHeads(
        attention: CPUMatrix<Float>,
        batchSize: Int,
        seqLen: Int
    ): CPUMatrix<Float> {
        val result = FloatArray(batchSize * seqLen * hiddenSize)

        for (b in 0 until batchSize) {
            for (h in 0 until numHeads) {
                for (s in 0 until seqLen) {
                    for (d in 0 until headDim) {
                        val fromIdx = b * numHeads * seqLen * headDim + h * seqLen * headDim + s * headDim + d
                        val toIdx = b * seqLen * hiddenSize + s * hiddenSize + h * headDim + d
                        result[toIdx] = attention.data[fromIdx]
                    }
                }
            }
        }

        return CPUMatrix.fromArray(result, batchSize * seqLen, hiddenSize)
    }
}