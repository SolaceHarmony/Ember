@file:OptIn(DelicateCoroutinesApi::class)

package ai.solace.core.kognitive.model.matrix

import kotlinx.coroutines.*
import kotlin.native.concurrent.*
import kotlin.math.exp

@ThreadLocal
object MatrixDispatcher {
    val scope = CoroutineScope(newFixedThreadPoolContext(4, "MatrixCompute"))
}

class CPUMatrix<T : Number> private constructor(
    val data: FloatArray,  // We'll use FloatArray for now since that's our primary use case
    val rows: Int,
    val cols: Int,
    private val numericOps: NumericOps<T>
) {
    interface NumericOps<T : Number> {
        fun zero(): T
        fun add(a: T, b: T): T
        fun multiply(a: T, b: T): T
        fun fromFloat(f: Float): T
        fun toFloat(t: T): Float
    }

    companion object {
        val floatOps = object : NumericOps<Float> {
            override fun zero() = 0.0f
            override fun add(a: Float, b: Float) = a + b
            override fun multiply(a: Float, b: Float) = a * b
            override fun fromFloat(f: Float) = f
            override fun toFloat(t: Float) = t
        }

        fun create(rows: Int, cols: Int): CPUMatrix<Float> {
            return CPUMatrix(FloatArray(rows * cols) { 0.0f }, rows, cols, floatOps)
        }

        fun fromArray(data: FloatArray, rows: Int, cols: Int): CPUMatrix<Float> {
            require(data.size == rows * cols) {
                "Data size must match dimensions: got \${data.size}, expected \${rows * cols}"
            }
            return CPUMatrix(data, rows, cols, floatOps)
        }
    }

    init {
        require(data.size == rows * cols) {
            "Data size must match dimensions: got \${data.size}, expected \${rows * cols}"
        }
    }

    operator fun get(row: Int, col: Int): Float {
        require(row in 0 until rows && col in 0 until cols) {
            "Index out of bounds: (\$row, \$col) for matrix of size (\$rows, \$cols)"
        }
        return data[row * cols + col]
    }

    operator fun set(row: Int, col: Int, value: Float) {
        require(row in 0 until rows && col in 0 until cols) {
            "Index out of bounds: (\$row, \$col) for matrix of size (\$rows, \$cols)"
        }
        data[row * cols + col] = value
    }

    suspend fun matmul(other: CPUMatrix<T>): CPUMatrix<T> {
        require(cols == other.rows) {
            "Invalid dimensions for matrix multiplication: (\$rows,\$cols) x (\${other.rows},\${other.cols})"
        }

        val result = FloatArray(rows * other.cols) { 0.0f }

        withContext(MatrixDispatcher.scope.coroutineContext) {
            val jobs = List(rows) { row ->
                async {
                    for (col in 0 until other.cols) {
                        var sum = 0.0f
                        for (k in 0 until cols) {
                            sum += data[row * cols + k] * other.data[k * other.cols + col]
                        }
                        result[row * other.cols + col] = sum
                    }
                }
            }
            jobs.forEach { it.await() }
        }

        return CPUMatrix(result, rows, other.cols, numericOps)
    }

    suspend fun add(other: CPUMatrix<T>): CPUMatrix<T> {
        require(rows == other.rows && cols == other.cols) {
            "Matrices must have same dimensions for addition"
        }

        val result = FloatArray(data.size) { 0.0f }

        withContext(MatrixDispatcher.scope.coroutineContext) {
            val chunkSize = (data.size + 3) / 4  // Divide into 4 chunks
            val jobs = List(4) { threadIdx ->
                async {
                    val start = threadIdx * chunkSize
                    val end = minOf(start + chunkSize, data.size)
                    for (i in start until end) {
                        result[i] = data[i] + other.data[i]
                    }
                }
            }
            jobs.forEach { it.await() }
        }

        return CPUMatrix(result, rows, cols, numericOps)
    }

    suspend fun hadamard(other: CPUMatrix<T>): CPUMatrix<T> {
        require(rows == other.rows && cols == other.cols) {
            "Matrices must have same dimensions for Hadamard product"
        }

        val result = FloatArray(data.size) { 0.0f }

        withContext(MatrixDispatcher.scope.coroutineContext) {
            val chunkSize = (data.size + 3) / 4
            val jobs = List(4) { threadIdx ->
                async {
                    val start = threadIdx * chunkSize
                    val end = minOf(start + chunkSize, data.size)
                    for (i in start until end) {
                        result[i] = data[i] * other.data[i]
                    }
                }
            }
            jobs.forEach { it.await() }
        }

        return CPUMatrix(result, rows, cols, numericOps)
    }

    fun transpose(): CPUMatrix<T> {
        val result = FloatArray(data.size) { 0.0f }
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                result[j * rows + i] = data[i * cols + j]
            }
        }
        return CPUMatrix(result, cols, rows, numericOps)
    }

    fun applyElementwise(operation: (Float) -> Float): CPUMatrix<Float> {
        val newData = data.map { operation(it) }.toFloatArray()
        return CPUMatrix(newData, rows, cols, floatOps)
    }
}
