package com.learnsypro.app.filemanager
import com.learnsypro.app.R

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.text.HtmlCompat
import androidx.core.text.getSpans
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.learnsypro.app.databinding.ActivityNoteEditorBinding
import com.learnsypro.app.filemanager.notes.NoteFileStore
import com.learnsypro.app.filemanager.util.ActivityTransitions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Màn hình soạn ghi chú — "bê" đủ bộ công cụ định dạng như 1 app Note thật: in đậm/nghiêng/gạch
 * chân, tô màu chữ, cỡ chữ, gạch đầu dòng, checklist, chèn ảnh. Dùng thẳng Spannable của
 * EditText (không cần thư viện rich-text ngoài) — mỗi nút áp/gỡ 1 loại Span lên đúng vùng bôi
 * đen hiện tại.
 *
 * Lưu = convert Spannable -> chuỗi HTML thật rồi ghi file .html vào Notes/ (không dùng database
 * riêng) — ghi chú vì vậy xem được, mở được bằng bất kỳ trình duyệt/HtmlViewerActivity nào, và
 * hiện đúng như 1 file bình thường khi duyệt Bộ nhớ trong.
 */
class NoteEditorActivity : LearnsyFileManagerActivity() {

    private lateinit var binding: ActivityNoteEditorBinding
    private var existingFile: File? = null
    private var hasUnsavedChanges = false

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) insertImageAtCursor(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        com.learnsypro.app.filemanager.util.WindowInsetsUtils.applyTopInsetHeight(binding.statusBarSpacer)

        binding.toolbar.title = getString(R.string.notes_new)
        binding.toolbar.setNavigationOnClickListener { confirmBackIfNeeded() }
        binding.toolbar.setOnMenuItemClickListener { onMenuItemSelected(it) }
        // Menu "Xóa" chỉ có ý nghĩa với ghi chú ĐÃ TỒN TẠI trên đĩa — ẩn khi đang tạo mới, vì
        // chưa có file nào để xóa (bấm sẽ không làm gì, dễ gây hiểu nhầm là lỗi).
        binding.toolbar.menu.findItem(R.id.action_delete)?.isVisible = false

        setupFormattingToolbar()
        trackUnsavedChanges()

        onBackPressedDispatcher.addCallback(this) { confirmBackIfNeeded() }

        val path = intent.getStringExtra(EXTRA_FILE_PATH)
        if (path != null) loadExistingNote(File(path))
    }

    private fun trackUnsavedChanges() {
        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { hasUnsavedChanges = true }
            override fun afterTextChanged(s: Editable?) {}
        }
        binding.etTitle.addTextChangedListener(watcher)
        binding.etContent.addTextChangedListener(watcher)
    }

    // ---------------- đọc ghi chú cũ (nếu sửa) ----------------

    private fun loadExistingNote(file: File) {
        existingFile = file
        binding.toolbar.menu.findItem(R.id.action_delete)?.isVisible = true
        lifecycleScope.launch {
            val html = withContext(Dispatchers.IO) { try { file.readText() } catch (e: Exception) { "" } }
            val title = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)?.trim().orEmpty()
            val bodyHtml = Regex("<body[^>]*>(.*?)</body>", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1) ?: html

            binding.etTitle.setText(HtmlCompat.fromHtml(title, HtmlCompat.FROM_HTML_MODE_LEGACY).toString())
            // FROM_HTML_MODE_LEGACY hiểu đúng <b>/<i>/<u>/<span style="color/font-size">/<img
            // src="data:...">: giữ nguyên toàn bộ định dạng đã lưu trước đó khi mở lại để sửa.
            val spanned = HtmlCompat.fromHtml(bodyHtml, HtmlCompat.FROM_HTML_MODE_LEGACY, { source ->
                // Ảnh nhúng base64 (data:image/...) -> giải mã trực tiếp thành Drawable để hiện
                // trong EditText, KHÔNG cần tải file ngoài vì ảnh đã nằm sẵn trong chính file HTML.
                decodeBase64Image(source)
            }, null)
            binding.etContent.setText(spanned)
            binding.toolbar.title = binding.etTitle.text.toString().ifBlank { getString(R.string.notes_untitled) }
            hasUnsavedChanges = false
        }
    }

    private fun decodeBase64Image(dataUri: String): android.graphics.drawable.Drawable? {
        return try {
            val base64 = dataUri.substringAfter("base64,", missingDelimiterValue = "")
            if (base64.isEmpty()) return null
            val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val drawable = android.graphics.drawable.BitmapDrawable(resources, bitmap)
            // Ảnh chèn giữ tối đa chiều rộng màn hình, không tràn khung soạn thảo.
            val maxWidth = (resources.displayMetrics.widthPixels - 64).coerceAtLeast(200)
            val scale = if (bitmap.width > maxWidth) maxWidth.toFloat() / bitmap.width else 1f
            drawable.setBounds(0, 0, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1))
            drawable
        } catch (e: Exception) {
            null
        }
    }

    // ---------------- thanh công cụ định dạng ----------------

    private fun setupFormattingToolbar() {
        binding.btnBold.setOnClickListener { toggleStyleSpan(Typeface.BOLD) }
        binding.btnItalic.setOnClickListener { toggleStyleSpan(Typeface.ITALIC) }
        binding.btnUnderline.setOnClickListener { toggleUnderline() }
        binding.btnColor.setOnClickListener { showColorPicker() }
        binding.btnFontSize.setOnClickListener { showSizePicker() }
        binding.btnBulletList.setOnClickListener { insertLinePrefix("•  ") }
        binding.btnChecklist.setOnClickListener { insertLinePrefix("☐  ") }
        binding.btnInsertImage.setOnClickListener { imagePickerLauncher.launch("image/*") }
    }

    /** true nếu người dùng có bôi đen 1 đoạn — bold/nghiêng/gạch chân/màu/cỡ chữ CHỈ áp lên đúng đoạn đó, giống mọi app Note thật. */
    private fun requireSelection(): IntRange? {
        val start = binding.etContent.selectionStart
        val end = binding.etContent.selectionEnd
        if (start == end) {
            Toast.makeText(this, getString(R.string.notes_select_text_first), Toast.LENGTH_SHORT).show()
            return null
        }
        return minOf(start, end) until maxOf(start, end)
    }

    /**
     * Bật/tắt kiểu chữ (đậm/nghiêng) trên vùng bôi đen — kiểm tra span đã có ở NGAY ĐẦU vùng chọn
     * để quyết định thêm hay gỡ, giống hành vi toggle thật của mọi trình soạn thảo (bôi đen đoạn
     * đã đậm rồi bấm lại nút Đậm -> hết đậm, thay vì luôn cộng dồn thêm span mới mỗi lần bấm).
     */
    private fun toggleStyleSpan(style: Int) {
        val range = requireSelection() ?: return
        val editable = binding.etContent.text
        val existing = editable.getSpans<StyleSpan>(range.first, range.first + 1).firstOrNull { it.style == style }
        if (existing != null) {
            removeSpanFromRange(editable, StyleSpan::class.java, range) { it.style == style }
        } else {
            editable.setSpan(StyleSpan(style), range.first, range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        hasUnsavedChanges = true
    }

    private fun toggleUnderline() {
        val range = requireSelection() ?: return
        val editable = binding.etContent.text
        val hasUnderline = editable.getSpans<UnderlineSpan>(range.first, range.first + 1).isNotEmpty()
        if (hasUnderline) {
            removeSpanFromRange(editable, UnderlineSpan::class.java, range) { true }
        } else {
            editable.setSpan(UnderlineSpan(), range.first, range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        hasUnsavedChanges = true
    }

    /**
     * Gỡ đúng phần span nằm TRONG vùng bôi đen — nếu span cũ trải dài RỘNG HƠN vùng đang chọn
     * (vd. cả câu đã đậm, giờ chỉ bôi đen 1 từ để bỏ đậm riêng từ đó), phải CẮT span cũ thành 2
     * đoạn còn lại (trước và sau vùng chọn) thay vì xóa nguyên span, để phần chữ ngoài vùng chọn
     * không bị mất định dạng theo.
     */
    private fun <T : Any> removeSpanFromRange(editable: Editable, type: Class<T>, range: IntRange, matches: (T) -> Boolean) {
        val spans = editable.getSpans(range.first, range.last + 1, type)
        for (span in spans) {
            if (!matches(span)) continue
            val spanStart = editable.getSpanStart(span)
            val spanEnd = editable.getSpanEnd(span)
            editable.removeSpan(span)
            if (spanStart < range.first) {
                val newSpan = cloneSpan(span) ?: continue
                editable.setSpan(newSpan, spanStart, range.first, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (spanEnd > range.last + 1) {
                val newSpan = cloneSpan(span) ?: continue
                editable.setSpan(newSpan, range.last + 1, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun <T : Any> cloneSpan(span: T): Any? = when (span) {
        is StyleSpan -> StyleSpan(span.style)
        is UnderlineSpan -> UnderlineSpan()
        is ForegroundColorSpan -> ForegroundColorSpan(span.foregroundColor)
        is RelativeSizeSpan -> RelativeSizeSpan(span.sizeChange)
        else -> null
    }

    private fun showColorPicker() {
        val range = requireSelection() ?: return
        val colors = intArrayOf(
            android.graphics.Color.parseColor("#1F2937"), // đen (mặc định)
            android.graphics.Color.parseColor("#EF4444"), // đỏ
            android.graphics.Color.parseColor("#F59E0B"), // cam
            android.graphics.Color.parseColor("#10B981"), // xanh lá
            android.graphics.Color.parseColor("#3B82F6"), // xanh dương
            android.graphics.Color.parseColor("#8B5CF6")  // tím
        )
        val names = arrayOf("Đen", "Đỏ", "Cam", "Xanh lá", "Xanh dương", "Tím")
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.notes_pick_color))
            .setItems(names) { _, which ->
                val editable = binding.etContent.text
                removeSpanFromRange(editable, ForegroundColorSpan::class.java, range) { true }
                editable.setSpan(ForegroundColorSpan(colors[which]), range.first, range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                hasUnsavedChanges = true
            }
            .show()
    }

    private fun showSizePicker() {
        val range = requireSelection() ?: return
        val sizes = floatArrayOf(0.8f, 1f, 1.4f, 1.8f)
        val names = arrayOf(
            getString(R.string.notes_size_small), getString(R.string.notes_size_normal),
            getString(R.string.notes_size_large), getString(R.string.notes_size_huge)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.notes_pick_size))
            .setItems(names) { _, which ->
                val editable = binding.etContent.text
                removeSpanFromRange(editable, RelativeSizeSpan::class.java, range) { true }
                editable.setSpan(RelativeSizeSpan(sizes[which]), range.first, range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                hasUnsavedChanges = true
            }
            .show()
    }

    /** Chèn ký hiệu gạch đầu dòng/checklist vào ĐẦU DÒNG hiện tại (nơi con trỏ đang đứng), không cần bôi đen. */
    private fun insertLinePrefix(prefix: String) {
        val editable = binding.etContent.text
        val cursor = binding.etContent.selectionStart.coerceAtLeast(0)
        val lineStart = editable.toString().lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        editable.insert(lineStart, prefix)
        binding.etContent.setSelection(cursor + prefix.length)
        hasUnsavedChanges = true
    }

    /**
     * Nén ảnh trước khi nhúng base64 vào HTML.
     * - Giới hạn cạnh dài nhất 800px (đủ nét trên điện thoại, nhẹ hơn 1080).
     * - Ưu tiên WebP (API 30+) vì nhỏ hơn JPEG ~25-40% với cùng chất lượng.
     * - Chất lượng adaptive: ảnh lớn nén mạnh hơn.
     * - Recycle bitmap để tránh OOM khi chèn nhiều ảnh.
     * - Báo kích thước sau nén để người dùng biết.
     */
    private fun insertImageAtCursor(uri: Uri) {
        Toast.makeText(this, "Đang xử lý ảnh…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val input = contentResolver.openInputStream(uri) ?: return@withContext null
                    val original = android.graphics.BitmapFactory.decodeStream(input)
                    input.close()
                    if (original == null) return@withContext null

                    val maxDim = 800
                    val scale = if (maxOf(original.width, original.height) > maxDim) {
                        maxDim.toFloat() / maxOf(original.width, original.height)
                    } else 1f

                    val resized = if (scale < 1f) {
                        android.graphics.Bitmap.createScaledBitmap(
                            original,
                            (original.width * scale).toInt().coerceAtLeast(1),
                            (original.height * scale).toInt().coerceAtLeast(1),
                            true
                        ).also {
                            if (it !== original) original.recycle()
                        }
                    } else original

                    // Adaptive quality: ảnh càng lớn (sau resize) thì nén mạnh hơn một chút
                    val quality = when {
                        resized.byteCount > 2_000_000 -> 65
                        resized.byteCount > 1_000_000 -> 72
                        else -> 78
                    }

                    val output = ByteArrayOutputStream()
                    val useWebp = android.os.Build.VERSION.SDK_INT >= 30
                    val format = if (useWebp) {
                        android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        android.graphics.Bitmap.CompressFormat.JPEG
                    }
                    val mime = if (useWebp) "image/webp" else "image/jpeg"
                    resized.compress(format, quality, output)
                    if (resized !== original && !resized.isRecycled) {
                        // original đã recycle ở trên nếu có resize
                    } else if (scale < 1f && !original.isRecycled) {
                        // đã xử lý
                    }
                    // An toàn: recycle resized nếu không còn cần
                    // (bitmap đã encode xong)
                    val bytes = output.toByteArray()
                    if (!resized.isRecycled) resized.recycle()

                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    Triple(base64, mime, bytes.size)
                } catch (e: Exception) {
                    null
                }
            }

            if (result == null) {
                Toast.makeText(this@NoteEditorActivity, getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val (base64, mime, sizeBytes) = result
            val dataUri = "data:$mime;base64,$base64"
            val drawable = decodeBase64Image(dataUri) ?: return@launch
            val editable = binding.etContent.text
            val cursor = binding.etContent.selectionStart.coerceAtLeast(0)
            val span = ImageSpan(drawable)
            // Marker ẩn để toHtml xuất đúng <img src="data:...">
            val marker = "\u200B[[IMG:$dataUri]]\u200B"
            editable.insert(cursor, " $marker ")
            editable.setSpan(span, cursor, cursor + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            binding.etContent.setSelection(cursor + marker.length + 2)
            hasUnsavedChanges = true

            val sizeKb = sizeBytes / 1024
            Toast.makeText(
                this@NoteEditorActivity,
                "Đã chèn ảnh (~${sizeKb} KB)",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ---------------- lưu / xóa ----------------

    private fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_save -> saveNote()
            R.id.action_delete -> confirmDelete()
        }
        return true
    }

    private fun saveNote() {
        val title = binding.etTitle.text.toString().trim().ifBlank { getString(R.string.notes_untitled) }
        val html = buildHtmlDocument(title, binding.etContent.text)
        lifecycleScope.launch {
            val target = existingFile ?: File(NoteFileStore.notesDir, NoteFileStore.suggestFileName(title))
            withContext(Dispatchers.IO) {
                NoteFileStore.notesDir.mkdirs()
                target.writeText(html)
            }
            existingFile = target
            hasUnsavedChanges = false
            binding.toolbar.menu.findItem(R.id.action_delete)?.isVisible = true
            binding.toolbar.title = title
            Toast.makeText(this@NoteEditorActivity, getString(R.string.notes_saved), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Convert Spannable (nội dung đang soạn) -> HTML thật. Xử lý thủ công thay vì dùng
     * Html.toHtml() có sẵn của Android vì hàm đó KHÔNG hỗ trợ export RelativeSizeSpan/ImageSpan
     * đúng cách (chỉ export được StyleSpan/UnderlineSpan/ForegroundColorSpan cơ bản) — quét từng
     * "đoạn span liên tục" (transition point) và tự bọc thẻ HTML tương ứng cho từng đoạn.
     */
    private fun buildHtmlDocument(title: String, content: Editable): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\">")
        sb.append("<title>${escapeHtml(title)}</title>")
        sb.append("<style>body{font-family:sans-serif;font-size:16px;line-height:1.5;padding:16px;} img{max-width:100%;height:auto;}</style>")
        sb.append("</head><body>")

        val text = content.toString()
        var i = 0
        while (i < text.length) {
            // Placeholder ảnh: nhảy qua nguyên marker, xuất thẳng thẻ <img>, bỏ qua ký tự
            // placeholder hiển thị của ImageSpan (không xuất ra HTML, chỉ dùng để hiện trong lúc soạn).
            val markerMatch = Regex("\u200B\\[\\[IMG:(.*?)]]\u200B").find(text, i)
            if (markerMatch != null && markerMatch.range.first == i) {
                sb.append("<br><img src=\"${markerMatch.groupValues[1]}\"><br>")
                i = markerMatch.range.last + 1
                continue
            }
            val c = text[i]
            if (c == '\n') {
                sb.append("<br>")
                i++
                continue
            }
            // Đoạn liên tục có CÙNG bộ span (đậm/nghiêng/gạch chân/màu/cỡ) -> gộp lại xuất 1 lần
            // thay vì mỗi ký tự 1 thẻ, để HTML xuất ra gọn và dễ đọc lại đúng khi mở file sau này.
            var j = i + 1
            while (j < text.length && text[j] != '\n' && sameSpansAt(content, i, j) &&
                Regex("\u200B\\[\\[IMG:").find(text, j)?.range?.first != j
            ) j++
            val segment = text.substring(i, j)
            sb.append(wrapSegmentWithTags(content, i, segment))
            i = j
        }

        sb.append("</body></html>")
        return sb.toString()
    }

    private fun sameSpansAt(content: Editable, posA: Int, posB: Int): Boolean {
        fun spanSetAt(pos: Int): Set<String> {
            val out = mutableSetOf<String>()
            content.getSpans<StyleSpan>(pos, pos + 1).forEach { out.add("style:${it.style}") }
            content.getSpans<UnderlineSpan>(pos, pos + 1).forEach { out.add("u") }
            content.getSpans<ForegroundColorSpan>(pos, pos + 1).forEach { out.add("color:${it.foregroundColor}") }
            content.getSpans<RelativeSizeSpan>(pos, pos + 1).forEach { out.add("size:${it.sizeChange}") }
            return out
        }
        return spanSetAt(posA) == spanSetAt(posB)
    }

    private fun wrapSegmentWithTags(content: Editable, pos: Int, segment: String): String {
        var text = escapeHtml(segment)
        content.getSpans<RelativeSizeSpan>(pos, pos + 1).firstOrNull()?.let {
            text = "<span style=\"font-size:${(it.sizeChange * 16).toInt()}px\">$text</span>"
        }
        content.getSpans<ForegroundColorSpan>(pos, pos + 1).firstOrNull()?.let {
            val hex = String.format("#%06X", 0xFFFFFF and it.foregroundColor)
            text = "<span style=\"color:$hex\">$text</span>"
        }
        content.getSpans<StyleSpan>(pos, pos + 1).forEach {
            text = when (it.style) {
                Typeface.BOLD -> "<b>$text</b>"
                Typeface.ITALIC -> "<i>$text</i>"
                Typeface.BOLD_ITALIC -> "<b><i>$text</i></b>"
                else -> text
            }
        }
        if (content.getSpans<UnderlineSpan>(pos, pos + 1).isNotEmpty()) text = "<u>$text</u>"
        return text
    }

    private fun escapeHtml(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun confirmDelete() {
        val file = existingFile ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.notes_delete_confirm_title))
            .setMessage(getString(R.string.notes_delete_confirm_message))
            .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { file.delete() }
                    finish()
                    ActivityTransitions.backward(this@NoteEditorActivity)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun confirmBackIfNeeded() {
        if (!hasUnsavedChanges) {
            finish()
            ActivityTransitions.backward(this)
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.notes_saved))
            .setMessage(getString(R.string.notes_unsaved_message))
            .setPositiveButton(getString(R.string.save)) { _, _ -> saveNote(); finish(); ActivityTransitions.backward(this) }
            .setNegativeButton(getString(R.string.notes_discard)) { _, _ -> finish(); ActivityTransitions.backward(this) }
            .show()
    }

    /** Tự động lưu nền khi rời màn hình (không hiện toast để khỏi làm phiền). */
    override fun onPause() {
        super.onPause()
        if (hasUnsavedChanges && (existingFile != null || binding.etContent.text?.isNotBlank() == true || binding.etTitle.text?.isNotBlank() == true)) {
            val title = binding.etTitle.text.toString().trim().ifBlank { getString(R.string.notes_untitled) }
            val html = buildHtmlDocument(title, binding.etContent.text)
            val target = existingFile ?: File(NoteFileStore.notesDir, NoteFileStore.suggestFileName(title)).also { existingFile = it }
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        NoteFileStore.notesDir.mkdirs()
                        target.writeText(html)
                    }
                    hasUnsavedChanges = false
                } catch (_: Exception) { /* bỏ qua, lần sau lưu lại */ }
            }
        }
    }

    companion object {
        const val EXTRA_FILE_PATH = "extra_note_file_path"
    }
}
