# Contributing to DroidClaw

Thank you for your interest in contributing to DroidClaw! As an experimental, on-device autonomous AI agent for Android, this project thrives on community input, ideas, and code contributions.

We are currently in active development, which means things move fast, APIs break, and bugs exist. Your help is incredibly valuable!

## 🌟 How You Can Help

There are several areas where DroidClaw needs immediate support:

1.  **New Tools (Bridge Layer):** The true power of DroidClaw lies in the actions it can perform on the device. We need more tools built into the `ToolRegistry` (e.g., controlling media, reading specific types of intents, taking photos).
2.  **Prompt Engineering:** The agent's `system prompt` in the `AgentLoopService` is critical. If you are good at structuring instructions to reduce hallucination and improve reasoning steps for open-source models, we need you!
3.  **UI/UX Refinements:** The Jetpack Compose UI in the `:app` module can always be smoother, more accessible, and more feature-rich.
4.  **Performance Optimization:** Help us improve the JNI wrapper around `llama.cpp` to squeeze every bit of performance and battery efficiency out of Android devices.
5.  **Documentation:** Writing clearer guides, documenting the codebase, and keeping `llms.txt` updated.
6.  **Bug Reports & Testing:** Don't have time to code? Run DroidClaw on your device, track its behavior, and open detailed bug reports.

## 🛠️ Development Setup

1.  **Fork and Clone:** Fork the repository on GitHub and clone your fork locally.
2.  **Android Studio:** Use Android Studio Ladybug or newer.
3.  **NDK/CMake:** Ensure you have the Android NDK and CMake installed via the SDK Manager, as the `:llm` module compiles `llama.cpp` natively.
4.  **Dependencies:** Let Gradle sync. If you encounter issue with Room or Compose compiler versions, please open an issue or check recent commits.
5.  **Model Loading:** For testing changes to the agent loop, ensure you have a valid `.gguf` model downloaded to your test device.

## 📝 Branching and Pull Requests

1.  **Branching:** Create a feature branch from `main`. Use a descriptive name: `feature/new-tool-camera` or `fix/crash-on-model-load`.
2.  **Draft PRs:** If you're working on a large change, open a Draft Pull Request early so maintainers can provide feedback on the direction.
3.  **Describe Your Changes:** When opening a PR, clearly explain *what* you changed and *why*. If your change affects the UI, include screenshots or a screen recording!
4.  **Testing:** Note how you tested the feature. E.g., "Ran on Pixel 7 Pro with Qwen2.5-3B-Q4_K_M. Verified the new swipe tool correctly scrolled the settings menu."

## 🧩 Adding a New Tool

If you want to add a new action the agent can perform, follow these steps:

1.  **Create the Tool Class:** In the `:bridge` module under `tools/`, create a new Kotlin object inheriting from `BaseTool`.
2.  **Define the Schema:** Define the JSON schema for the tool's parameters so the LLM knows how to call it.
3.  **Implement `execute()`:** Write the code to perform the action. If it interacts with the UI, you'll likely need to use the `ActionSender` provided by the Accessibility Service.
4.  **Register the Tool:** Add your new tool to the `com.droidclaw.bridge.ToolRegistry` so the `AgentLoopService` is aware of it and includes it in the system prompt.

## 💬 Community and Discussions

While we are working on setting up a Discord or Slack channel, the best place to discuss ideas is in the **GitHub Discussions** tab or by opening an **Issue** tagged with `discussion`.

---

*Thank you for helping us build the future of on-device AI for Android!*
