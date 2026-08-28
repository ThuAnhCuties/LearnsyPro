package com.learnsypro.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.learnsypro.app.R
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * ── RandomMascotLayer ──
 * Bạn nữ tai mèo (28 pose, xem /res/drawable-nodpi/mascot_girl_XX.png) xuất
 * hiện NGẪU NHIÊN và THƯA THỚT trên Trang chủ — khác hẳn bản gốc người dùng
 * gửi (screenshot 188017.png) nơi mascot lặp lại dày đặc ở gần như mọi card.
 *
 * Thiết kế: tại một thời điểm chỉ có TỐI ĐA 1 bạn hiện trên màn hình. Sau khi
 * hiện đủ lâu (SHOW_DURATION), bạn ấy biến mất, rồi chờ một khoảng nghỉ ngẫu
 * nhiên (HIDE_MIN..HIDE_MAX) mới xuất hiện lại ở vị trí khác, pose khác. Vị
 * trí random trong 4 góc màn hình để không che nội dung chính giữa màn hình.
 * Được gọi ở tab Trang chủ VÀ tab Tài liệu (xem DashboardScreen.kt).
 *
 * Không chặn tương tác (giống FloatingDecos — không có Modifier.clickable,
 * đặt trong Box không có pointerInput nào), và tôn trọng cờ `enabled` từ
 * DashboardViewModel.mascotEnabled (toggle "Bạn đồng hành" trong Cài đặt).
 */
private val MASCOT_DRAWABLES = listOf(
    R.drawable.mascot_girl_01, R.drawable.mascot_girl_02, R.drawable.mascot_girl_03,
    R.drawable.mascot_girl_04, R.drawable.mascot_girl_05, R.drawable.mascot_girl_06,
    R.drawable.mascot_girl_07, R.drawable.mascot_girl_08, R.drawable.mascot_girl_09,
    R.drawable.mascot_girl_10, R.drawable.mascot_girl_11, R.drawable.mascot_girl_12,
    R.drawable.mascot_girl_13, R.drawable.mascot_girl_14,
    // mascot_girl_15 (pose cầm điện thoại) đã BỎ khỏi bộ theo yêu cầu — tránh
    // dính logo/hình dáng giống iPhone.
    R.drawable.mascot_girl_16, R.drawable.mascot_girl_17, R.drawable.mascot_girl_18,
    R.drawable.mascot_girl_19, R.drawable.mascot_girl_20, R.drawable.mascot_girl_21,
    R.drawable.mascot_girl_22, R.drawable.mascot_girl_23, R.drawable.mascot_girl_24,
    R.drawable.mascot_girl_25, R.drawable.mascot_girl_26, R.drawable.mascot_girl_27,
    R.drawable.mascot_girl_28,
)

private enum class MascotCorner { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }

private data class MascotSpot(
    val drawableRes: Int,
    val corner: MascotCorner,
    val sizeDp: Int,
    val insetXDp: Int,
    val insetYDp: Int
)

// "Cân bằng": đủ để người dùng thấy và nhận ra bạn ấy, nhưng nghỉ đủ lâu để
// không cảm giác dày đặc/gây phân tâm khi đang học. ~1 lần xuất hiện mỗi
// 20-40s, mỗi lần hiện 8s.
private const val SHOW_DURATION_MS = 8_000L
private const val HIDE_MIN_MS = 20_000L
private const val HIDE_MAX_MS = 40_000L

private fun randomSpot(): MascotSpot {
    val corner = MascotCorner.entries.toTypedArray().random()
    return MascotSpot(
        drawableRes = MASCOT_DRAWABLES.random(),
        corner = corner,
        sizeDp = Random.nextInt(72, 104),
        insetXDp = Random.nextInt(4, 22),
        // Chừa khoảng trống trên/dưới để không đè lên top bar hoặc bottom nav.
        insetYDp = Random.nextInt(90, 170)
    )
}

@Composable
fun RandomMascotLayer(enabled: Boolean, modifier: Modifier = Modifier) {
    if (!enabled) return

    var visible by remember { mutableStateOf(false) }
    var spot by remember { mutableStateOf(randomSpot()) }

    LaunchedEffect(Unit) {
        // Lần đầu vào Trang chủ: chờ một chút rồi mới xuất hiện, tránh cảm
        // giác giật ngay lúc màn hình vừa load xong.
        delay(Random.nextLong(3_000L, 8_000L))
        while (true) {
            spot = randomSpot()
            visible = true
            delay(SHOW_DURATION_MS)
            visible = false
            delay(Random.nextLong(HIDE_MIN_MS, HIDE_MAX_MS))
        }
    }

    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 4 },
            exit = fadeOut(tween(260)) + slideOutVertically(tween(260)) { it / 4 },
            modifier = Modifier.align(
                when (spot.corner) {
                    MascotCorner.TOP_START -> Alignment.TopStart
                    MascotCorner.TOP_END -> Alignment.TopEnd
                    MascotCorner.BOTTOM_START -> Alignment.BottomStart
                    MascotCorner.BOTTOM_END -> Alignment.BottomEnd
                }
            )
        ) {
            Image(
                painter = painterResource(id = spot.drawableRes),
                contentDescription = null,
                modifier = Modifier
                    .cornerPadding(spot.corner, spot.insetXDp)
                    .offset(y = signedInsetY(spot.corner, spot.insetYDp))
                    .size(spot.sizeDp.dp)
            )
        }
    }
}

/** Padding ngang theo đúng cạnh mà Box đã align (start cho *_START, end cho *_END). */
private fun Modifier.cornerPadding(corner: MascotCorner, insetXDp: Int): Modifier = when (corner) {
    MascotCorner.TOP_START, MascotCorner.BOTTOM_START -> this.then(Modifier.padding(start = insetXDp.dp))
    MascotCorner.TOP_END, MascotCorner.BOTTOM_END -> this.then(Modifier.padding(end = insetXDp.dp))
}

/** Chừa khoảng cách dọc: đẩy xuống dưới top bar khi ở góc trên, đẩy lên trên bottom nav khi ở góc dưới. */
private fun signedInsetY(corner: MascotCorner, dpValue: Int) = when (corner) {
    MascotCorner.TOP_START, MascotCorner.TOP_END -> dpValue.dp
    MascotCorner.BOTTOM_START, MascotCorner.BOTTOM_END -> -dpValue.dp
}
