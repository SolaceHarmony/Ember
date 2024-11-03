import multiprocessing
import uuid
import time
import gmpy2
import numpy as np
from hashlib import sha256

def compute_chunk_hash(value, precision=10):
    """Compute a deterministic hash of the value."""
    # Round to specified precision and convert to string to ensure consistent hashing
    rounded_value = format(float(value), f'.{precision}f')
    return sha256(rounded_value.encode()).hexdigest()[:8]

def process_chunk(args):
    i, j, matrix, chunk_size, precision = args
    start_time = time.time()
    
    # Extract the chunk and compute sum
    chunk = matrix[i:i + chunk_size, j:j + chunk_size]
    computation_result = float(np.sum(chunk))
    
    # Convert to gmpy2 for high precision handling
    computation_result = gmpy2.mpfr(computation_result, precision=precision)
    
    # Compute hash of the result
    computation_hash = compute_chunk_hash(computation_result, precision=precision)
    time_taken = time.time() - start_time
    
    return (i, j), computation_hash, float(computation_result), time_taken

def compute_sgemm(matrix, chunk_size=2, precision=100):
    # Define chunks to be processed
    size = matrix.shape[0]
    chunk_args = []
    for i in range(0, size, chunk_size):
        for j in range(0, size, chunk_size):
            chunk_args.append((i, j, matrix, chunk_size, precision))
    
    # Use multiprocessing to process chunks
    with multiprocessing.Pool(processes=multiprocessing.cpu_count()) as pool:
        results = pool.map(process_chunk, chunk_args)
    
    # Process the results
    hash_mismatches = []
    verification_results = {}
    
    for (i, j), computation_hash, computation_result, time_taken in results:
        # Store results for verification
        verification_results[(i, j)] = {
            'hash': computation_hash,
            'result': computation_result,
            'time': time_taken
        }
        
        # Verify the hash
        verification_hash = compute_chunk_hash(computation_result, precision=precision)
        print(f"Chunk at ({i},{j}):")
        print(f"  Result: {computation_result:.{precision}f}")
        print(f"  Original Hash: {computation_hash}")
        print(f"  Verification Hash: {verification_hash}")
        print(f"  Time: {time_taken:.6f} seconds")
        
        if computation_hash != verification_hash:
            hash_mismatches.append((i, j))
    
    if hash_mismatches:
        print("\nHash mismatches found:")
        for i, j in hash_mismatches:
            result = verification_results[(i, j)]
            print(f"Error at chunk ({i},{j}):")
            print(f"  Result: {result['result']}")
            print(f"  Original Hash: {result['hash']}")
            print(f"  Verification Hash: {compute_chunk_hash(result['result'], precision=precision)}")
    else:
        print("\nAll chunks match their computed hash values.")
    
    return verification_results

def numpy_sgemm(matrix_a, matrix_b):
    start_time = time.time()
    result = np.dot(matrix_a, matrix_b)
    time_taken = time.time() - start_time
    print(f"NumPy SGEMM Result:\n{result}")
    print(f"Elapsed Time for NumPy SGEMM: {time_taken:.6f} seconds")
    return result

def main():
    # Set random seed for reproducibility
    np.random.seed(42)
    
    # Set matrix size and chunk size
    matrix_size = 8
    chunk_size = 2
    precision = 10  # Reduced precision for better stability

    # Initialize matrices with random values
    matrix_a = np.random.rand(matrix_size, matrix_size).astype(np.float32)
    matrix_b = np.random.rand(matrix_size, matrix_size).astype(np.float32)

    print("Matrix A:")
    print(matrix_a)
    print("\nMatrix B:")
    print(matrix_b)

    # Perform SGEMM with NumPy for reference
    print("\nPerforming NumPy SGEMM:")
    numpy_result = numpy_sgemm(matrix_a, matrix_b)

    # Process chunks with multiprocessing
    print("\nProcessing chunks with multiprocessing:")
    results = compute_sgemm(numpy_result, chunk_size=chunk_size, precision=precision)

if __name__ == "__main__":
    main()