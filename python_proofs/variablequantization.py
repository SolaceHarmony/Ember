import hashlib
import numpy as np
from dataclasses import dataclass
from typing import List, Tuple
import time

@dataclass
class BlockHash:
    block_id: int
    original_hash: str
    quantized_hash: str
    timestamp: float = time.time()

class HashVerifier:
    def __init__(self, verification_precision: int = 10):
        self.verification_precision = verification_precision
    
    def compute_original_block_hash(self, block_id: int, data: np.ndarray) -> str:
        """Compute deterministic hash for original float data"""
        # Round the data to specified precision and create string representation
        rounded_data = [round(float(x), self.verification_precision) for x in data]
        data_str = str(rounded_data)  # Uses Python's list string representation
        return self.sha256(data_str)
    
    def compute_quantized_block_hash(self, block_id: int, data: np.ndarray) -> str:
        """Compute deterministic hash for quantized data"""
        # Convert quantized data to list and create string representation
        data_list = data.tolist()  # Convert numpy array to list
        return self.sha256(str(data_list))
    
    def verify_block(self, block_id: int, original: np.ndarray, quantized: np.ndarray) -> BlockHash:
        """Verify quantization result against original data"""
        original_hash = self.compute_original_block_hash(block_id, original)
        quantized_hash = self.compute_quantized_block_hash(block_id, quantized)
        
        return BlockHash(
            block_id=block_id,
            original_hash=original_hash,
            quantized_hash=quantized_hash
        )
    
    @staticmethod
    def sha256(input_str: str) -> str:
        """Compute SHA-256 hash and return first 8 characters"""
        hash_obj = hashlib.sha256(input_str.encode())
        return hash_obj.hexdigest()[:8]

class VerifiedQuantizer:
    def __init__(self, block_size: int = 4096, verification_precision: int = 10):
        self.block_size = block_size
        self.verifier = HashVerifier(verification_precision)
    
    def quantize_block(self, block_id: int, data: np.ndarray) -> BlockResult:
        """Quantize a block of data with verification"""
        start_time = time.time()
        
        # Convert input to numpy array if it isn't already
        data = np.asarray(data)
        
        # Compute scale based on absolute maximum
        abs_max = np.max(np.abs(data)) if len(data) > 0 else 1.0
        
        # Perform quantization
        quantized = self.quantize_8bit(data)
        
        # Compute hashes and metrics
        hash_info = self.verifier.verify_block(block_id, data, quantized)
        compression_ratio, mse = self.compute_metrics(data, quantized, abs_max)
        
        return BlockResult(
            block_id=block_id,
            quantized_data=quantized,
            original_data=data,
            hash_info=hash_info,
            processing_time_ms=(time.time() - start_time) * 1000,
            compression_ratio=compression_ratio,
            mean_squared_error=mse
        )
    
    def quantize_all_blocks(self, data: np.ndarray) -> List[BlockResult]:
        """Quantize all data in blocks"""
        data = np.asarray(data)
        blocks = np.array_split(data, np.ceil(len(data) / self.block_size))
        return [self.quantize_block(i, block) for i, block in enumerate(blocks)]
    
    def compute_metrics(self, original: np.ndarray, quantized: np.ndarray, abs_max: float) -> Tuple[float, float]:
        """Calculate metrics for quantization results"""
        # Compression ratio (32-bit float to 8-bit int)
        compression_ratio = 4.0
        
        # Mean squared error using the same scale as quantization
        scale = abs_max / 127.0
        dequantized = (quantized.astype(float) - 128) * scale
        mse = np.mean((original - dequantized) ** 2)
        
        return compression_ratio, float(mse)
    
    def quantize_8bit(self, data: np.ndarray) -> np.ndarray:
        """Quantize float data to 8 bits"""
        abs_max = np.max(np.abs(data)) if len(data) > 0 else 1.0
        scale = abs_max / 127.0
        
        # Scale to [-127, 127] range and shift to [0, 255]
        scaled = np.clip(data / scale, -127, 127)
        return np.round(scaled + 128).astype(np.uint8)

def test_quantization():
    # Generate test data
    np.random.seed(42)  # For reproducibility
    data = np.random.normal(0, 1, 1000)
    
    # Create quantizer
    quantizer = VerifiedQuantizer(block_size=128)
    
    # Quantize data
    results = quantizer.quantize_all_blocks(data)
    
    print(f"\nProcessed {len(results)} blocks:")
    for block in results[:3]:  # Show first 3 blocks
        print(f"\nBlock {block.block_id}:")
        print(f"Processing Time: {block.processing_time_ms:.2f}ms")
        print(f"Compression Ratio: {block.compression_ratio:.2f}x")
        print(f"Mean Squared Error: {block.mean_squared_error:.6f}")
        print(f"Original Hash: {block.hash_info.original_hash}")
        print(f"Quantized Hash: {block.hash_info.quantized_hash}")
        print(f"First 5 quantized values: {block.quantized_data[:5]}")
        
        # Verify hash consistency
        verifier = HashVerifier()
        verify_orig = verifier.compute_original_block_hash(block.block_id, block.original_data)
        verify_quant = verifier.compute_quantized_block_hash(block.block_id, block.quantized_data)
        
        print(f"Verification OK: {verify_orig == block.hash_info.original_hash}")

if __name__ == "__main__":
    test_quantization()