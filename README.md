# Solace of Harmony: LTC Inspired Neural Network in Pure Kotlin Native

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


## Components


## Building

```bash
./gradlew build
```

## Requirements

- Kotlin 2.1
- macOS with Apple Silicon
- Xcode Command Line Tools

## License

MIT 

## Citation

```bibtex
@inproceedings{hasani2020liquid,
  title={Liquid neural networks},
  author={Hasani, Ramin and Lechner, Mathias and Amini, Alexander and Rus, Daniela and Grosu, Radu},
  booktitle={AAAI Conference on Artificial Intelligence},
  year={2020}
}
```
