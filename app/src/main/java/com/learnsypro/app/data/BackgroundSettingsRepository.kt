package com.learnsypro.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.learnsypro.app.ui.dashboard.BgSettings
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.net.URLEncoder

val Context.backgroundDataStore by preferencesDataStore(name = "learnsy_bg_cache")

private const val BG_BUCKET = "backgrounds"
private const val MAX_PX = 1920
private const val TARGET_BYTES = 900 * 1024 // 900 KB — nền có thể to hơn avatar
private const val UP_PREFIX = "learnsy_bg:"
private const val TTL_SECONDS = 60L * 60 * 24 * 30 // 30 ngày, giống bản web

data class BgUploadResult(val ok: Boolean, val sizeBytes: Int? = null, val msg: String? = null)

/**
 * ── BackgroundSettingsRepository (thay lsLoad/lsSave/upLoad/upSave/resizeImage
 *    trong background-settings.js) ──
 *
 * Khác bản web: ảnh nền KHÔNG lưu base64 trong Upstash (quá dài cho REST URL
 * dạng path của UpstashClient) — thay vào đó upload lên Supabase Storage
 * bucket "backgrounds" (giống AvatarRepository), chỉ lưu metadata nhỏ gọn
 * {presetId,blurMode,blurPercent,imageUrl} dạng JSON vào Upstash.
 *
 * Local cache dùng DataStore thay localStorage; key theo studentId, mặc định
 * "admin" khi chưa có id (tương đương lsKey(id) bản gốc).
 */
class BackgroundSettingsRepository(
    private val context: Context,
    private val upstash: UpstashClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun cacheKey(id: String) = stringPreferencesKey("learnsy_bg_${id.ifBlank { "admin" }}")
    private fun upstashKey(id: String) = UP_PREFIX + id.ifBlank { "admin" }

    /** Đọc từ DataStore local — tương đương lsLoad(). */
    suspend fun loadLocal(studentId: String?): BgSettings {
        val raw = context.backgroundDataStore.data.first()[cacheKey(studentId ?: "admin")]
        return raw?.let { runCatching { json.decodeFromString<BgSettings>(it) }.getOrNull() } ?: BgSettings()
    }

    /** Ghi vào DataStore local — tương đương lsSave(). */
    suspend fun saveLocal(studentId: String?, settings: BgSettings) {
        val key = cacheKey(studentId ?: "admin")
        context.backgroundDataStore.edit { it[key] = json.encodeToString(settings) }
    }

    /** Tải metadata từ Upstash — tương đương upLoad(). null nếu chưa từng lưu hoặc lỗi mạng. */
    suspend fun loadRemote(studentId: String): BgSettings? {
        val raw = upstash.get(upstashKey(studentId)) ?: return null
        // Giải mã lại — saveRemote() đã URL-encode JSON trước khi gửi lên Upstash.
        val decoded = runCatching {
            java.net.URLDecoder.decode(raw, "UTF-8")
        }.getOrDefault(raw)
        return runCatching { json.decodeFromString<BgSettings>(decoded) }.getOrNull()
            ?: runCatching { json.decodeFromString<BgSettings>(raw) }.getOrNull()
    }

    /**
     * Lưu metadata lên Upstash (30 ngày) — tương đương upSave(). UpstashClient.set
     * ghép value thẳng vào URL path (không tự encode), nên phải URL-encode JSON
     * ở đây trước — tránh vỡ request vì ký tự { } " : trong chuỗi JSON.
     */
    suspend fun saveRemote(studentId: String, settings: BgSettings) {
        runCatching {
            val encoded = URLEncoder.encode(json.encodeToString(settings), "UTF-8")
            upstash.set(upstashKey(studentId), encoded, expireSeconds = TTL_SECONDS)
        }
    }

    /**
     * Resize ảnh về tối đa MAX_PX (giữ tỉ lệ), nén JPEG giảm dần đến khi
     * ≤ TARGET_BYTES — tương đương resizeImage() trong bản gốc.
     *
     * LƯU Ý: hiện KHÔNG được gọi tới — uploadImage() bên dưới đang chặn tính
     * năng upload trên app có chủ đích (chỉ cho dùng ở website). Giữ lại hàm
     * này để dễ khôi phục sau này nếu cần mở lại tính năng.
     */
    private suspend fun resizeAndCompress(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Không mở được ảnh")
        val original = BitmapFactory.decodeStream(input)
        input.close()

        val w = original.width
        val h = original.height
        val scale = if (w > MAX_PX || h > MAX_PX) {
            if (w >= h) MAX_PX.toFloat() / w else MAX_PX.toFloat() / h
        } else 1f

        val targetW = (w * scale).toInt().coerceAtLeast(1)
        val targetH = (h * scale).toInt().coerceAtLeast(1)
        val scaled = if (scale != 1f) Bitmap.createScaledBitmap(original, targetW, targetH, true) else original

        var quality = 92
        var bytes: ByteArray
        while (true) {
            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            bytes = stream.toByteArray()
            if (bytes.size <= TARGET_BYTES || quality <= 40) break
            quality -= 5
        }

        if (scaled != original) scaled.recycle()
        original.recycle()
        bytes
    }

    /**
     * Upload ảnh nền bị CHẶN CÓ CHỦ ĐÍCH trên app Android — tính năng tải ảnh
     * nền tùy chỉnh chỉ hỗ trợ trên website (theo yêu cầu). Nút bấm vẫn hiện
     * bình thường trên UI, nhưng khi bấm sẽ trả về lỗi này thay vì thật sự
     * gọi Supabase Storage. resizeAndCompress()/Supabase upload không được
     * gọi tới — chặn ngay từ đầu hàm để không tốn CPU nén ảnh vô ích.
     *
     * Muốn mở lại tính năng này trên app: xóa khối chặn bên dưới, khôi phục
     * lại đoạn code upload thật (xem lịch sử git / bản trước khối chặn).
     */
    suspend fun uploadImage(studentId: String, imageUri: Uri): BgUploadResult {
        return BgUploadResult(
            ok = false,
            msg = "Custom background upload is only available on the website. Please visit the website to change your background image."
        )
    }

    /** Xoá ảnh nền khỏi Supabase Storage. Không xoá metadata (giống removeImage() bản gốc — chỉ xoá ảnh). */
    suspend fun deleteImage(studentId: String) {
        runCatching {
            SupabaseClientProvider.client.storage
                .from(BG_BUCKET)
                .delete("backgrounds/${studentId.ifBlank { "admin" }}.jpg")
        }
    }
}
