import numpy as np
import matplotlib.pyplot as plt
from transformers import AutoTokenizer, AutoModel
import torch
from sklearn.metrics.pairwise import cosine_similarity
def harmonic_wave(params, t, batch_size):
    """
    Generate a harmonic wave based on parameters.
    Handles batch processing for multiple embeddings.
    """
    harmonics = []
    for i in range(batch_size):
        amplitudes, frequencies, phases = np.split(params[i], 3)
        harmonic = (
            amplitudes[:, None] * np.sin(2 * np.pi * frequencies[:, None] * t + phases[:, None])
        )
        harmonics.append(harmonic.sum(axis=0))
    return np.vstack(harmonics)
from transformers import AutoTokenizer, AutoModel

# Load transformer model and tokenizer
model_name = "bert-base-uncased"  # Replace with desired transformer model
tokenizer = AutoTokenizer.from_pretrained(model_name)
model = AutoModel.from_pretrained(model_name)

# Generate embeddings
def generate_embeddings(texts):
    """
    Generate embeddings for a list of texts using a pretrained transformer.
    Returns: numpy array of shape (num_texts, embedding_dim)
    """
    embeddings = []
    for text in texts:
        inputs = tokenizer(text, return_tensors="pt", padding=True, truncation=True)
        outputs = model(**inputs)
        # Use the CLS token embedding as the representation
        cls_embedding = outputs.last_hidden_state[:, 0, :].detach().numpy()
        embeddings.append(cls_embedding)
    return np.vstack(embeddings)

# Example texts
texts = [
    "The quick brown fox jumps over the lazy dog.",
    "AI is transforming the world of technology.",
    "Deep learning enables powerful language models."
]

# Generate embeddings
embeddings = generate_embeddings(texts)
def map_embeddings_to_harmonics(embeddings):
    """
    Initialize harmonic parameters for all embeddings in a batch.
    """
    batch_size, embedding_dim = embeddings.shape
    params = []
    for i in range(batch_size):
        params.append(np.random.rand(3 * embedding_dim))  # Amplitudes, Frequencies, Phases
    return np.vstack(params)

def loss_function(params, t, target_embedding):
    """
    Compute the loss between the target embedding and the generated harmonic wave.
    Uses Mean Squared Error (MSE) as the metric.
    """
    # Generate harmonic wave for the given parameters
    amplitudes, frequencies, phases = np.split(params, 3)
    harmonic = (
        amplitudes[:, None] * np.sin(2 * np.pi * frequencies[:, None] * t + phases[:, None])
    ).sum(axis=0)
    
    # Compute MSE loss
    loss = ((target_embedding - harmonic) ** 2).mean()
    return loss


def compute_gradients(params, t, target_embedding, epsilon=1e-5):
    """
    Compute numerical gradients for the harmonic parameters using finite differences.
    """
    gradients = np.zeros_like(params)
    for i in range(len(params)):
        params_step = params.copy()
        
        # Positive perturbation
        params_step[i] += epsilon
        loss_plus = loss_function(params_step, t, target_embedding)
        
        # Negative perturbation
        params_step[i] -= 2 * epsilon
        loss_minus = loss_function(params_step, t, target_embedding)
        
        # Compute gradient
        gradients[i] = (loss_plus - loss_minus) / (2 * epsilon)
    return gradients


def train_harmonic_embeddings(embeddings, t, batch_size, learning_rate=0.01, epochs=100):
    """
    Train harmonic wave parameters to match transformer embeddings.
    Handles multiple embeddings in batch.
    """
    params = map_embeddings_to_harmonics(embeddings)  # Random initialization
    for epoch in range(epochs):
        total_loss = 0
        for i in range(batch_size):
            # Compute loss
            loss = loss_function(params[i], t, embeddings[i])
            
            # Compute gradients
            gradients = compute_gradients(params[i], t, embeddings[i])
            
            # Update parameters
            params[i] -= learning_rate * gradients
            
            # Accumulate loss
            total_loss += loss
        print(f"Epoch {epoch + 1}/{epochs}, Loss: {total_loss / batch_size}")
    return params


# Visualize embeddings vs harmonic waves
def visualize_embeddings(target, learned):
    """
    Visualize target embeddings and learned harmonic embeddings.
    """
    plt.figure(figsize=(12, 6))

    # Plot target embeddings
    plt.subplot(211)
    plt.imshow(target, aspect="auto", cmap="viridis")
    plt.title("Target Embeddings")
    plt.colorbar()

    # Plot learned harmonic embeddings (reshaped)
    plt.subplot(212)
    plt.imshow(learned, aspect="auto", cmap="viridis")
    plt.title("Learned Harmonic Embeddings")
    plt.colorbar()

    plt.tight_layout()
    plt.show()
    
    # Generate time steps
t = np.linspace(0, 5, embeddings.shape[1])  # Adjust as needed for your embeddings

# Train harmonic embeddings
batch_size = embeddings.shape[0]  # Number of embeddings (batch size)
params = train_harmonic_embeddings(embeddings, t, batch_size)

# Generate learned harmonic waves
learned_harmonic_wave = harmonic_wave(params, t, batch_size)

# Reshape learned harmonic wave to match embeddings
if learned_harmonic_wave.shape == embeddings.shape:
    learned_harmonic_wave = learned_harmonic_wave.reshape(embeddings.shape)
else:
    raise ValueError(
        f"Shape mismatch: learned wave shape {learned_harmonic_wave.shape}, "
        f"expected {embeddings.shape}"
    )

# Visualize the results
visualize_embeddings(embeddings, learned_harmonic_wave)