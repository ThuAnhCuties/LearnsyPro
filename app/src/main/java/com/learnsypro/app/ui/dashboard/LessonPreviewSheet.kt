package com.learnsypro.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.ui.quiz.Lesson
import com.learnsypro.app.ui.theme.NunitoFontFamily

/**
 * ── LessonPreviewSheet ──
 * Bottom sheet xác nhận trước khi vào bài học — hiện sau khi bấm "Học!" ở
 * Trang chủ, trước khi thật sự mở QuizPlayer. Không tìm thấy file JSX
 * nguồn tương ứng trong repo GitHub tại thời điểm convert (không phải
 * home-screen.jsx/dashboard.jsx đã có — component web thật đứng sau nút
 * "Học!" chưa xác định được tên/đường dẫn file), nên UI ở đây được TÁI
 * TẠO LẠI thủ công dựa theo ảnh chụp thực tế từ bản deploy production
 * (zuka.gq), giữ đúng bố cục quan sát được: icon sách + tên bài + môn
 * học/số câu + 2 nút "Để sau" / "Bắt đầu học!". Logic bên trong (đặc biệt
 * animation, câu chữ phụ) là suy luận hợp lý theo phong cách chung của
 * app, KHÔNG phải convert 1:1 từ mã nguồn — nếu sau này tìm ra đúng file
 * JSX gốc, nên đối chiếu lại và chỉnh cho khớp.
 */
@Composable
fun LessonPreviewSheet(
    lesson: Lesson?,
    dark: Boolean,
    onDismiss: () -> Unit,
    onStart: () -> Unit
) {
    AnimatedVisibility(
        visible = lesson != null,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(180))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = lesson != null,
                enter = slideInVertically(tween(220)) { it },
                exit = slideOutVertically(tween(180)) { it }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = false) {} // chặn click xuyên xuống overlay phía sau
                        .background(
                            if (dark) Color(0xFF221128) else Color.White,
                            RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                        )
                        .padding(horizontal = 22.dp, vertical = 22.dp)
                ) {
                    // Thanh kéo (drag handle) — chỉ trang trí, không kéo được thật
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(36.dp)
                            .height(4.dp)
                            .background(
                                if (dark) Color(0x33FFFFFF) else Color(0x1F000000),
                                RoundedCornerShape(50)
                            )
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    if (lesson != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Mascot cầm bảng "Yêu bạn!" — cổ vũ trước khi vào bài,
                            // thay cho icon sách tròn trung tính.
                            MascotImage(drawableRes = MascotPose.SIGN_LOVE_YOU, sizeDp = 60)
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = lesson.title,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (dark) Color(0xFFF5E9FF) else Color(0xFF2A1233),
                                    fontFamily = NunitoFontFamily
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${lesson.subject ?: "Tiếng Anh"} · ${lesson.questionCount} câu hỏi",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (dark) Color(0xFFB89FCF) else Color(0xFFA07090),
                                    fontFamily = NunitoFontFamily
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(22.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Nút "Để sau" — viền, không nền
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(
                                        1.5.dp,
                                        if (dark) Color(0x33FFFFFF) else Color(0xFFF5D5E8),
                                        RoundedCornerShape(50)
                                    )
                                    .clickable(onClick = onDismiss)
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Để sau",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (dark) Color(0xFFB89FCF) else Color(0xFFA07090),
                                    fontFamily = NunitoFontFamily
                                )
                            }

                            // Nút "Bắt đầu học!" — gradient hồng-tím, khớp màu chủ đạo CTA toàn app
                            Box(
                                modifier = Modifier
                                    .weight(2f)
                                    .background(
                                        Brush.linearGradient(listOf(Color(0xFFF472B6), Color(0xFFA855F7))),
                                        RoundedCornerShape(50)
                                    )
                                    .clickable(onClick = onStart)
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    DashboardIcon(name = "zap", size = 16.dp, color = Color.White)
                                    Text(
                                        text = "Bắt đầu học!",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        fontFamily = NunitoFontFamily
                                    )
                                }
                            }
                        }

                        // Đệm an toàn dưới cùng cho các máy có gesture nav bar
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}
