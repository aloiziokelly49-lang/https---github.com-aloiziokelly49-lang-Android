# CloudInk ProGuard Rules
# ========================

# EventBus
-keepattributes *Annotation*
-keepclassmembers class ** {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Tesseract OCR
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.rmtheis.tess.** { *; }

# CameraX
-keep class androidx.camera.** { *; }

# DataBinding
-keep class androidx.databinding.** { *; }
-dontwarn androidx.databinding.**

# RxJava3
-dontwarn io.reactivex.rxjava3.**
-keep class io.reactivex.rxjava3.** { *; }
