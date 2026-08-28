package com.learnsypro.app.data

import com.learnsypro.app.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

/**
 * Khởi tạo Supabase client cho app Android.
 *
 * KHÁC VỚI BẢN WEB (index.html gốc):
 * Bản web dùng /api/config trả về JSON mã hóa AES-256-GCM, rồi giải mã bằng
 * __ENC_KEY hardcode ngay trong client JS — cách này KHÔNG bảo mật thật sự
 * vì key vẫn nằm trong source gửi cho browser, ai xem source cũng lấy được.
 *
 * Với Android, ta dùng cách chuẩn của Supabase: SUPA_URL và anon SUPA_KEY
 * (public anon key) được thiết kế để đặt an toàn ở phía client — bảo mật
 * thực sự nằm ở Row Level Security (RLS) policies trên Supabase, không phải
 * ở việc giấu key. Không cần bước mã hóa/giải mã nào thêm.
 *
 * Thiết lập:
 * 1. Thêm vào local.properties (KHÔNG commit file này lên git):
 *      SUPA_URL=https://xxxx.supabase.co
 *      SUPA_KEY=eyJhbGciOi...
 * 2. Trong app/build.gradle.kts, đọc local.properties và đưa vào BuildConfig
 *    (xem hướng dẫn cuối file).
 */
object SupabaseClientProvider {

    val client by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPA_URL,
            supabaseKey = BuildConfig.SUPA_KEY
        ) {
            install(Postgrest)
            install(Auth)
            install(Realtime)
            install(Storage)
        }
    }
}

/*
═══ Hướng dẫn thêm vào app/build.gradle.kts để BuildConfig.SUPA_URL / SUPA_KEY hoạt động ═══

import java.util.Properties

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    defaultConfig {
        buildConfigField("String", "SUPA_URL", "\"${localProps.getProperty("SUPA_URL", "")}\"")
        buildConfigField("String", "SUPA_KEY", "\"${localProps.getProperty("SUPA_KEY", "")}\"")
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(platform("io.github.jan-tennert.supabase:bom:2.6.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:gotrue-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.ktor:ktor-client-android:2.3.11")
}
*/
