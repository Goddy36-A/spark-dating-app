# ── Supabase / Ktor ───────────────────────────────────────────────────────────
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn io.github.jan.supabase.**

# ── Kotlinx Serialization ─────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.spark.dating.**$$serializer { *; }
-keepclassmembers class com.spark.dating.** {
    *** Companion;
}
-keepclasseswithmembers class com.spark.dating.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Firebase ──────────────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ── Hilt ─────────────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-dontwarn dagger.hilt.**

# ── Room ─────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.**

# ── OkHttp ───────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Coil ─────────────────────────────────────────────────────────────────────
-dontwarn coil.**

# ── Google Play Billing ───────────────────────────────────────────────────────
-keep class com.android.billingclient.** { *; }

# ── Keep Compose lambdas ──────────────────────────────────────────────────────
-keepclassmembers class ** {
    public static ** ComposableSingletons$*;
}

# ── General Android ──────────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-keepclassmembers class **.R$* {
    public static <fields>;
}

# ── Remove logs in release ────────────────────────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
