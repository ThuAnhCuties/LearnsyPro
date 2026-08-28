package com.learnsypro.app.filemanager.cloud

import com.learnsypro.app.filemanager.model.RemoteFile
import java.io.File

/** Dung lượng tài khoản cloud: đã dùng / tổng, tính theo byte. null nếu provider không cung cấp thông tin này. */
data class CloudStorageQuota(val usedBytes: Long, val totalBytes: Long)

/**
 * Hợp đồng chung cho mọi dịch vụ cloud (Google Drive, Dropbox, Box).
 * Mỗi provider implement lớp này bằng REST API riêng của họ, nhưng UI (CloudBrowserActivity)
 * chỉ cần thao tác qua interface này, không quan tâm chi tiết provider.
 */
interface CloudFileService {

    /** true nếu đã có access token hợp lệ (đã liên kết tài khoản). */
    suspend fun isLinked(): Boolean

    /** Liệt kê file/thư mục trong 1 folder. folderId rỗng = thư mục gốc. */
    suspend fun listFiles(folderId: String): Result<List<RemoteFile>>

    /** Tải file lên thư mục cha parentId. */
    suspend fun uploadFile(localFile: File, parentId: String): Result<Unit>

    /** Tải file về máy theo cloudFileId. */
    suspend fun downloadFile(cloudFileId: String, destination: File): Result<Unit>

    /** Xóa file/thư mục theo id. */
    suspend fun deleteFile(cloudFileId: String): Result<Unit>

    /** Tạo thư mục mới trong parentId. */
    suspend fun createFolder(name: String, parentId: String): Result<Unit>

    /** Ngắt liên kết tài khoản (xóa token đã lưu). */
    fun unlink()

    /** Lấy dung lượng đã dùng/tổng của tài khoản, hiển thị dưới dạng thanh mini có màu khi vào kết nối. */
    suspend fun getStorageQuota(): Result<CloudStorageQuota>

    /**
     * Đổi tên file/thư mục theo id. Mỗi provider có cách gọi riêng (xem từng implementation),
     * nhưng UI chỉ cần gọi qua hàm chung này — giống các thao tác khác trong interface.
     */
    suspend fun renameFile(cloudFileId: String, newName: String): Result<Unit>

    /**
     * Tạo (hoặc lấy lại nếu đã có) liên kết chia sẻ công khai dạng xem-được (view-only) cho 1
     * file/thư mục, dùng để đưa vào Android share sheet — KHÔNG tải file về máy trước, khác
     * với share nội bộ của Bộ nhớ trong (share thẳng nội dung file qua FileProvider).
     */
    suspend fun getShareLink(cloudFileId: String): Result<String>

    /**
     * URL tải trực tiếp ảnh/video thu nhỏ + header cần gửi kèm (thường là "Authorization:
     * Bearer <token>") để hiện thumbnail thật trong danh sách, giống thumbnail thật đã có ở Bộ
     * nhớ trong. Khác DLNA (URL mở thẳng không cần xác thực), CẢ 3 provider cloud đều yêu cầu
     * access token hợp lệ mới tải được nội dung file thật — vì vậy trả về cặp (url, headers)
     * thay vì chỉ url suông, để Coil (RemoteFileAdapter) gắn đúng header khi load ảnh.
     * Trả về null nếu không phải ảnh/video hoặc chưa đăng nhập.
     */
    suspend fun getThumbnailRequest(file: RemoteFile): Pair<String, Map<String, String>>?
}
