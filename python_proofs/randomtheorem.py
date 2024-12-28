import numpy as np
import matplotlib.pyplot as plt
import altair as alt
import pandas as pd

# Function to simulate the LTC update
def ltc_update(state, input, tau, gain):
    return state + (1/tau) * (-state + gain * input)

# Generate random time constants and gains
taus = np.random.uniform(0.1, 1, 50)
gains = np.random.uniform(0.1, 1, 50)

# Initialize states
states = np.zeros(50)

# Generate random gaussian inputs
np.random.seed(42)
inputs = np.random.normal(0, 1, 5000)

# Simulate the LTC chain
last_ltc_output = []
for i in inputs:
    for j in range(50):
        states[j] = ltc_update(states[j], i, taus[j], gains[j])
    last_ltc_output.append(states[-1])

# Visualize the output
plt.hist(last_ltc_output, bins=50)
plt.xlabel("Output Value")
plt.ylabel("Frequency")
plt.title("Histogram of Output from the Last LTC")
plt.show()

# Create an Altair chart for the histogram
df = pd.DataFrame({'last_ltc_output': last_ltc_output})
chart = alt.Chart(df).mark_bar().encode(
    x=alt.X('last_ltc_output', bin=True),
    y='count()',
    tooltip=[alt.Tooltip('last_ltc_output', bin=True), 'count()']
).properties(
    title='Histogram of Output from the Last LTC'
).interactive()
chart.save('last_ltc_output_histogram.json')