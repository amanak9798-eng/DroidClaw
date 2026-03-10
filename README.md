<div align="center">
  <img src="https://via.placeholder.com/150/0f172a/10b981?text=DroidClaw" alt="DroidClaw Logo" width="150" height="150" />
  <h1>DroidClaw</h1>
  <p><strong>The On-Device Autonomous AI Agent for Android</strong></p>
  
  <p>
    <a href="#features">Features</a> •
    <a href="#quick-start">Quick Start</a> •
    <a href="#architecture">Architecture</a> •
    <a href="#contributing">Contributing</a>
  </p>
</div>

---

> 🚀 **WORK IN PROGRESS**: DroidClaw is currently in active development. We are open-sourcing early to gather community feedback and contributions. Expect breaking changes and bugs!

DroidClaw is an experimental, fully autonomous AI agent designed specifically for Android. It leverages on-device Large Language Models (LLMs) via `llama.cpp` to understand your screen, reason about tasks, and interact with the Android UI—all without sending your personal data to the cloud.

From reading your screen using Android's Accessibility APIs to simulating taps and swipes, DroidClaw aims to be a generalized assistant that can navigate apps, read content, and execute complex localized workflows.

## ✨ Features

- **On-Device Intelligence**: Runs entirely locally using `llama.cpp`. Your data never leaves your device.
- **Computer Vision Capabilities**: Can "see" the screen using advanced accessibility node parsing and screenshot analysis.
- **Autonomous Execution**: Uses an intelligent Agent Loop to plan, execute, and verify tasks without constant human intervention.
- **UI Interaction**: Can tap, swipe, scroll, and type text natively using Android Accessibility Services.
- **Extensible Tool System**:
  - `ToolTapElement`: Tap UI elements
  - `ToolSwipe`: Swipe up/down/left/right
  - `ToolTypeText`: Input text
  - `ToolFileRead`: Read local files
  - `ToolSendSms`: Send SMS messages
  ...and more!

## 🚀 Quick Start

### Prerequisites
- Android Studio Ladybug or newer
- An Android device running Android 12 (API 31) or higher
- At least 8GB of RAM on the device (required for running 3B+ parameter models)

### Setup Instructions

1. **Clone the repository:**
   ```bash
   git clone https://github.com/yourusername/droidclaw.git
   cd droidclaw
   ```

2. **Download a Model:**
   DroidClaw requires a `.gguf` model to function. We recommend models in the 1.5B to 3B range for mobile devices.
   - Example: *Qwen2.5-1.5B-Instruct-Q4_K_M.gguf* or *Phi-3-mini-4k-instruct-q4.gguf*
   - Place the downloaded `.gguf` file in your device's `Download/Models` folder.
   > ⚠️ **Note for Developers**: Do NOT commit 5GB `.gguf` files to this repository. GitHub limits file sizes to 100MB. Please use [HuggingFace](https://huggingface.co/) to host your models or link users directly to existing GGUF models.

3. **Build and Install:**
   Open the project in Android Studio, sync Gradle, and run it on your device.
   
   **To share the app with others (GitHub Releases):**
   1. Build a signed APK in Android Studio (`Build > Generate Signed Bundle / APK...`).
   2. Go to your GitHub repository and click **Releases > Draft a new release**.
   3. Drag and drop your `.apk` file into the "Attach binaries" section.
   4. Publish the release! Users can now download and install the app directly from your repo without compiling the code.

4. **Grant Permissions:**
   On first launch, you must grant:
   - **Accessibility Service**: Critical! This allows DroidClaw to read the screen and perform clicks/swipes.
   - **Notification Listener**: Allows the agent to read incoming notifications.
   - **Storage Permission**: To load the model files.

## 📱 Model Suggestions by Device Tier

Choosing the right model is critical for performance and battery life.

### High-End Devices (12GB+ RAM, Snapdragon 8 Gen 2/3)
- Models: `Qwen2.5-3B-Instruct` or `Llama-3.2-3B-Instruct`
- Quantization: `Q4_K_M` or `Q5_K_M`
- *Expect great reasoning capabilities but higher battery drain.*

### Mid-Range Devices (8GB RAM, Snapdragon 7 series)
- Models: `Qwen2.5-1.5B-Instruct` or `Phi-3-mini`
- Quantization: `Q4_K_M`
- *The sweet spot for speed and capability.*

### Lower-End / Older Devices (4GB - 6GB RAM)
- Models: `Qwen2.5-0.5B-Instruct` or `TinyLlama-1.1B`
- Quantization: `Q4_0`
- *Fast inference, but the agent may struggle with complex multi-step reasoning.*
- ⚠️ **Note:** To ensure smooth performance on low-end devices or when using models below 1B parameters, **autonomous tool calling is disabled by default**. In this mode, DroidClaw functions as a fast, private, on-device chatbot rather than an autonomous agent.

## 🏗️ Architecture

DroidClaw is built with a modular architecture:

- **`:app`**: The main Android UI (Jetpack Compose).
- **`:orchestrator`**: The "brain" containing the `AgentLoopService`, prompt generation, and state machine.
- **`:bridge`**: The connective tissue. Contains the `ToolRegistry`, handles Accessibility Service delegation, and executes actual device actions.
- **`:core`**: Shared domain models, states, and utilities.
- **`:llm`**: The JNI wrapper around `llama.cpp` for running the models natively on the CPU/GPU.

## 🤝 Contributing to DroidClaw

**We need your help!** DroidClaw is a massive undertaking, and we're looking for community support in several areas:

1. **New Tools**: Help us build tools for new actions (e.g., answering phone calls, controlling media, managing WiFi).
2. **Prompts & Reasoning**: The agent's system prompt needs constant tuning. Help us make it smarter and less prone to hallucination.
3. **Performance Optimization**: We need C++/JNI experts to help extract more performance out of the `llama.cpp` wrapper.
4. **UI Refinements**: The Compose UI can always be smoother!

### How to Help
Please read our [**CONTRIBUTING.md**](CONTRIBUTING.md) guide before submitting a PR.
- Check the [Issues](https://github.com/yourusername/droidclaw/issues) tab for `good first issue` tags.
- Join our community discussions to propose features.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---
*Built with ❤️ for the open-source Android community.*
