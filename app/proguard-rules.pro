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

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Fix for java.lang.NoSuchFieldError: No instance field fontWeightAdjustment of type I in class Landroid/content/res/Configuration;
# This can happen on some API 31+ devices (or devices pretending to be API 31) when R8 optimizes away version checks.
-keepclassmembers class android.content.res.Configuration {
    int fontWeightAdjustment;
}

# Preserve Compose platform view names for better crash reports if obfuscation is too aggressive
-keepclassmembers class androidx.compose.ui.platform.AndroidComposeView {
    *;
}
