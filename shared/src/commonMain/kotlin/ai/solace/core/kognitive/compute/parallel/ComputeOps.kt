@file:OptIn(DelicateCoroutinesApi::class)

package ai.solace.core.kognitive.compute.parallel

import kotlinx.coroutines.*
import kotlin.math.*
import kotlin.native.concurrent.*

const val BLOCK_SIZE = 32

@ThreadLocal
object ComputeOps {
    private val threadPool = newFixedThreadPoolContext(4, "ComputePool")
    val scope = CoroutineScope(threadPool)

    object Activations {
        fun sigmoid(x: Float): Float = 1.0f / (1.0f + exp(-x))

        fun sigmoidDerivative(x: Float): Float {
            val s = sigmoid(x)
            return s * (1.0f - s)
        }

        fun tanh(x: Float): Float = kotlin.math.tanh(x)

        fun tanhDerivative(x: Float): Float {
            val t = tanh(x)
            return 1.0f - t * t
        }
    }

    suspend fun applyActivation(
        input: FloatArray,
        output: FloatArray,
        operation: (Float) -> Float
    ) {
        require(input.size == output.size) { "Input and output arrays must have same size" }

        withContext(scope.coroutineContext) {
            val chunkSize = (input.size + 3) / 4
            val jobs = List(4) { threadIdx ->
                async {
                    val start = threadIdx * chunkSize
                    val end = minOf(start + chunkSize, input.size)
                    for (i in start until end) {
                        output[i] = operation(input[i])
                    }
                }
            }
            jobs.forEach { it.await() }
        }
    }

    suspend fun blockMatrixMultiply(
        a: FloatArray,
        b: FloatArray,
        c: FloatArray,
        m: Int,
        n: Int,
        k: Int
    ) {
        withContext(scope.coroutineContext) {
            // Divide matrix into blocks
            val jobs = mutableListOf<Job>()
            for (i in 0 until m step BLOCK_SIZE) {
                for (j in 0 until n step BLOCK_SIZE) {
                    val job = launch {
                        multiplyBlock(a, b, c, i, j, k, m, n)
                    }
                    jobs.add(job)
                }
            }
            jobs.joinAll()
        }
    }

    private suspend fun multiplyBlock(
        a: FloatArray,
        b: FloatArray,
        c: FloatArray,
        iStart: Int,
        jStart: Int,
        k: Int,
        m: Int,
        n: Int
    ) {
        val iEnd = minOf(iStart + BLOCK_SIZE, m)
        val jEnd = minOf(jStart + BLOCK_SIZE, n)

        for (i in iStart until iEnd) {
            for (j in jStart until jEnd) {
                var sum = 0.0f
                for (kk in 0 until k) {
                    sum += a[i * k + kk] * b[kk * n + j]
                }
                c[i * n + j] = sum
            }
        }
    }

    suspend fun vectorAdd(
        a: FloatArray,
        b: FloatArray,
        destination: FloatArray
    ) {
        require(a.size == b.size && a.size == destination.size) {
            "Arrays must have the same size"
        }

        withContext(scope.coroutineContext) {
            val chunkSize = (a.size + 3) / 4
            val jobs = List(4) { threadIdx ->
                async {
                    val start = threadIdx * chunkSize
                    val end = minOf(start + chunkSize, a.size)
                    for (i in start until end) {
                        destination[i] = a[i] + b[i]
                    }
                }
            }
            jobs.forEach { it.await() }
        }
    }

    suspend fun hadamard(
        a: FloatArray,
        b: FloatArray,
        destination: FloatArray
    ) {
        require(a.size == b.size && a.size == destination.size) {
            "Arrays must have the same size"
        }

        withContext(scope.coroutineContext) {
            val chunkSize = (a.size + 3) / 4
            val jobs = List(4) { threadIdx ->
                async {
                    val start = threadIdx * chunkSize
                    val end = minOf(start + chunkSize, a.size)
                    for (i in start until end) {
                        destination[i] = a[i] * b[i]
                    }
                }
            }
            jobs.forEach { it.await() }
        }
    }
}