# ============================================================
# QuickQR — R8 configuration
# ============================================================
#
# Deliberately short. Compose, CameraX, Room, DataStore and Lifecycle ship their
# own consumer rules inside their AARs, and R8 applies them automatically. This
# file used to add blanket keeps for androidx.compose.runtime.**,
# androidx.camera.** and com.google.zxing.**, which stopped R8 from shrinking or
# optimising any of them. ZXing in particular is only used to *encode*, so R8 can
# now drop every decoder it ships.
#
# If a release build ever fails on a missing class, add a narrow rule for that
# class here — never a package-wide keep without a reason written next to it.

# Readable stack traces in Play Console. Upload mapping.txt with each release.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- ML Kit -----------------------------------------------------------------
# ML Kit discovers its components by reflection, from class names in manifest
# metadata that R8 cannot see. Without these rules BarcodeScanning.getClient()
# throws a NullPointerException in release builds only — debug builds, which are
# not minified, work fine, so this is easy to break without noticing.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-dontwarn com.google.android.gms.internal.mlkit_vision_barcode.**
