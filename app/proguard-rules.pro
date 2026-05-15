# Blink - 20-20-20 Eye Care

# Shizuku
-keep class rikka.shizuku.** { *; }

# Receivers & Services
-keep class com.eyecare.daemon.receiver.** { *; }
-keep class com.eyecare.daemon.service.** { *; }

# Kotlin serialization metadata
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Lifecycle
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * implements androidx.lifecycle.LifecycleOwner { *; }
