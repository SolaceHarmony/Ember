# worker.py

import numpy as np
from multiprocessing import shared_memory, Queue
import time
import sys
import psutil
import queue

def worker_process(shm_name: str, start_idx: int, end_idx: int, dtype: str, task_queue: Queue, result_queue: Queue):
    """
    Persistent worker process that listens for and processes computation tasks.

    Args:
        shm_name (str): Name of the shared memory block.
        start_idx (int): Starting index of the data chunk.
        end_idx (int): Ending index of the data chunk.
        dtype (str): Data type of the NumPy array.
        task_queue (Queue): Queue for receiving tasks.
        result_queue (Queue): Queue for sending back results.
    """
    try:
        # Attach to the existing shared memory block
        existing_shm = shared_memory.SharedMemory(name=shm_name)
        print(f"Worker {start_idx}-{end_idx}: Connected to shared memory '{shm_name}'.")

        # Adjust process priority (requires appropriate permissions)
        try:
            p = psutil.Process()
            p.nice(10)  # Increase niceness to lower priority
            print(f"Worker {start_idx}-{end_idx}: Priority set to nice=10.")
        except psutil.AccessDenied:
            print(f"Worker {start_idx}-{end_idx}: Failed to set priority due to Access Denied.")

        # Create a NumPy array view into the shared memory for model parameters
        model_chunk = np.ndarray(
            (end_idx - start_idx,),
            dtype=dtype,
            buffer=existing_shm.buf,
            offset=start_idx * np.dtype(dtype).itemsize
        )

        while True:
            try:
                # Wait for a task from the queue with timeout to handle shutdown signals
                task = task_queue.get(timeout=5)

                if task is None:
                    # None is the signal to shut down
                    print(f"Worker {start_idx}-{end_idx}: Received shutdown signal.")
                    break

                command = task.get('command')
                if command == 'compute':
                    # Start timing
                    start_time = time.perf_counter()

                    # Example Computation: Square each model parameter
                    # Using vectorized operations for efficiency
                    model_chunk[:] = np.square(model_chunk.astype(np.float32)).astype(dtype)

                    # End timing
                    end_time = time.perf_counter()
                    computation_time = (end_time - start_time) * 1000  # Convert to milliseconds

                    # Prepare result
                    result = {
                        'start_idx': start_idx,
                        'end_idx': end_idx,
                        'computation_time_ms': computation_time
                    }

                    # Send back the result through the result_queue
                    result_queue.put(result)
                    print(f"Worker {start_idx}-{end_idx}: Computation completed in {computation_time:.4f} ms.")

                elif command == 'update_data':
                    # Example of updating data: Replace model parameters with new values
                    new_data = task.get('new_data')
                    if new_data is not None and len(new_data) == (end_idx - start_idx):
                        model_chunk[:] = new_data.astype(dtype)
                        print(f"Worker {start_idx}-{end_idx}: Data updated.")
                    else:
                        print(f"Worker {start_idx}-{end_idx}: Invalid data provided for update.")

                else:
                    print(f"Worker {start_idx}-{end_idx}: Unknown command '{command}'.")

            except queue.Empty:
                # No task received within timeout
                continue
            except Exception as e:
                print(f"Worker {start_idx}-{end_idx}: Error processing task - {e}")

    except Exception as e:
        print(f"Worker {start_idx}-{end_idx}: Failed to connect to shared memory '{shm_name}' - {e}")

    finally:
        # Always close the shared memory in the worker process
        existing_shm.close()
        print(f"Worker {start_idx}-{end_idx}: Detached from shared memory '{shm_name}'.")
        sys.exit(0)
