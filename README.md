# Kognitive: Hybrid LNN-Transformer Neural Network in Pure Kotlin Native

A pure Kotlin implementation of a hybrid neural network architecture combining Liquid Neural Networks with Transformers, targeting Apple Silicon with native performance.

## Architecture

The network combines the parallel processing capabilities of Transformers with the continuous-time adaptability of LNNs:

- **Transformer Components**: Self-attention mechanisms for parallel sequence processing
- **Liquid Neural Network**: Continuous-time neural dynamics with adaptive computation
- **Hybrid Integration**: Transformers handle pattern recognition while LNNs manage temporal dynamics

## Features

- Pure Kotlin/Native implementation
- CPU-optimized matrix operations with coroutine-based parallelization
- Zero external dependencies
- Native floating-point compute with SIMD optimization
- Custom print formatter for native platforms

## Usage

```kotlin
val model = TransformerLNN(
    inputSize = 64,
    hiddenSize = 128
)

// Sample input
val input = CPUMatrix.create(batchSize = 1, seqLen * inputSize).also { matrix ->
    for (i in 0 until matrix.data.size) {
        matrix.data[i] = kotlin.random.Random.nextFloat()
    }
}

// Forward pass
val output = model.forward(input)
```

## Components

- `CPUMatrix`: Efficient matrix operations
- `MultiHeadAttention`: Transformer attention mechanism
- `LiquidTimeConstant`: Core LNN dynamics
- `TransformerLNN`: Main hybrid architecture
- `ComputeOps`: Parallelized compute operations

## Building

```bash
./gradlew build
```

## Requirements

- Kotlin 1.9+
- macOS with Apple Silicon
- Xcode Command Line Tools

## License

Apache 2.0

## Citation

```bibtex
@inproceedings{hasani2020liquid,
  title={Liquid neural networks},
  author={Hasani, Ramin and Lechner, Mathias and Amini, Alexander and Rus, Daniela and Grosu, Radu},
  booktitle={AAAI Conference on Artificial Intelligence},
  year={2020}
}
```
