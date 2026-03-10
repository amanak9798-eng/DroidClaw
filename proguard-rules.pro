# ─── Kotlin ────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ─── Kotlin Serialization ──────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.droidclaw.**$$serializer { *; }
-keepclassmembers class com.droidclaw.** {
    *** Companion;
}
-keepclasseswithmembers class com.droidclaw.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ─── OkHttp ────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }



# ─── Room ──────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.**

# ─── Coroutines ────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ─── WorkManager ───────────────────────────────────────────────────────────
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ─── App-specific data classes used by JSON serialization ──────────────────
-keep class com.droidclaw.bridge.ToolExecuteRequest { *; }
-keep class com.droidclaw.bridge.ToolResultResponse { *; }
-keep class com.droidclaw.bridge.models.ToolResult { *; }
