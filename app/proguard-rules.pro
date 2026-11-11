# The release build shrinks the app. TAG talks to Shizuku through AIDL and reads a few things by name,
# so its own classes are kept whole; the libraries bring their own rules.
-keep class com.microtag.** { *; }
-dontwarn org.jetbrains.annotations.**
