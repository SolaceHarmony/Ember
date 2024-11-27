package ai.solace.core.kognitive.model.matrix

import ai.solace.core.kognitive.compute.parallel.ComputeOps

class LiquidTimeConstant(
    private val inputSize: Int,
    private val hiddenSize: Int
) {
    // Backbone layers
    private val backboneLayer1 = CPUMatrix.create(inputSize + hiddenSize, hiddenSize).also { matrix ->
        for (i in 0 until matrix.data.size) {
            matrix.data[i] = kotlin.random.Random.nextFloat() * 0.02f - 0.01f
        }
    }

    private val backboneLayer2 = CPUMatrix.create(hiddenSize, hiddenSize).also { matrix ->
        for (i in 0 until matrix.data.size) {
            matrix.data[i] = kotlin.random.Random.nextFloat() * 0.02f - 0.01f
        }
    }

    // Specialized networks
    private val timeNet = CPUMatrix.create(hiddenSize, hiddenSize).also { matrix ->
        for (i in 0 until matrix.data.size) {
            matrix.data[i] = kotlin.random.Random.nextFloat() * 0.02f - 0.01f
        }
    }

    private val stateNetG = CPUMatrix.create(hiddenSize, hiddenSize).also { matrix ->
        for (i in 0 until matrix.data.size) {
            matrix.data[i] = kotlin.random.Random.nextFloat() * 0.02f - 0.01f
        }
    }

    private val stateNetH = CPUMatrix.create(hiddenSize, hiddenSize).also { matrix ->
        for (i in 0 until matrix.data.size) {
            matrix.data[i] = kotlin.random.Random.nextFloat() * 0.02f - 0.01f
        }
    }

    // Learnable parameters
    private val tau = CPUMatrix.create(1, hiddenSize).also { matrix ->
        matrix.data.fill(1.0f)
    }

    private val bias = CPUMatrix.create(1, hiddenSize).also { matrix ->
        for (i in 0 until matrix.data.size) {
            matrix.data[i] = kotlin.random.Random.nextFloat() * 0.02f - 0.01f
        }
    }

    suspend fun forward(
        x: CPUMatrix<Float>,
        h: CPUMatrix<Float>,
        t: CPUMatrix<Float>
    ): Pair<CPUMatrix<Float>, CPUMatrix<Float>> {
        // Combine input and hidden state
        val combinedData = FloatArray(x.data.size + h.data.size)

        // Safe array copying
        x.data.copyInto(combinedData, 0, 0, x.data.size)
        h.data.copyInto(combinedData, x.data.size, 0, h.data.size)

        val combinedMatrix = CPUMatrix.fromArray(combinedData, 1, inputSize + hiddenSize)

        // Process through backbone with bias
        val features = combinedMatrix
            .matmul(backboneLayer1)
            .matmul(backboneLayer2)
            .add(bias)

        // Compute temporal factor with time constant
        val temporalFactors = features.matmul(timeNet)
        val ftOutput = FloatArray(temporalFactors.data.size) { idx ->
            ComputeOps.Activations.sigmoid(temporalFactors.data[idx] / tau.data[idx % hiddenSize])
        }

        // Transform states
        val gx = features.matmul(stateNetG)
        val hx = features.matmul(stateNetH)

        // Compute time-dependent gate
        val timeGate = FloatArray(t.data.size) { idx ->
            val scaledTime = t.data[idx] / tau.data[idx % hiddenSize]
            ComputeOps.Activations.sigmoid(ftOutput[idx] * -scaledTime)
        }

        // Blend states
        val blendedState = FloatArray(hiddenSize) { idx ->
            val gated = timeGate[idx] * gx.data[idx] + (1.0f - timeGate[idx]) * hx.data[idx]
            gated + bias.data[idx]
        }

        val hNew = CPUMatrix.fromArray(blendedState, 1, hiddenSize)

        return Pair(hNew, hNew)
    }
}