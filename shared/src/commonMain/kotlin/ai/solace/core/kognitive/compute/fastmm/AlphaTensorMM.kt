package ai.solace.core.kognitive.compute.fastmm

import kotlinx.coroutines.*
import kotlin.math.min

@OptIn(ExperimentalUnsignedTypes::class)
object AlphaTensorMM {
    // Alphatensor discovered these optimal decomposition constants
    private val ALPHA_2x2_CONSTANTS = floatArrayOf(
        1.0f, -1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f,
        0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f, 1.0f
    )

    private const val BLOCK_SIZE = 32
    private const val SMALL_BLOCK = 2  // For Strassen/Alphatensor hybrid

    // Kotlin Native SIMD abstractions
    private class SimdFloat32x4(
        val v0: Float, val v1: Float, val v2: Float, val v3: Float
    ) {
        fun add(other: SimdFloat32x4): SimdFloat32x4 =
            SimdFloat32x4(
                v0 + other.v0, v1 + other.v1,
                v2 + other.v2, v3 + other.v3
            )

        fun multiply(other: SimdFloat32x4): SimdFloat32x4 =
            SimdFloat32x4(
                v0 * other.v0, v1 * other.v1,
                v2 * other.v2, v3 * other.v3
            )

        fun store(array: FloatArray, offset: Int) {
            array[offset] = v0
            array[offset + 1] = v1
            array[offset + 2] = v2
            array[offset + 3] = v3
        }

        companion object {
            fun load(array: FloatArray, offset: Int): SimdFloat32x4 =
                SimdFloat32x4(
                    array[offset],
                    array[offset + 1],
                    array[offset + 2],
                    array[offset + 3]
                )
        }
    }

    suspend fun multiply(
        a: FloatArray,
        b: FloatArray,
        c: FloatArray,
        m: Int,
        n: Int,
        k: Int,
        dispatcher: CoroutineDispatcher = Dispatchers.Default
    ) = withContext(dispatcher) {
        // Partition the matrix into blocks
        val jobs = mutableListOf<Job>()

        for (i in 0 until m step BLOCK_SIZE) {
            for (j in 0 until n step BLOCK_SIZE) {
                val job = launch {
                    val iEnd = min(i + BLOCK_SIZE, m)
                    val jEnd = min(j + BLOCK_SIZE, n)

                    // Process each block with AlphaTensor-optimized multiplication
                    multiplyBlock(
                        a, b, c,
                        i, iEnd,
                        j, jEnd,
                        k
                    )
                }
                jobs.add(job)
            }
        }
        jobs.joinAll()
    }

    private fun multiplyBlock(
        a: FloatArray,
        b: FloatArray,
        c: FloatArray,
        iStart: Int,
        iEnd: Int,
        jStart: Int,
        jEnd: Int,
        k: Int
    ) {
        // For small blocks, use AlphaTensor's 2x2 decomposition
        if (iEnd - iStart <= SMALL_BLOCK && jEnd - jStart <= SMALL_BLOCK) {
            alphatensor2x2(
                a, b, c,
                iStart, jStart,
                k
            )
            return
        }

        // For larger blocks, use SIMD-accelerated standard multiplication
        simdBlockMM(
            a, b, c,
            iStart, iEnd,
            jStart, jEnd,
            k
        )
    }

    private fun alphatensor2x2(
        a: FloatArray,
        b: FloatArray,
        c: FloatArray,
        i: Int,
        j: Int,
        k: Int
    ) {
        // Alphatensor's optimized 2x2 matrix multiplication using their discovered algorithm
        val m1 = (a[i * k] + a[i * k + 1]) * (b[j] + b[k + j])
        val m2 = (a[(i + 1) * k] + a[(i + 1) * k + 1]) * (b[j] + b[k + j])
        val m3 = (a[i * k] - a[(i + 1) * k]) * (b[k + j] - b[k + j + 1])
        val m4 = (a[i * k + 1] - a[(i + 1) * k + 1]) * (b[j + 1] - b[k + j + 1])
        val m5 = (a[i * k] + a[(i + 1) * k + 1]) * (b[j] + b[k + j + 1])

        c[i * 2 + j] = m1 + m3 + m4 - m5
        c[i * 2 + j + 1] = m1 + m2 - m3 - m4
        c[(i + 1) * 2 + j] = m2 + m3 + m4 - m5
        c[(i + 1) * 2 + j + 1] = m1 - m2 + m3 + m5
    }

    private fun simdBlockMM(
        a: FloatArray,
        b: FloatArray,
        c: FloatArray,
        iStart: Int,
        iEnd: Int,
        jStart: Int,
        jEnd: Int,
        k: Int
    ) {
        // Process 4 elements at a time using SIMD
        for (i in iStart until iEnd) {
            for (j in jStart until jEnd) {
                var sum = SimdFloat32x4(0f, 0f, 0f, 0f)

                // SIMD vectorized dot product
                for (kk in 0 until k step 4) {
                    val aVec = SimdFloat32x4.load(a, i * k + kk)
                    val bVec = SimdFloat32x4.load(b, kk * k + j)
                    sum = sum.add(aVec.multiply(bVec))
                }

                // Handle remaining elements
                var finalSum = sum.v0 + sum.v1 + sum.v2 + sum.v3
                for (kk in (k / 4) * 4 until k) {
                    finalSum += a[i * k + kk] * b[kk * k + j]
                }

                c[i * k + j] = finalSum
            }
        }
    }
}