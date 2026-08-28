package com.learnsypro.app.ui.dashboard

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.ui.theme.NunitoFontFamily
import com.learnsypro.app.ui.toast.ToastController
import com.learnsypro.app.ui.toast.ToastType
import kotlinx.coroutines.launch

/**
 * ── AvatarUploader ──
 * Tương đương function AvatarUploader({student,dark,avatarUrl,loading,onUpload,onRemove})
 * trong avatar.jsx. Bấm vào avatar để mở gallery chọn ảnh (thay cho <input type="file">),
 * overlay spinner khi đang tải, badge camera góc dưới phải, nút "Đổi ảnh"/"Xóa",
 * feedback hiển thị qua ToastController dùng chung toàn app (thay vì message cục bộ).
 *
 * @param displayName tên hiển thị (student.display_name || student.username)
 * @param onUpload callback trả về AvatarUploadResult tương tự bản web {ok,size,msg}
 * @param onRemove callback xóa avatar
 */
@Composable
fun AvatarUploader(
    displayName: String,
    dark: Boolean,
    avatarUrl: String?,
    loading: Boolean,
    onUpload: suspend (Uri) -> Pair<Boolean, String?>,
    onRemove: suspend () -> Unit,
    modifier: Modifier = Modifier
) {
    var preview by remember { mutableStateOf<Uri?>(null) }
    var removing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val busy = loading || removing
    val displayUrl = preview?.toString() ?: avatarUrl

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        preview = uri
        scope.launch {
            val (ok, resultMsg) = onUpload(uri)
            preview = null
            val text = if (ok) {
                "Cập nhật thành công!" + (resultMsg?.let { " ($it)" } ?: "")
            } else {
                resultMsg ?: "Thất bại, thử lại nhé!"
            }
            ToastController.show(text, if (ok) ToastType.SUCCESS else ToastType.ERROR, scope = scope)
        }
    }

    Column(
        modifier = modifier.padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Avatar circle (tap to change) ──
        Box(
            modifier = Modifier
                .size(84.dp)
                .clickable(enabled = !busy) { pickImageLauncher.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            LetterAvatar(
                name = displayName,
                size = 84.dp,
                dark = dark,
                animate = true,
                avatarUrl = displayUrl
            )

            // Overlay — spinner khi busy, icon edit khi rảnh (bản Android luôn hiện nhẹ, không cần hover)
            if (busy) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .background(Color(0x61000000), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val rotation by rememberInfiniteTransition(label = "spin").animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
                        label = "spinRotation"
                    )
                    Box(modifier = Modifier.graphicsLayer { rotationZ = rotation }) {
                        DashboardIcon(name = "sparkle", size = 22.dp, color = Color.White)
                    }
                }
            }

            // Camera badge — góc dưới phải
            if (!busy) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(26.dp)
                        .background(AccentGradient, CircleShape)
                        .border(2.dp, if (dark) Color(0xFF1E0845) else Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    DashboardIcon(name = "sparkle", size = 13.dp, color = Color.White)
                }
            }
        }

        // ── Action buttons ──
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { pickImageLauncher.launch("image/*") },
                enabled = !busy,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF472B6)),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                DashboardIcon(name = "shuffle", size = 13.dp, color = Color.White)
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (loading) "Đang tải..." else "Đổi ảnh",
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    color = Color.White
                )
            }

            if (!avatarUrl.isNullOrBlank() && !busy) {
                OutlinedButton(
                    onClick = {
                        removing = true
                        scope.launch {
                            onRemove()
                            removing = false
                            ToastController.show("Đã xóa ảnh đại diện!", ToastType.SUCCESS, scope = scope)
                        }
                    },
                    shape = RoundedCornerShape(50),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0x66EF4444)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Xóa",
                        fontFamily = NunitoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            if (removing) {
                Text(
                    text = "Đang xóa…",
                    fontFamily = NunitoFontFamily,
                    fontSize = 11.sp,
                    color = Color(0xFFEF4444)
                )
            }
        }
    }
}
