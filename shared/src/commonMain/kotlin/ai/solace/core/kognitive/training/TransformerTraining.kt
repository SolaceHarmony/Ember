package ai.solace.core.kognitive.training

import ai.solace.core.kognitive.model.matrix.*
import kotlinx.coroutines.*
import ai.solace.core.kognitive.utils.Formatter
import ai.solace.core.kognitive.utils.format

class TransformerTrainer(
    private val model: TransformerLNN,
    private val learningRate: Float = 0.001f,
    private val batchSize: Int = 32,
    private val validateEvery: Int = 100
) {
    private val validationMetrics = mutableListOf<Float>()
    private val trainingLosses = mutableListOf<Float>()

    suspend fun train(
        trainData: List<TrainingSequence>,
        validationData: List<TrainingSequence>,
        epochs: Int,
        optimizer: Optimizer
    ) = coroutineScope {
        for (epoch in 0 until epochs) {
            val shuffledData = trainData.shuffled()
            var epochLoss = 0.0f

            // Process batches
            for (batchStart in shuffledData.indices step batchSize) {
                val batchEnd = minOf(batchStart + batchSize, shuffledData.size)
                val batch = shuffledData.subList(batchStart, batchEnd)

                // Convert batch to input tensors
                val (batchInputs, batchTargets) = prepareBatch(batch)

                // Forward pass
                val outputs = model.forward(batchInputs)

                // Calculate loss
                val loss = calculateLoss(outputs, batchTargets)
                epochLoss += loss

                // Backward pass and optimize
                optimizer.step(loss)

                // Validate periodically
                if ((batchStart / batchSize) % validateEvery == 0) {
                    val validationLoss = validate(validationData)
                    validationMetrics.add(validationLoss)
                    printProgress(epoch, batchStart / batchSize, loss, validationLoss)
                }
            }

            trainingLosses.add(epochLoss / (shuffledData.size / batchSize))
        }
    }

    private suspend fun validate(validationData: List<TrainingSequence>): Float {
        var totalLoss = 0.0f

        for (sequence in validationData) {
            val input = sequence.toInputTensor()
            val target = sequence.toTargetTensor()

            val output = model.forward(input)
            totalLoss += calculateLoss(output, target)
        }

        return totalLoss / validationData.size
    }

    private fun calculateLoss(output: CPUMatrix<Float>, target: CPUMatrix<Float>): Float {
        // Mean Squared Error for now
        var loss = 0.0f
        for (i in 0 until output.data.size) {
            val diff = output.data[i] - target.data[i]
            loss += diff * diff
        }
        return loss / output.data.size
    }

    private fun prepareBatch(batch: List<TrainingSequence>): Pair<CPUMatrix<Float>, CPUMatrix<Float>> {
        // Stack inputs and targets into matrices
        val inputSize = batch[0].input.size
        val targetSize = batch[0].target.size

        val inputs = FloatArray(batch.size * inputSize)
        val targets = FloatArray(batch.size * targetSize)

        batch.forEachIndexed { idx, sequence ->
            sequence.input.copyInto(inputs, idx * inputSize)
            sequence.target.copyInto(targets, idx * targetSize)
        }

        return Pair(
            CPUMatrix.fromArray(inputs, batch.size, inputSize),
            CPUMatrix.fromArray(targets, batch.size, targetSize)
        )
    }

    private fun printProgress(epoch: Int, batch: Int, loss: Float, validationLoss: Float) {
        val strFormatted = String.format("%.2f", loss )
        val strFormatted2 = String.format("%.2f", validationLoss )

        println("Epoch $epoch, Batch $batch - Loss: ${strFormatted}, Validation: $strFormatted2")
    }
}

data class TrainingSequence(internal val input: FloatArray, internal val target: FloatArray) {
    fun toInputTensor(): CPUMatrix<Float> = CPUMatrix.fromArray(input, 1, input.size)
    fun toTargetTensor(): CPUMatrix<Float> = CPUMatrix.fromArray(target, 1, target.size)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as TrainingSequence

        if (!input.contentEquals(other.input)) return false
        if (!target.contentEquals(other.target)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = input.contentHashCode()
        result = 31 * result + target.contentHashCode()
        return result
    }
}

interface Optimizer {
    suspend fun step(loss: Float)
}