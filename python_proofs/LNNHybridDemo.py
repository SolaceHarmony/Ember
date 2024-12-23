import torch
import torch.nn as nn
import numpy as np
from typing import Optional, Tuple

class LiquidTimeConstant(nn.Module):
    """
    Implements the Liquid Time-Constant cell following Hasani et al. 2022
    """
    def __init__(self, input_size: int, hidden_size: int):
        super().__init__()
        self.input_size = input_size
        self.hidden_size = hidden_size
        
        # Backbone network
        self.backbone = nn.Sequential(
            nn.Linear(input_size + hidden_size, hidden_size),
            nn.Tanh(),
            nn.Linear(hidden_size, hidden_size)
        )
        
        # Networks for liquid time-constant mechanism
        self.time_net = nn.Linear(hidden_size, hidden_size)
        self.state_net_g = nn.Linear(hidden_size, hidden_size)
        self.state_net_h = nn.Linear(hidden_size, hidden_size)
        
        # Learnable parameters
        self.tau = nn.Parameter(torch.ones(hidden_size))
        self.A = nn.Parameter(torch.randn(hidden_size))

    def forward(self, x: torch.Tensor, h: torch.Tensor, t: torch.Tensor) -> Tuple[torch.Tensor, torch.Tensor]:
        """
        Forward pass implementing the closed-form solution
        """
        combined = torch.cat([x, h], dim=-1)
        features = self.backbone(combined)
        
        # Compute liquid time-constant factor
        f_t = torch.sigmoid(self.time_net(features))
        
        # State transformations
        g_x = self.state_net_g(features)
        h_x = self.state_net_h(features)
        
        # Time-dependent gating
        gate = torch.sigmoid(-f_t * t.view(-1, 1))
        
        # Closed-form update
        h_new = gate * g_x + (1 - gate) * h_x
        
        return h_new, h_new

class TransformerLNN(nn.Module):
    """
    Hybrid architecture combining Transformer attention with LTC dynamics
    """
    def __init__(self, 
                 input_size: int,
                 hidden_size: int,
                 num_heads: int = 4,
                 dropout: float = 0.1):
        super().__init__()
        
        self.hidden_size = hidden_size
        self.num_heads = num_heads
        
        # Layers
        self.input_proj = nn.Linear(input_size, hidden_size)
        self.attention = nn.MultiheadAttention(hidden_size, num_heads, dropout=dropout)
        self.ltc = LiquidTimeConstant(hidden_size, hidden_size)
        self.output_proj = nn.Linear(hidden_size, input_size)
        
        # Layer normalization
        self.norm1 = nn.LayerNorm(hidden_size)
        self.norm2 = nn.LayerNorm(hidden_size)

    def forward(self, 
                x: torch.Tensor,
                times: Optional[torch.Tensor] = None,
                mask: Optional[torch.Tensor] = None) -> torch.Tensor:
        """
        Forward pass combining attention and LTC dynamics
        """
        batch_size, seq_len, _ = x.shape
        
        # Generate default timing if none provided
        if times is None:
            times = torch.arange(seq_len, dtype=torch.float32, device=x.device)
            times = times.unsqueeze(0).expand(batch_size, -1)
        
        # Project input
        h = self.input_proj(x)
        
        # Self-attention with residual connection and normalization
        h_att = h.transpose(0, 1)  # [seq_len, batch, hidden]
        h_att, _ = self.attention(h_att, h_att, h_att, attn_mask=mask)
        h_att = h_att.transpose(0, 1)  # [batch, seq, hidden]
        h_att = self.norm1(h + h_att)
        
        # Process with LTC
        ltc_state = torch.zeros(batch_size, self.hidden_size, device=x.device)
        outputs = []
        
        for t in range(seq_len):
            ltc_in = h_att[:, t]
            ltc_out, ltc_state = self.ltc(ltc_in, ltc_state, times[:, t])
            outputs.append(ltc_out)
        
        # Combine outputs
        outputs = torch.stack(outputs, dim=1)
        outputs = self.norm2(outputs + h_att)
        
        # Project to output space
        y = self.output_proj(outputs)
        
        return y

    def get_attention_weights(self, x: torch.Tensor) -> torch.Tensor:
        """Helper method to extract attention weights for visualization"""
        h = self.input_proj(x)
        h = h.transpose(0, 1)
        _, attn_weights = self.attention(h, h, h, need_weights=True)
        return attn_weights
    
    import torch
import torch.nn as nn
import torch.optim as optim
import numpy as np
import matplotlib.pyplot as plt
from IPython.display import clear_output
import seaborn as sns
from typing import List, Dict, Optional
from datetime import datetime

