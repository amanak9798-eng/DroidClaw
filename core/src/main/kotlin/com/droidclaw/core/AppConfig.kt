package com.droidclaw.core

object AppConfig {
    const val LLM_PORT = 11434

    const val LOCALHOST = "127.0.0.1"


    // SharedPreferences file names
    const val PREFS_MAIN = "droidclaw_prefs"
    const val PREFS_TOOLS = "droidclaw_tool_prefs"

    // SharedPreferences keys
    const val PREFS_KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    const val PREFS_KEY_SELECTED_MODEL = "selected_model_id"
    const val PREFS_KEY_SELECTED_MODEL_NAME = "selected_model_name"

    // Agent behaviour toggles (Settings screen)
    const val PREFS_KEY_AGENT_HEARTBEAT   = "agent_heartbeat"
    const val PREFS_KEY_AGENT_AUTO_RETRY  = "agent_auto_retry"
    const val PREFS_KEY_AGENT_SAVE_FAILED = "agent_save_failed"

    // Performance toggles (Dashboard screen)
    const val PREFS_KEY_USE_VULKAN = "use_vulkan"
    const val PREFS_KEY_USE_NPU    = "use_npu"

    // Foreground service — action strings used by AgentForegroundService
    const val ACTION_START_AGENT = "com.droidclaw.ACTION_START_AGENT"
    const val ACTION_STOP_AGENT  = "com.droidclaw.ACTION_STOP_AGENT"
    const val EXTRA_MODEL_PATH   = "model_path"
    // Fully-qualified class name used by ui module to start the service without a direct dependency
    const val FOREGROUND_SERVICE_CLASS = "com.droidclaw.app.AgentForegroundService"


    // Task status values — must match strings stored in Room TaskDao
    object TaskStatus {
        const val PENDING   = "PENDING"
        const val RUNNING   = "RUNNING"
        const val COMPLETED = "COMPLETED"
        const val FAILED    = "FAILED"
    }
}
