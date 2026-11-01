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
# ML Kit finds its components through registrar classes named in manifest
# metadata and creates them by reflection. firebase-components ships
# `-keep class * implements ComponentRegistrar`, but in R8 full mode keeping a
# class does not keep its constructor, so the registrars cannot be created and
# BarcodeScanning.getClient() throws a NullPointerException — in release builds
# only, which makes it easy to break without noticing. Keeping the constructors
# is all ML Kit needs; its own rules cover its JNI and protobuf classes.
#
# This replaced package-wide keeps of com.google.mlkit.** and the ML Kit
# internals, which left about 13% of the app's code unoptimised and readable.
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}