class TrainingVisualizer:
    def __init__(self, save_intervals: int = 10, plot_intervals: int = 5):
        self.save_intervals = save_intervals
        self.plot_intervals = plot_intervals
        
        # History tracking
        self.losses: List[float] = []
        self.accuracies: List[float] = []
        self.ltc_states: List[torch.Tensor] = []
        self.attn_weights: List[torch.Tensor] = []
        self.timestamps: List[str] = []
        
    def update_metrics(self, loss: float, accuracy: float, 
                      ltc_state: torch.Tensor, attn_weights: torch.Tensor):
        """Update training metrics"""
        self.losses.append(loss)
        self.accuracies.append(accuracy)
        self.ltc_states.append(ltc_state.detach().cpu())
        self.attn_weights.append(attn_weights.detach().cpu())
        self.timestamps.append(datetime.now().strftime("%H:%M:%S"))
        
    def plot_training_progress(self, epoch: int, batch_idx: int, x: torch.Tensor, y: torch.Tensor, y_pred: torch.Tensor):
        """Plot comprehensive training visualizations"""
        # Convert tensors to 2D by taking first channel/feature
        x_plot = x[:, :, 0].cpu().detach().numpy()  # Take first feature dimension
        y_plot = y[:, :, 0].cpu().detach().numpy()
        y_pred_plot = y_pred[:, :, 0].cpu().detach().numpy()
        
        clear_output(wait=True)
        plt.figure(figsize=(20, 12))
        
        # Plot actual visualizations using 2D data
        plt.subplot(231)
        plt.plot(self.losses[-100:], label='Training Loss', color='blue')
        plt.title(f'Loss Curve (Current: {self.losses[-1]:.4f})')
        plt.xlabel('Last 100 Steps')
        plt.ylabel('Loss')
        plt.grid(True)
        
        # Use the 2D versions for prediction plots
        plt.subplot(235)
        idx = 0  # Plot first sequence in batch
        plt.plot(y_plot[idx], label='Ground Truth', color='blue', alpha=0.7)
        plt.plot(y_pred_plot[idx], label='Prediction', color='red', alpha=0.7)
        plt.title('Prediction vs Ground Truth')
        plt.xlabel('Sequence Step')
        plt.ylabel('Value')
        plt.legend()
        plt.grid(True)
    def generate_synthetic_data(num_samples: int = 1000,
                            seq_length: int = 50,
                            input_dim: int = 10,
                            device: str = 'cuda' if torch.cuda.is_available() else 'cpu'
                            ) -> tuple[torch.Tensor, torch.Tensor]:
        """Generate synthetic sequence data with multiple patterns"""
        # Time steps
        t = torch.linspace(0, 10, seq_length, device=device)
        t = t.view(1, -1, 1).repeat(num_samples, 1, input_dim)
        
        # Generate diverse patterns
        patterns = []
        for i in range(input_dim):
            # Combine sine waves with different frequencies
            freq1 = (i + 1) * 0.5
            freq2 = (i + 1) * 0.25
            pattern = torch.sin(2 * np.pi * freq1 * t[..., i]) + \
                    0.5 * torch.sin(2 * np.pi * freq2 * t[..., i])
            patterns.append(pattern)
        
        # Combine patterns
        x = torch.stack(patterns, dim=-1)
        
        # Create target with transformations
        y = torch.roll(x, shifts=-1, dims=1) * 1.5 + 0.5
        
        return x, y

    def train_epoch(model: nn.Module,
                    train_loader: torch.utils.data.DataLoader,
                    criterion: nn.Module,
                    optimizer: torch.optim.Optimizer,
                    device: str,
                    visualizer,
                    epoch: int) -> float:
        """Train for one epoch"""
        model.train()
        total_loss = 0
        
        for batch_idx, (x, y) in enumerate(train_loader):
            x, y = x.to(device), y.to(device)
            optimizer.zero_grad()
            
            # Forward pass
            y_pred = model(x)
            loss = criterion(y_pred, y)
            
            # Backward pass
            loss.backward()
            optimizer.step()
            
            total_loss += loss.item()
            
            # Update visualizations
            if batch_idx % visualizer.save_intervals == 0:
                with torch.no_grad():
                    # Get model states
                    ltc_state, _ = model.ltc(
                        model.input_proj(x[0:1]), 
                        torch.zeros(1, model.hidden_size, device=device),
                        torch.zeros(1, device=device)
                    )
                    attn_weights = model.get_attention_weights(x[0:1])
                    
                    # Compute accuracy
                    mse = ((y_pred - y) ** 2).mean().item()
                    accuracy = 100 * (1 - min(mse, 1))
                    
                    # Update visualizer
                    visualizer.update_metrics(loss.item(), accuracy, ltc_state, attn_weights)
            
            # Plot progress
            if batch_idx % visualizer.plot_intervals == 0:
                visualizer.plot_training_progress(epoch, batch_idx, x, y, y_pred)
        
        return total_loss / len(train_loader)
    
import torch
import torch.nn as nn
import torch.optim as optim
import matplotlib.pyplot as plt
import seaborn as sns
# %matplotlib inline

#from transformer_lnn import TransformerLNN
#from training_visualizer import TrainingVisualizer, generate_synthetic_data

## 1. Setup Training Parameters
# Model parameters
INPUT_SIZE = 10
HIDDEN_SIZE = 64
NUM_HEADS = 4

