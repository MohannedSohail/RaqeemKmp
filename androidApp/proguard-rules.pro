# Raqeem ProGuard rules for offline Whisper JNI bindings

-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class org.mohtech.app.ai.whisper.** { *; }
-keepclassmembers class org.mohtech.app.ai.whisper.AndroidLocalSpeechTranscriber {
    private *;
    public *;
}
