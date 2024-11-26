# main.py

import numpy as np
from multiprocessing import Process, shared_memory, Queue
import worker
import time
import os

def initialize_shared_memory(model_params: np.ndarray, input_data: np.ndarray) -> shared_memory.SharedMemory:
    """
    Initialize shared memory and copy data into it.

    Args:
        model_params (np.ndarray): NumPy array of model parameters.
        input_data (np.ndarray): NumPy array of input data.

    Returns:
        shared_memory.SharedMemory: The created shared memory block.
    """
    total_size = model_params.nbytes + input_data.nbytes
    shm = shared_memory.SharedMemory(create=True, size=total_size)
    # Copy model parameters
    shm_model = np.ndarray(model_params.shape, dtype=model_params.dtype, buffer=shm.buf, offset=0)
    shm_model[:] = model_params[:]
    # Copy input data
    shm_input = np.ndarray(input_data.shape, dtype=input_data.dtype, buffer=shm.buf, offset=model_params.nbytes)
    shm_input[:] = input_data[:]
    return shm

def verify_results(original_model_params: np.ndarray, original_input_data: np.ndarray, shared_memory_block: shared_memory.SharedMemory) -> float:
    """
    Verify the correctness of computations by comparing with expected results.

    Args:
        original_model_params (np.ndarray): Original model parameters before computation.
        original_input_data (np.ndarray): Original input data before computation.
        shared_memory_block (shared_memory.SharedMemory): Shared memory containing updated data.

    Returns:
        float: Maximum absolute error.
    """
    # Reconstruct the shared arrays
    shm_model = np.ndarray(original_model_params.shape, dtype=original_model_params.dtype, buffer=shared_memory_block.buf, offset=0)
    shm_input = np.ndarray(original_input_data.shape, dtype=original_input_data.dtype, buffer=shared_memory_block.buf, offset=original_model_params.nbytes)
    
    # Example verification: Ensure model parameters have been updated correctly
    # This depends on what 'compute' tasks do. For illustration, let's assume 'compute' squares the parameters
    expected_model = original_model_params.astype(np.float32) ** 2
    actual_model = shm_model.astype(np.float32)
    max_error = np.max(np.abs(actual_model - expected_model))
    
    return max_error

def main():
    # Configuration Parameters
    data_size = 110_000_000  # Simulating BERT-Base model parameters
    dtype = 'float32'         # Using float32 for model parameters
    num_workers = os.cpu_count() or 4  # Number of worker processes

    print(f"System has {num_workers} CPU cores. Utilizing all available cores.")

    # Initialize model parameters with random values
    model_params = np.random.rand(data_size).astype(dtype)

    # Initialize input data
    batch_size = 32
    sequence_length = 128
    vocab_size = 30522  # Example vocab size for BERT
    input_data = np.random.randint(0, vocab_size, size=(batch_size, sequence_length)).astype('int32')  # Token IDs

    # Initialize shared memory and copy data into it
    shm = initialize_shared_memory(model_params, input_data)
    print(f"Shared memory '{shm.name}' created with size {shm.size} bytes.")

    # Calculate chunk sizes for workers based on model parameters
    chunk_size = data_size // num_workers
    indices = []
    task_queues = []
    result_queues = []
    workers = []

    for i in range(num_workers):
        start = i * chunk_size
        end = (i + 1) * chunk_size if i != num_workers - 1 else data_size
        indices.append((start, end))
        task_queue = Queue()
        result_queue = Queue()
        task_queues.append(task_queue)
        result_queues.append(result_queue)

    try:
        # Spawn worker processes
        for i in range(num_workers):
            start, end = indices[i]
            p = Process(target=worker.worker_process, args=(
                shm.name,
                start,
                end,
                dtype,
                task_queues[i],
                result_queues[i]
            ))
            p.start()
            workers.append(p)
            print(f"Spawned Worker {start}-{end} with PID {p.pid}.")

        # Allow some time for workers to initialize
        time.sleep(1)

        # Define a series of tasks to send to workers
        tasks = [
            {'command': 'compute'},
            {'command': 'compute'},
            {'command': 'update_data'},  # 'new_data' will be added per worker
            {'command': 'compute'},
            {'command': 'compute'},
        ]

        for idx, task in enumerate(tasks):
            print(f"\nDispatching Task {idx + 1}: {task['command']}")
            for i in range(num_workers):
                if task['command'] == 'update_data':
                    # Generate new data for update based on worker's chunk size
                    start, end = indices[i]
                    new_chunk_size = end - start
                    new_data = np.random.rand(new_chunk_size).astype(dtype)
                    # Ensure the new data matches the chunk size
                    if len(new_data) != new_chunk_size:
                        print(f"Error: New data size {len(new_data)} does not match worker {start}-{end} chunk size {new_chunk_size}.")
                        continue
                    task_to_send = {
                        'command': 'update_data',
                        'new_data': new_data
                    }
                else:
                    # For 'compute' command
                    task_to_send = {
                        'command': 'compute'
                    }
                task_queues[i].put(task_to_send)
                print(f"Sent {task['command']} task to Worker {indices[i][0]}-{indices[i][1]}.")

            # Collect results for compute tasks
            if task['command'] == 'compute':
                for i in range(num_workers):
                    result = result_queues[i].get()
                    print(f"Received result from Worker {result['start_idx']}-{result['end_idx']}: "
                          f"Computation Time = {result['computation_time_ms']:.4f} ms.")

            time.sleep(2)  # Simulate time between tasks

        # Verification after all tasks
        max_error = verify_results(model_params, input_data, shm)
        print(f"\nMaximum Error after All Computations: {max_error:.10f}")

    finally:
        # Shutdown workers gracefully
        for i in range(num_workers):
            task_queues[i].put(None)  # Send shutdown signal
            print(f"Sent shutdown signal to Worker {indices[i][0]}-{indices[i][1]}.")

        # Wait for all workers to terminate
        for p in workers:
            p.join()
            print(f"Worker with PID {p.pid} has terminated.")

        # Clean up shared memory
        shm.close()
        shm.unlink()
        print(f"Shared memory '{shm.name}' closed and unlinked.")

if __name__ == "__main__":
    main()
