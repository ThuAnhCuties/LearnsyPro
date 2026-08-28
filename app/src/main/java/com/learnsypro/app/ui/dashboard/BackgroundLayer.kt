package com.learnsypro.app.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * ── BackgroundLayer ──
 * Tương đương applyBackground() + #learnsy-bg-overlay trong background-settings.js.
 *
 * Bản web dựng 1 div overlay cố định, đặt gradient/ảnh + filter:blur() lên
 * chính overlay đó (né stacking-context cắt backdrop-filter), rồi phủ thêm
 * lớp dim màu bằng box-shadow inset. Ở Compose không có vấn đề stacking-
 * context kiểu DOM, nên chỉ cần 1 Box nền dưới cùng + Modifier.blur() +
 * 1 Box dim màu phủ lên trên — tương đương về mặt hình ảnh.
 *
 * Đặt Composable này ngay dưới FloatingDecos trong DashboardScreen, phía
 * trên background mặc định của Scaffold.
 */
@Composable
fun BackgroundLayer(
    settings: BgSettings,
    dark: Boolean,
    liteMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    // blurMode 'off' → tắt hẳn nền, chỉ còn màu nền phẳng (giống bản gốc)
    if (settings.blurMode == "off") {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(if (dark) Color(0xFF12000E) else Color(0xFFFFF5F9))
        )
        return
    }

    val percent = settings.blurPercent
    val blurDp by animateDpAsState(
        targetValue = if (liteMode) 0.dp else blurDpForPercent(percent).dp,
        animationSpec = tween(350),
        label = "bgBlur"
    )
    val dimColor by animateColorAsState(
        targetValue = if (dark) {
            Color(10 / 255f, 0f, 12 / 255f, dimAlphaDark(percent))
        } else {
            Color(1f, 1f, 1f, dimAlphaLight(percent))
        },
        animationSpec = tween(350),
        label = "bgDim"
    )

    // Nếu preset sáng nhưng dark mode đang bật → swap sang default_dark (giống bản gốc)
    val resolvedId = if (dark && settings.presetId in LIGHT_PRESET_IDS) "default_dark" else settings.presetId
    val preset = BG_PRESETS.find { it.id == resolvedId } ?: BG_PRESETS.first()

    Box(modifier = modifier.fillMaxSize()) {
        if (settings.presetId == "custom_image" && !settings.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = settings.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .let { if (blurDp.value > 0f) it.blur(blurDp) else it }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .let { if (blurDp.value > 0f) it.blur(blurDp) else it }
                    .background(preset.gradient ?: Brush())
            )
        }
        // Lớp dim màu phủ lên trên — tương đương box-shadow inset của overlay
        Box(modifier = Modifier.fillMaxSize().background(dimColor))
    }
}

/** Fallback trong trường hợp preset không có gradient (không nên xảy ra). */
private fun Brush(): androidx.compose.ui.graphics.Brush =
    androidx.compose.ui.graphics.SolidColor(Color(0xFFFFF5F9))
