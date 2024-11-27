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
        matrix.data.fill(0.0f)
    }

    suspend fun forward(
        x: CPUMatrix<Float>,
        h: CPUMatrix<Float>,
        t: CPUMatrix<Float>
    ): Pair<CPUMatrix<Float>, CPUMatrix<Float>> {
        require(x.rows == h.rows) { "Batch size mismatch between input and hidden state" }
        require(h.cols == hiddenSize) { "Hidden state size mismatch" }
        require(x.rows == t.rows) { "Batch size mismatch between input and time" }

        val batchSize = x.rows

        // Combine input and hidden state
        val combinedInput = FloatArray(batchSize * (inputSize + hiddenSize))
        for (b in 0 until batchSize) {
            // Copy input
            x.data.copyInto(
                destination = combinedInput,
                destinationOffset = b * (inputSize + hiddenSize),
                startIndex = b * inputSize,
                endIndex = (b + 1) * inputSize
            )
            // Copy hidden state
            h.data.copyInto(
                destination = combinedInput,
                destinationOffset = b * (inputSize + hiddenSize) + inputSize,
                startIndex = b * hiddenSize,
                endIndex = (b + 1) * hiddenSize
            )
        }

        val combinedMatrix = CPUMatrix.fromArray(combinedInput, batchSize, inputSize + hiddenSize)

        // Process through backbone with bias
        val features = combinedMatrix
            .matmul(backboneLayer1)
            .matmul(backboneLayer2)

        // Add bias to features
        val biasedFeatures = FloatArray(features.data.size)
        for (i in 0 until features.data.size) {
            biasedFeatures[i] = features.data[i] + bias.data[i % hiddenSize]
        }
        val biasedMatrix = CPUMatrix.fromArray(biasedFeatures, batchSize, hiddenSize)

        // Compute temporal factor
        val temporalFactors = biasedMatrix.matmul(timeNet)
        val ftOutput = FloatArray(temporalFactors.data.size)
        for (i in 0 until temporalFactors.data.size) {
            ftOutput[i] = ComputeOps.Activations.sigmoid(
                temporalFactors.data[i] / tau.data[i % hiddenSize]
            )
        }

        // Transform states
        val gx = biasedMatrix.matmul(stateNetG)
        val hx = biasedMatrix.matmul(stateNetH)

        // Compute time-dependent gate for each batch element
        val timeGate = FloatArray(batchSize * hiddenSize)
        for (b in 0 until batchSize) {
            for (h in 0 until hiddenSize) {
                val idx = b * hiddenSize + h
                val scaledTime = t.data[b] / tau.data[h]
                timeGate[idx] = ComputeOps.Activations.sigmoid(-ftOutput[idx] * scaledTime)
            }
        }

        // Blend states for each batch element
        val blendedState = FloatArray(batchSize * hiddenSize)
        for (b in 0 until batchSize) {
            for (h in 0 until hiddenSize) {
                val idx = b * hiddenSize + h
                val gate = timeGate[idx]
                blendedState[idx] = gate * gx.data[idx] + (1.0f - gate) * hx.data[idx]
            }
        }

        val hNew = CPUMatrix.fromArray(blendedState, batchSize, hiddenSize)
        return Pair(hNew, hNew)
    }
}