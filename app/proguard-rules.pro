# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve source + line info so release stack traces can be retraced reliably.
-keepattributes SourceFile,LineNumberTable

# Normalize source filename in obfuscated traces.
-renamesourcefileattribute SourceFile

# Fix for java.lang.NoSuchFieldError: No instance field fontWeightAdjustment of type I in class Landroid/content/res/Configuration;
# This can happen on some API 31+ devices (or devices pretending to be API 31) when R8 optimizes away version checks.
-keepclassmembers class android.content.res.Configuration {
    int fontWeightAdjustment;
}

# Preserve Compose platform view names for better crash reports if obfuscation is too aggressive
-keepclassmembers class androidx.compose.ui.platform.AndroidComposeView {
    *;
}

# Local runtime readiness check uses Class.forName("com.google.ai.edge.litertlm.Engine")
# and therefore depends on the binary class name.
-keepnames class com.google.ai.edge.litertlm.Engine

# LiteRT-LM JNI bridge looks up Java members (for example in SamplerConfig)
# from native code. Obfuscating/removing these members can cause:
# "JNI DETECTED ERROR IN APPLICATION: mid == null ... CallIntMethodV"
-keep class com.google.ai.edge.litertlm.** { *; }

# LocalInferenceClient reads several ML Kit GenAI model properties by reflection
# (e.g., candidates/text/errorCode). Preserve reflective members.
-keepclassmembers class com.google.mlkit.genai.common.** {
    *** getErrorCode(...);
    <fields>;
}

-keepclassmembers class com.google.mlkit.genai.prompt.** {
    *** getCandidates(...);
    *** getText(...);
    <fields>;
}
