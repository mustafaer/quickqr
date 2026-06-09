# ============================================================
# QuickQR — Production ProGuard / R8 Configuration
# ============================================================

# ---- General ------------------------------------------------
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes InnerClasses,EnclosingMethod
-keepattributes Exceptions

# Preserve names for debugging crash logs (Firebase Crashlytics, Play Console)
-renamesourcefileattribute SourceFile

# ---- Kotlin -------------------------------------------------
-dontwarn kotlin.**
-dontwarn kotlinx.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ---- Room Database ------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface *
-keepclassmembers @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# ---- DataStore Preferences ----------------------------------
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# ---- Jetpack Compose ----------------------------------------
-dontwarn androidx.compose.**
# Keep Composable functions discoverable
-keep class androidx.compose.runtime.** { *; }

# ---- ML Kit (Barcode Scanning) ------------------------------
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-dontwarn com.google.android.gms.internal.mlkit_vision_barcode.**

# ---- ZXing (QR Code Generation) -----------------------------
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ---- CameraX ------------------------------------------------
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ---- AndroidX Lifecycle / ViewModel -------------------------
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}

# ---- Google Play Core (SplashScreen, etc.) -------------------
-keep class androidx.core.splashscreen.** { *; }

# ---- Prevent R8 from stripping data classes -----------------
-keepclassmembers class net.mustafaer.quickqr.data.** {
    <init>(...);
    <fields>;
}

# ---- Keep FileProvider paths --------------------------------
-keep class androidx.core.content.FileProvider { *; }

# ---- Enum safety --------------------------------------------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---- Serializable / Parcelable -----------------------------
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ---- Remove Log calls in release builds ---------------------
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
