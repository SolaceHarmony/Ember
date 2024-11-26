import ray
import numpy as np
import logging
import multiprocessing.shared_memory as shared_memory

# Initialize Ray and logging
ray.init()
logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")

# Define parameters
data_size = 110_000_000
num_workers = 12
block_size = 64  # Smaller block size for reduced quantization error
quantization_bits = 5  # Higher bit depth for reduced error

# Function to simulate quantization with FP32 intermediate handling and threshold
def quantize_nbit(data_block, bits=4):
    range_val = (2 ** (bits - 1)) - 1
    scale = np.max(np.abs(data_block)) / range_val
    logging.info(f"Quantization scale factor for block: {scale}")

    quantized = np.clip(np.round(data_block.astype(np.float32) / scale), -range_val, range_val).astype(np.int8)
    logging.info(f"Quantized values (first 10): {quantized[:10]}")

    rescaled = (quantized * scale).astype(np.float32)
    logging.info(f"Rescaled values (first 10): {rescaled[:10]}")
    return rescaled

@ray.remote
def compute_quantized_square(shared_name, start, end, block_size, quantization_bits):
    # Access shared memory block
    existing_shm = shared_memory.SharedMemory(name=shared_name)
    data_chunk = np.ndarray((end - start,), dtype=np.float32, buffer=existing_shm.buf)[start:end]

    # Process chunk in blocks with FP32 intermediate handling
    results = []
    for i in range(0, len(data_chunk), block_size):
        block = data_chunk[i:i+block_size]
        
        # Skip empty blocks
        if len(block) == 0:
            continue
        
        quantized_block = quantize_nbit(block, bits=quantization_bits)

        # Calculate square in FP32 and log for observability
        squared_block = np.square(quantized_block.astype(np.float32))
        logging.info(f"Squared block values (first 10): {squared_block[:10]}")
        
        results.append(squared_block)
    
    existing_shm.close()  # Close access to shared memory

    # Check if results have data before concatenating
    if results:
        return np.concatenate(results)
    else:
        return np.array([], dtype=np.float32)

# Initialize data
data = np.random.rand(data_size).astype('float32')
logging.info(f"Initialized data (first 10 values): {data[:10]}")

# Set up shared memory
shm = shared_memory.SharedMemory(create=True, size=data.nbytes)
shared_data = np.ndarray(data.shape, dtype=np.float32, buffer=shm.buf)
np.copyto(shared_data, data)  # Copy data to shared memory

# Split data into chunks
chunk_size = data_size // num_workers
tasks = [
    compute_quantized_square.remote(shm.name, i * chunk_size, (i + 1) * chunk_size, block_size, quantization_bits)
    for i in range(num_workers)
]

# Retrieve results from Ray tasks
results = ray.get(tasks)

# Combine results
processed_data = np.concatenate(results)
logging.info(f"Processed data (first 10 values): {processed_data[:10]}")

# Verification
expected_data = np.square(data.astype(np.float32))
max_error = np.max(np.abs(processed_data - expected_data))
logging.info(f"Maximum Error after quantization and squaring with FP32: {max_error}")
print(f"Maximum Error: {max_error}")

# Cleanup
shm.close()
shm.unlink()