# Training parameters
BATCH_SIZE = 32
LEARNING_RATE = 1e-3
NUM_EPOCHS = 50

# Data parameters
NUM_SAMPLES = 1000
SEQ_LENGTH = 50

# Device configuration
device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
print(f"Using device: {device}")
## 2. Generate Synthetic Data

# Generate training data
x_train, y_train = TrainingVisualizer.generate_synthetic_data(
    num_samples=NUM_SAMPLES,
    seq_length=SEQ_LENGTH,
    input_dim=INPUT_SIZE,
    device=device
)

# Create dataloader
train_dataset = torch.utils.data.TensorDataset(x_train, y_train)
train_loader = torch.utils.data.DataLoader(
    train_dataset,
    batch_size=BATCH_SIZE,
    shuffle=True
)

# Plot sample sequence
plt.figure(figsize=(12, 4))
plt.plot(x_train[0, :, 0].cpu().numpy(), label='Input')
plt.plot(y_train[0, :, 0].cpu().numpy(), label='Target')
plt.title('Sample Sequence')
plt.legend()
plt.grid(True)
plt.show()

## 3. Create and Initialize Model
# Initialize model
model = TransformerLNN(
    input_size=INPUT_SIZE,
    hidden_size=HIDDEN_SIZE,
    num_heads=NUM_HEADS
).to(device)

# Loss and optimizer
criterion = nn.MSELoss()
optimizer = optim.Adam(model.parameters(), lr=LEARNING_RATE)

# Initialize visualizer
visualizer = TrainingVisualizer()
for epoch in range(NUM_EPOCHS):
    model.train()
    epoch_loss = 0
    
    for batch_idx, (x, y) in enumerate(train_loader):
        # Forward pass
        y_pred = model(x)
        loss = criterion(y_pred, y)
        
        # Backward pass
        optimizer.zero_grad()
        loss.backward()
        optimizer.step()
        
        epoch_loss += loss.item()
        
        # Update visualization
        if batch_idx % visualizer.save_intervals == 0:
            with torch.no_grad():
                # Get model states
                batch_input = x[0:1]  # Shape: [1, seq_len, input_dim]
                projected = model.input_proj(batch_input)  # Shape: [1, seq_len, hidden_size]
                
                # Take first timestep for LTC state
                ltc_input = projected[:, 0, :]  # Shape: [1, hidden_size]
                ltc_state, _ = model.ltc(
                    ltc_input,
                    torch.zeros(1, HIDDEN_SIZE, device=device),
                    torch.zeros(1, device=device)
                )

        attn_weights = model.get_attention_weights(batch_input)
        
        # Compute accuracy
        mse = ((y_pred - y) ** 2).mean().item()
        accuracy = 100 * (1 - min(mse, 1))
        
        # Update visualizer
        visualizer.update_metrics(loss.item(), accuracy, ltc_state, attn_weights)
        
        # Plot progress
        if batch_idx % visualizer.plot_intervals == 0:
            visualizer.plot_training_progress(epoch, batch_idx, x, y, y_pred)
    
    # End of epoch summary
    avg_loss = epoch_loss / len(train_loader)
    print(f"Epoch {epoch+1}/{NUM_EPOCHS}, Average Loss: {avg_loss:.4f}")
    
    # Generate test data
x_test, y_test = TrainingVisualizer.generate_synthetic_data(
    num_samples=10,
    seq_length=SEQ_LENGTH,
    input_dim=INPUT_SIZE,
    device=device
)

# Model evaluation
model.eval()
with torch.no_grad():
    y_pred = model(x_test)
    test_loss = criterion(y_pred, y_test)
    
    # Plot predictions
    plt.figure(figsize=(15, 5))
    for i in range(3):  # Plot first 3 sequences
        plt.subplot(1, 3, i+1)
        plt.plot(y_test[i, :, 0].cpu().numpy(), label='Ground Truth', alpha=0.7)
        plt.plot(y_pred[i, :, 0].cpu().numpy(), label='Prediction', alpha=0.7)
        plt.title(f'Sequence {i+1}')
        plt.legend()
        plt.grid(True)
    plt.tight_layout()
    plt.show()
    
print(f"Final Test Loss: {test_loss:.4f}")

# Plot training history
plt.figure(figsize=(15, 5))

# Loss progression
plt.subplot(131)
plt.plot(visualizer.losses, label='Training Loss')
plt.title('Loss Progression')
plt.xlabel('Step')
plt.ylabel('Loss')
plt.grid(True)

# Accuracy progression
plt.subplot(132)
plt.plot(visualizer.accuracies, label='Accuracy')
plt.title('Accuracy Progression')
plt.xlabel('Step')
plt.ylabel('Accuracy (%)')
plt.grid(True)

# Final attention pattern
plt.subplot(133)
# Extract a 2D slice from the 3D tensor
attn_weights_2d = visualizer.attn_weights[-1][0].cpu().numpy()

# Plot the 2D attention weights
sns.heatmap(attn_weights_2d, cmap='viridis')
plt.title('Final Attention Pattern')

plt.tight_layout()
plt.show()