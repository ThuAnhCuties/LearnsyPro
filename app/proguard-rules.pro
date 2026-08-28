# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ==================== Retrofit / OkHttp / Gson ====================
# Xem CloudApis.kt, DropboxService.kt, BoxService.kt
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes Exceptions

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Gson: giữ nguyên tên field của các data class model (Dropbox/Box/Drive) vì Gson dùng reflection
# theo đúng tên property lúc parse JSON — đổi tên/inline sẽ làm parse JSON sai/lỗi lúc runtime.
-keep class com.learnsypro.app.filemanager.cloud.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken

# ==================== Google Sign-In / Drive API ====================
# Xem GoogleDriveService.kt, CloudFragment.kt
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-dontwarn com.google.api.client.**
-dontwarn org.apache.http.**
-dontwarn com.google.common.**

# ==================== Apache POI (preview Word/Excel) ====================
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**
-dontwarn org.apache.commons.compress.**
-keep class org.apache.poi.** { *; }
-keep class org.openxmlformats.** { *; }
-keep class schemasMicrosoftComOffice.** { *; }

# ==================== Apache MINA FtpServer ====================
-dontwarn org.apache.mina.**
-dontwarn org.apache.ftpserver.**
-keep class org.apache.ftpserver.** { *; }

# ==================== ZXing (QR) ====================
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ==================== Supabase / Ktor ====================
-dontwarn io.ktor.**
-dontwarn kotlinx.serialization.**
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.learnsypro.app.**$$serializer { *; }
-keepclassmembers class com.learnsypro.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.learnsypro.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ==================== AppAuth (OAuth Dropbox/Box) ====================
-keep class net.openid.appauth.** { *; }
-dontwarn net.openid.appauth.**

# ==================== Coil / Media3 ====================
-dontwarn coil.**
-dontwarn androidx.media3.**

# ==================== SSHJ / SMBJ (SFTP client + SMB client) ====================
# Các thư viện này reference optional/runtime-only class không có trên Android
# (Java EE javax.el.*, OSGi framework, java.awt.Shape cho batik/graphbuilder mà
# log4j kéo theo, Kerberos org.ietf.jgss.* cho SPNEGO auth của SMB, sun.security.x509
# cho eddsa). Các nhánh code dùng chúng không bao giờ chạy trên Android (không có
# GUI server, không cần OSGi, không cần Java EE) nên an toàn để R8 bỏ qua thay vì
# fail cứng lúc build.
-dontwarn com.hierynomus.**
-dontwarn net.schmizz.**
-dontwarn org.apache.sshd.**
-dontwarn org.apache.mina.core.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.slf4j.**
-dontwarn javax.el.**
-dontwarn java.awt.**
-dontwarn org.osgi.**
-dontwarn org.ietf.jgss.**
-dontwarn org.ivy.**
-dontwarn com.graphbuilder.**
-dontwarn net.engio.mbassy.**
-dontwarn net.i2p.crypto.eddsa.**
-dontwarn aQute.bnd.**
-dontwarn org.bouncycastle.**
-keep class com.hierynomus.** { *; }
-keep class net.schmizz.** { *; }

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ==================== Mascot drawables (tránh Resource ID #0x0) ====================
# NGUYÊN NHÂN THẬT (xác nhận bằng decompile app-release.apk build 24.5, không phải
# suy đoán): R8 bundled trong AGP 9.0.1 không parse được Kotlin metadata của Kotlin
# 2.4.0 — build log có hàng trăm dòng "WARNING: R8: An error occurred when parsing
# kotlin metadata" cho MỌI class, đây KHÔNG phải warning vô hại. Hậu quả: R8 hiểu sai
# cấu trúc `object MascotPose { const val PEEK = R.drawable.mascot_girl_12 ... }` và
# const-fold nhầm các hằng số này thành 0 khi inline tại nơi gọi — xác nhận trực tiếp
# qua bytecode: `MascotImage(drawableRes = MascotPose.PEEK, ...)` trong TabHome.kt
# (LoadingState, màn hình đầu tiên sau login) bị R8 biên dịch thành lời gọi
# painterResource(id = 0) với 0 là literal cứng, không phải tham số truyền vào.
# Ảnh PNG trong resources.arsc vẫn còn nguyên vẹn — đây không phải lỗi resource
# shrinker, keep.xml (đã có, đúng, giữ nguyên) không giải quyết được lớp bug này.
#
# Fix: chặn R8 tối ưu hoá/inline object MascotPose và MASCOT_DRAWABLES, buộc giữ
# nguyên dạng field runtime thay vì const-fold tại compile time.
-keepclassmembers class com.learnsypro.app.ui.dashboard.MascotPose {
    public static final int *;
}
-keep,allowobfuscation class com.learnsypro.app.ui.dashboard.MascotPose
-keepclassmembers class com.learnsypro.app.ui.dashboard.RandomMascotLayerKt {
    *** MASCOT_DRAWABLES;
}

-keepclassmembers class **.R$drawable {
    public static <fields>;
}
