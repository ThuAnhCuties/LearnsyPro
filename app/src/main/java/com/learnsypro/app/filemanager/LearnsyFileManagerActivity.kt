package com.learnsypro.app.filemanager

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.learnsypro.app.darkModeDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * ── LearnsyFileManagerActivity ──
 * Base Activity dùng chung cho TOÀN BỘ module Quản lý tệp (trước đây là app MyFile
 * Manager độc lập — mọi Activity ở đây từng kế thừa trực tiếp AppCompatActivity,
 * tự chuyển sáng/tối theo Theme.Material3.DayNight = theo cài đặt HỆ THỐNG).
 *
 * Sau khi gộp vào Learnsy Pro, module này cần đồng bộ theo dark mode CỦA APP
 * (bật/tắt trong Cài đặt, lưu ở DataStore "learnsy_prefs"/"learnsy_dark" — xem
 * MainActivity.kt ở package cha) thay vì theo hệ thống, để trải nghiệm nhất quán
 * khi người dùng bấm icon Tệp tin ở header Dashboard: nếu app đang ở chế độ tối,
 * màn Quản lý tệp mở ra cũng phải tối ngay, bất kể điện thoại đang để sáng/tối gì.
 *
 * AppCompatDelegate.setDefaultNightMode() phải gọi TRƯỚC super.onCreate() để có
 * hiệu lực đúng lúc Activity resolve theme lần đầu (gọi sau sẽ không kịp, phải
 * recreate() mới áp dụng, gây nháy sai màu 1 khung hình).
 *
 * Đọc DataStore là API bất đồng bộ (Flow/suspend), nhưng theme phải quyết định
 * NGAY LẬP TỨC trước khung hình đầu tiên — dùng runBlocking ở đây CHỈ để đọc 1
 * giá trị boolean nhỏ, cục bộ trên máy (không qua mạng), nên độ trễ không đáng kể
 * (vài micro-giây) và an toàn để chặn ngắn tại đây, khác với runBlocking cho tác
 * vụ mạng/IO nặng (điều KHÔNG nên làm trên main thread).
 */
abstract class LearnsyFileManagerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applyLearnsyDarkModePreference()
        super.onCreate(savedInstanceState)
    }

    private fun applyLearnsyDarkModePreference() {
        try {
            // darkModeDataStore là EXTENSION PROPERTY trên android.content.Context (khai ở
            // MainActivity.kt, package cha) — không phải top-level val truy cập được qua tên
            // gói đủ (com.learnsypro.app.darkModeDataStore), PHẢI gọi trên 1 Context thật sự.
            // Activity chính LÀ Context nên dùng "this" trực tiếp là đủ, không cần applicationContext.
            val isDark = runBlocking {
                this@LearnsyFileManagerActivity.darkModeDataStore.data.first()[com.learnsypro.app.DARK_MODE_KEY]
            }
            // null = người dùng chưa từng đổi trong Cài đặt -> chưa có gì để đồng bộ,
            // giữ nguyên hành vi mặc định của hệ thống (AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            // thay vì ép về 1 chiều cụ thể.
            if (isDark != null) {
                AppCompatDelegate.setDefaultNightMode(
                    if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                )
            }
        } catch (e: Exception) {
            // Đọc preference thất bại (lỗi DataStore/IO hiếm gặp) KHÔNG được phép chặn
            // Activity mở lên — chấp nhận rớt về theme hệ thống mặc định còn hơn crash.
            com.learnsypro.app.filemanager.util.LogBus.error(
                "Không đọc được dark mode preference của Learnsy Pro, dùng theme hệ thống mặc định",
                "THEME_SYNC",
                e
            )
        }
    }
}
