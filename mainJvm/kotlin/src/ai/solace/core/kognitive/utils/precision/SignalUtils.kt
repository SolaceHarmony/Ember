package ai.solace.core.kognitive.utils.precision

import kotlin.math.sin

// Generate a sine wave as test data
fun generateSineWaveData(steps: Int, frequency: Double): List<Double> {
    return List(steps) { step ->
        sin(2 * Math.PI * frequency * step / steps)
    }
}