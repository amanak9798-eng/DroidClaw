# DroidClaw Features

This document provides an overview of the tools and capabilities currently available to the DroidClaw autonomous agent.

## Core Capabilities

DroidClaw is designed to be a generalized Android assistant. It achieves this by combining local LLM reasoning with a suite of "Tools." The agent decides which tool to use based on your request and the current state of the device.

## Available Tools (Bridge Module)

These are the tools currently registered in the `ToolRegistry` that the agent can autonomously invoke.

### UI Interaction Tools
*   **`ToolTapElement`**:
    *   **Description**: Taps the screen at specific coordinates.
    *   **Usage**: The agent uses this to click buttons, open apps from the launcher, and navigate menus. It relies on the Accessibility Service to dispatch gestures.
*   **`ToolSwipe`**:
    *   **Description**: Swipes the screen in a specified direction (up, down, left, right).
    *   **Usage**: Used for scrolling through lists, changing pages, or dismissing items.
*   **`ToolTypeText`**:
    *   **Description**: Injects text into editable fields, handling keyboard simulation.
    *   **Usage**: Used for filling out forms, writing messages, or searching.

### Device Data Tools
*   **`ToolFileRead`**:
    *   **Description**: Reads the contents of a local file into the LLM's context.
    *   **Usage**: Used to process local documents, read logs, or analyze text files without cloud upload.
*   **`ToolSendSms`**:
    *   **Description**: Sends an SMS message to a specified number.
    *   **Usage**: Used for communication tasks requested by the user.

## System Tools

*   **Final Answer (`reply`)**: This is the default action when the agent determines it has completed the user's request. It stops the loop and presents the final text to the user in the Chat UI.

## Model Independence

DroidClaw is designed to be model-independent. As long as the `llama.cpp` server can load a `.gguf` file and the model understands JSON schemas and basic system prompting, it can be used.

### Supported Architectures
- Llama 3 / 3.1 / 3.2
- Qwen 2 / 2.5
- Phi 3 / 3.5

## Future Features Roadmap

The following features are currently being explored:

-   [ ] **Screenshot Analysis**: Utilizing VLMs (like LLaVA or Qwen-VL) to "see" the UI natively.
-   [ ] **Notification Reading**: Hooking into the `NotificationListenerService` to allow the agent to read and summarize incoming notifications.
-   [ ] **Intent Dispatching**: Allowing the agent to launch specific screens within other apps via precise Android Intents.
-   [ ] **Advanced Shell Execution**: (Root required) Direct interaction with the underlying Linux system.
