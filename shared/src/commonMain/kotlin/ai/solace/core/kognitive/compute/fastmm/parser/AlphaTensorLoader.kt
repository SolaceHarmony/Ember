package ai.solace.core.kognitive.compute.fastmm.parser

import kotlin.collections.get
import kotlin.text.get

/**
 * Loads and caches AlphaTensor's matrix factorizations from raw .npz files.
 */
class AlphaTensorLoader {
    // Cache loaded factorizations
    private val factorizationCache = mutableMapOf<Triple<Int, Int, Int>, MatrixFactorization>()
    private var numpyArrays: Map<String, NumpyArray>? = null

    data class MatrixFactorization(
        val m: Int,
        val k: Int,
        val n: Int,
        val uMatrix: NumpyArray,
        val vMatrix: NumpyArray,
        val wMatrix: NumpyArray
    ) {
        val numMultiplications: Int get() = uMatrix.shape[1]

        fun computeMultiplyStep(index: Int): MultiplicationStep {
            // Extract coefficients for this multiplication step
            val uCoeffs = extractStepCoefficients(uMatrix, index)
            val vCoeffs = extractStepCoefficients(vMatrix, index)
            val wCoeffs = extractStepCoefficients(wMatrix, index)

            return MultiplicationStep(uCoeffs, vCoeffs, wCoeffs)
        }

        private fun extractStepCoefficients(
            matrix: NumpyArray,
            stepIndex: Int
        ): List<Coefficient> {
            val coeffs = mutableListOf<Coefficient>()
            for (i in 0 until matrix.shape[0]) {
                val value = matrix.data[i * matrix.shape[1] + stepIndex]
                if (value != 0f) {
                    coeffs.add(Coefficient(i, value))
                }
            }
            return coeffs
        }
    }

    data class MultiplicationStep(
        val uCoefficients: List<Coefficient>,
        val vCoefficients: List<Coefficient>,
        val wCoefficients: List<Coefficient>
    )

    data class Coefficient(val index: Int, val value: Float)

    /**
     * Load factorizations from an AlphaTensor .npz file
     */
    fun loadFactorizations(path: Path) {
        // Read and parse the NPZ file
        FileSystem.SYSTEM.source(path).use { fileSource ->
            val reader = NumpyReader(fileSource.buffer())
            numpyArrays = reader.readArrays()
        }

        // Extract factorizations
        numpyArrays?.forEach { (key, _) ->
            // Keys are in format "(m,k,n)"
            val dims = key.removeSurrounding("(", ")")
                .split(",")
                .map { it.toInt() }

            if (dims.size == 3) {
                val (m, k, n) = dims
                extractFactorization(Triple(m, k, n))
            }
        }
    }

    /**
     * Get a specific matrix factorization
     */
    fun getFactorization(m: Int, k: Int, n: Int): MatrixFactorization? {
        val key = Triple(m, k, n)
        return factorizationCache[key] ?: extractFactorization(key)
    }

    private fun extractFactorization(dims: Triple<Int, Int, Int>): MatrixFactorization? {
        val arrays = numpyArrays ?: return null
        val (m, k, n) = dims
        val key = "($m,$k,$n)"

        val uMatrix = arrays["${key}_U"] ?: return null
        val vMatrix = arrays["${key}_V"] ?: return null
        val wMatrix = arrays["${key}_W"] ?: return null

        val factorization = MatrixFactorization(m, k, n, uMatrix, vMatrix, wMatrix)
        factorizationCache[dims] = factorization
        return factorization
    }
}