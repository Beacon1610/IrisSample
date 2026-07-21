# Báo cáo phân tích và sửa lỗi dự án IrisSample-IriAegis-26T

**Vai trò đánh giá:** Senior Android Developer / Software Architect  
**Phạm vi:** source code trong ZIP người dùng cung cấp, toàn bộ ba module Gradle, manifest, resource XML, database, luồng đăng nhập/điểm danh/thống kê/xuất/backup, Git và khả năng build trong môi trường hiện tại.  
**Branch thực hiện:** `codex/audit-critical-high-fixes`  
**Mốc source gốc đã bảo toàn:** commit `6c91252` (`chore: preserve uploaded phase 3 working state`).

> Giới hạn kiểm chứng: môi trường phân tích không có Android SDK và không tải được Gradle distribution do không có kết nối tới `services.gradle.org`. Vì vậy chưa thể biên dịch APK hoặc chạy trên thiết bị IriTech. Các kết luận “đã sửa” dưới đây dựa trên đọc code, kiểm tra tĩnh, fixture SQLite, test độc lập cho hàm mật khẩu/FaceMatcher và kiểm tra cấu trúc XLSX; không được hiểu là đã kiểm thử end-to-end trên thiết bị thật.

---

# Phần 1 – Tóm tắt ứng dụng hiện tại

## 1.1 Cấu trúc dự án và kiến trúc tổng thể

`settings.gradle` khai báo ba module:

1. `app`: ứng dụng Android chính. Phần lớn code là Java Activity + XML layout; quản lý tài khoản, môn học, sinh viên, điểm danh, Face ID, thống kê, CSV/XLSX, email và backup/restore.
2. `iris`: Android Library chứa lớp tích hợp IriTech như `CaptureActivity`, `CaptureFragment`, `IriController`, cấu hình license, gallery mống mắt và các wrapper native.
3. `iricapture+`: module đóng gói trực tiếp file `iricapture+.aar`.

Module `app` có `compileSdk/targetSdk 34`, `minSdk 24`, Java 17. Module `iris` dùng Java 8, `compileSdk 33`, `minSdk 19`. Ứng dụng áp dụng thêm Kotlin/Compose plugin nhưng màn hình nghiệp vụ chính vẫn là Activity/XML; phần Compose hiện chủ yếu là các file theme, nên plugin/dependency Compose làm tăng độ phức tạp build mà chưa mang lại giá trị rõ ràng.

Kiến trúc hiện tại là **Activity-centric**:

- Activity trực tiếp đọc/ghi `SQLiteDatabase` hoặc gọi `DatabaseHelper`.
- `DatabaseHelper.java` vừa định nghĩa schema, migration, authentication, CRUD, attendance, thống kê, Face ID và email recipient.
- Một số phần đã được tách tốt hơn: `AttendanceExportRepository`, `AttendanceExportService`, `BackupImportManager`, `DatabaseValidator`, model/adapter.
- Chưa có ViewModel/Repository thống nhất cho toàn ứng dụng, chưa có lớp domain/use-case cho luồng điểm danh.

Quy mô source đã rà soát: 53 file Java trong `app`, 85 XML resource, 26 layout. Các class lớn nhất gồm `DatabaseHelper` (~74 KB), `BackupRestoreHelper` (~54 KB), `AttendanceManagementActivity` (~49 KB), `StudentListActivity` (~48 KB), `MainActivity` (~42 KB).

## 1.2 Gradle, manifest và dependency

`app/build.gradle`:

- Ký cả debug/release bằng `app/keystore/debug.keystore_ubioxPro`, alias/password hard-code `android` (`app/build.gradle:21-27`). Đây có thể liên quan ràng buộc license/chữ ký IriTech nên chưa tự ý đổi.
- Release bật minify.
- ABI chỉ gồm `armeabi-v7a`, `arm64-v8a`.
- Tích hợp CameraX, ML Kit Face Detection, TensorFlow Lite, Gson, JavaMail, AndroidX Biometric, GCM cũ và hai module iris.
- Sau sửa, release không còn đóng gói Brevo secret; debug chỉ bật SMTP trực tiếp khi developer chủ động cấu hình (`app/build.gradle:41-73`).

`AndroidManifest.xml`:

- Launcher là `LoginActivity`.
- Deep link custom scheme `irissample://login` và `irissample://reset-password` mở Activity exported.
- `FileProvider` dùng `${applicationId}.fileprovider`.
- `allowBackup=false` sau sửa.
- Manifest vẫn xin nhiều quyền cũ hoặc rộng: external storage, phone state, accounts, Wi-Fi/network mutation, boot, task list, owner data, Bluetooth cũ, cùng quyền riêng của SDK IriTech (`AndroidManifest.xml:8-35`).

## 1.3 Cấu trúc SQLite và quan hệ dữ liệu

Database: `attendance.db`, version hiện tại **25** (`DatabaseHelper.java:21-24`).

### Bảng `admin`

- PK: `admin_id`.
- `email` unique/not-null.
- Có password, thông tin hồ sơ, role, admin code, reset token/expiry, cờ first login, Face embedding, cờ Face/Iris.

### Bảng `subjects`

- PK: `subject_id`.
- FK `created_by` và `instructor_id` tới `admin`, `ON DELETE SET NULL`.
- Có tên môn, ca học, status, thời gian tạo.

### Bảng `students`

- PK: `student_id`.
- Có họ tên, email, điện thoại, password, face vector, photo path.
- Nhiều cột quan trọng vẫn nullable và email chưa unique.

### Bảng `enrollments`

- Composite PK `(student_id, subject_id)`.
- FK tới `students` và `subjects`.
- Có `enrollment_status`; sau migration v25 chuẩn hóa thành `ACTIVE` hoặc giữ `INACTIVE` (`DatabaseHelper.java:513-526`).
- Chưa có ngày bắt đầu/kết thúc enrollment.

### Bảng `checkin_history`

- PK auto-increment `checkin_id`.
- FK student/subject, ngày và giờ check-in.
- Unique index `(student_id, subject_id, checkin_date)` ngăn một sinh viên có nhiều bản ghi trong cùng môn/ngày (`DatabaseHelper.java:529-536`).
- Chưa lưu phương thức điểm danh, trạng thái snapshot, note, người thao tác hoặc audit metadata.

### Bảng `class_sessions`

- PK auto-increment `session_id`.
- Unique `(subject_id, session_date)`.
- Có `late_cutoff_time` và `created_at`.
- Chưa có giờ bắt đầu, giờ kết thúc, trạng thái “đang diễn ra/đã đóng/hủy”.

### Bảng `email_recipients`

- Unique `(subject_id, email_address)`.
- FK subject `ON DELETE CASCADE`.

Foreign key được bật trong `onOpen()` (`DatabaseHelper.java:289-294`). Migration restore chạy trong transaction, tạo bảng/cột còn thiếu, seed session từ check-in cũ, dọn trùng, chuẩn hóa enrollment và tạo index (`DatabaseHelper.java:301-350`).

## 1.4 Luồng đăng nhập

1. `LoginActivity` nhận email/password và gọi `DatabaseHelper.authenticateUser()`.
2. Sau sửa, password mới dùng PBKDF2 có salt; hash SHA-256 cũ được tự nâng cấp khi đăng nhập thành công (`DatabaseHelper.java:869-903`, `PasswordHasher.java`).
3. Nếu first login, ứng dụng chuyển tới cập nhật hồ sơ.
4. Nếu tài khoản chưa có Face ID, chuyển tới đăng ký; nếu đã có, mở `AdminFaceVerifyActivity`.
5. Có đăng nhập bằng mống mắt qua IriTech SDK và nhận kết quả từ Activity SDK.
6. Có Android Biometric/device credential qua `BiometricAuthHelper` cho một số thao tác xác thực lại.
7. Reset password dùng token ngẫu nhiên có thời hạn, nhưng token vẫn được lưu dạng rõ và truyền qua custom scheme.

## 1.5 Luồng quản lý sinh viên/môn học

- `SubjectListActivity`/`AddSubjectActivity`: tạo, sửa, phân công môn học.
- `StudentListActivity`: tải enrollment của môn hiện tại, tìm kiếm/lọc, thêm/sửa/xóa, mở điểm danh iris/face/mock và thống kê.
- `AddStudentActivity`: tạo sinh viên, lưu Face embedding/photo, thêm enrollment. Sau sửa, thêm sinh viên + enrollment chạy trong transaction; sinh viên đã tồn tại có thể được gán vào môn mà không bắt buộc quét lại mặt.
- `ImportCsvActivity`: sau sửa dùng SAF `ACTION_OPEN_DOCUMENT`, executor nền, parser hỗ trợ chuỗi CSV quoted, giới hạn 10.000 dòng và transaction toàn file.

## 1.6 Luồng điểm danh hiện tại

Luồng ghi thống nhất sau sửa nằm ở `DatabaseHelper.recordDailyAttendance()` (`DatabaseHelper.java:353-428`):

1. Validate student/subject/date/time theo định dạng chặt.
2. Mở transaction.
3. Kiểm tra enrollment `ACTIVE`.
4. Tạo hoặc lấy `class_session` tương ứng.
5. Cập nhật mốc đi trễ nếu có.
6. Insert check-in bằng `CONFLICT_IGNORE`.
7. Trả kết quả rõ: `SUCCESS`, `DUPLICATE`, `NOT_ENROLLED`, `INVALID_INPUT`, `DATABASE_ERROR`.
8. Commit/rollback transaction.

Ba đường ghi chính đã chuyển sang API này:

- Điểm danh thật từ `StudentListActivity`.
- Mock check-in từ `MockCheckinActivity`.
- Import lịch sử trong `AttendanceManagementActivity`.

Trạng thái không được lưu trực tiếp trong `checkin_history`; khi đọc, code so thời gian check-in đầu tiên với `late_cutoff_time` để suy ra `Có mặt` hoặc `Đi trễ`. `Vắng` được suy ra nếu có `class_session` nhưng không có check-in.

### Mức đáp ứng với luồng chuẩn 10 bước

| Bước chuẩn | Hiện trạng |
|---|---|
| Chọn môn học | Có |
| Xác định buổi học hiện tại | Có session theo ngày, nhưng chưa có giờ bắt đầu/kết thúc/trạng thái |
| Xác thực sinh viên | Có Iris/Face/password/mock |
| Kiểm tra thuộc môn | Đã có trong transaction sau sửa |
| Kiểm tra đã điểm danh | Có unique index + kết quả `DUPLICATE` |
| So với khoảng đi trễ | Có một mốc cutoff; chưa có “khoảng đầu/cuối” đầy đủ |
| Lưu Có mặt/Đi trễ | Lưu check-in, trạng thái được suy ra khi đọc |
| Tự xác định Vắng sau kết thúc | **Chưa đầy đủ**; hiện chỉ cần session tồn tại là người chưa check-in bị xem là vắng |
| Hiển thị kết quả | Có Toast/dialog, đã phân biệt nguyên nhân tốt hơn |
| Audit log | **Chưa có** |

## 1.7 Luồng mống mắt

- Module `iris` bao bọc Capture/Enroll/Verify/Identify, IriTech license và native SDK.
- `StudentListActivity`, `LoginActivity`, `RegisterAdminActivity`, `ForgotPasswordActivity`, `ProfileActivity` mở `CaptureActivity` bằng intent/action của SDK.
- Gallery/template mặc định vẫn dùng đường dẫn external storage `/sdcard/iritech` (`iris/Constants.java:11`, `iris/IriController.java:184,277`; app cũng tham chiếu đường dẫn này).
- Kết quả identify trong `StudentListActivity` đang trích student ID từ chuỗi mô tả `"ID: ..., distance:"` thay vì extra có cấu trúc (`StudentListActivity.java:1019-1080`). Đây là điểm tích hợp mong manh nhưng chưa sửa vì cần xác nhận contract thực tế của SDK trên thiết bị.

## 1.8 Luồng Face ID

- CameraX lấy frame, ML Kit xác định mặt, TFLite tạo embedding 192 chiều.
- `FaceMatcher` đọc embedding JSON từ SQLite và tính khoảng cách Euclidean.
- `FaceRecognitionActivity` hỗ trợ:
  - 1:1 nếu có `target_student_id`.
  - 1:N trong môn học nếu `target_student_id == null` sau sửa (`FaceRecognitionActivity.java:83-89,340-360`).
- Ngưỡng auto accept `< 0.8`; khoảng `0.8–1.0` cho phép xác nhận thủ công (`FaceRecognitionActivity.java:49-50,381-402`).
- Chưa có liveness/anti-spoof; embedding và ảnh vẫn không được mã hóa ở tầng ứng dụng.

## 1.9 Thống kê

- Màn hình có hai chế độ: theo ngày và theo sinh viên.
- Theo ngày: chọn một ngày, lọc Có mặt/Đi trễ/Vắng, tìm sinh viên, biểu đồ tròn.
- Theo sinh viên: tổng buổi, có mặt, đi trễ, vắng, tỷ lệ; nhấn để xem chi tiết từng buổi.
- Công thức hiện tại:
  - `attendanceRate = presentCount * 100 / totalSessions`.
  - `lateRate = lateCount * 100 / totalSessions`.
  - `presentCount` bao gồm cả đi trễ, còn `lateCount` là tập con (`StudentAttendanceStats.java:26-35`).
- Công thức phù hợp với cách hiểu “tham gia = có mặt hoặc đi trễ”. Sai lệch còn lại đến từ mô hình session/enrollment, không phải phép chia.
- Chưa có lọc khoảng ngày bắt đầu–kết thúc và chưa có sort UI theo tỷ lệ/vắng.

## 1.10 Xuất CSV/XLSX và email

- `AttendanceExportRepository` lấy subject, session, toàn bộ sinh viên enrollment ACTIVE và matrix check-in.
- Sheet tổng hợp tính tổng buổi/có mặt/đi trễ/vắng/tỷ lệ.
- `AttendanceExcelExporter` tự tạo OOXML ZIP. File fixture đã mở thành công bằng `openpyxl`; XML escaping hoạt động.
- Tên sheet hiện tại là `Thông tin sinh viên` và `Chi tiết điểm danh`.
- Sheet 2 hiện là **ma trận sinh viên × ngày**, không phải mỗi dòng một sự kiện như yêu cầu mục E. Schema hiện tại cũng chưa có `method`, `note`, ca học snapshot.
- SAF `ACTION_CREATE_DOCUMENT` được dùng để người dùng chọn tên/nơi lưu; việc ghi đè do document provider xử lý.
- Sau sửa, email nhiều người nhận dùng BCC, tránh lộ email giữa các người nhận (`EmailService.java:314-341`).
- Gửi SMTP trực tiếp từ APK đã bị tắt ở release; production cần backend/mail relay.

## 1.11 Backup/restore

Đây là phần được triển khai tương đối chắc chắn:

- Export/restore qua SAF.
- Archive có mã hóa.
- Import vào staging, giới hạn kích thước/tổng entry, chống Zip Slip bằng canonical path.
- Kiểm tra SQLite header, `PRAGMA integrity_check`, bảng bắt buộc, DB version và tài khoản Super Admin trước khi thay database.
- Migration staging trước khi replace live DB.
- Có safety copy và rollback nếu thay thế thất bại.

Cần kiểm thử thiết bị thật thêm cho tính nhất quán giữa SQLite, ảnh và gallery/template Iris.

## 1.12 Git và build

Git có các branch chính:

- `main`
- `feature/admin-face-id-login`
- `feature/import-export-database-saf`
- `codex/attendance-statistics`
- `codex/phase-1-cleanup`
- `codex/phase-2-export-refactor`
- `codex/phase-3-excel-export`
- branch audit mới `codex/audit-critical-high-fixes`

Source ZIP có nhiều thay đổi Phase 3 chưa commit; chúng đã được bảo toàn nguyên trạng ở commit `6c91252` trước khi sửa.

Build `:app:assembleDebug` chưa chạy được vì:

- Không có `ANDROID_HOME`/`ANDROID_SDK_ROOT` hợp lệ.
- `local.properties` trỏ tới đường dẫn Windows `D:\AndroidSDK` không tồn tại trong môi trường Linux.
- Gradle wrapper 8.13 chưa có cache và không tải được từ `services.gradle.org` (`UnknownHostException`).

Do đó chưa kiểm chứng được compile dependency/native IriTech, manifest merge, R8 hoặc APK runtime.

---

# Phần 2 – Danh sách vấn đề

| STT | Mức độ | Vấn đề | File/class liên quan | Nguyên nhân / tình huống / ảnh hưởng | Hướng khắc phục | Trạng thái |
|---:|---|---|---|---|---|---|
| 1 | **Critical** | Khóa SMTP thật từng nằm trong `local.properties` và được đưa vào `BuildConfig` | `app/build.gradle`, `EmailService` | Secret trong APK có thể bị decompile; kẻ khác có thể gửi mail bằng tài khoản Brevo, gây spam/chi phí/khóa tài khoản | Thu hồi/rotate key ngay; release không bao giờ nhúng secret; chuyển gửi mail sang backend | **Code đã sửa; vẫn phải rotate key ngoài hệ thống** |
| 2 | **Critical** | Mật khẩu sinh viên dạng rõ và admin dùng SHA-256 không salt | `DatabaseHelper.authenticateUser`, `verifyStudentPassword`, `PasswordResetHelper`, `AddStudentActivity`, import CSV | Nếu DB/backup bị lộ, mật khẩu có thể đọc hoặc crack nhanh; sinh viên dùng lại mật khẩu nơi khác làm tăng thiệt hại | PBKDF2 + random salt + constant-time compare; tự nâng cấp legacy sau login thành công | **Đã sửa** (`c57a11e`) |
| 3 | **High** | Trạng thái enrollment tự mâu thuẫn: thêm/import ghi `Not Enrolled` nhưng query vẫn coi là thuộc môn | `AddStudentActivity`, `ImportCsvActivity`, `DatabaseHelper` | Sau khi bắt đầu lọc status, sinh viên biến mất hoặc thống kê/export sai | Chuẩn hóa `ACTIVE/INACTIVE`, migration v25, index và filter thống nhất | **Đã sửa** (`ee5f5a3`) |
| 4 | **High** | Ghi session/cutoff/check-in trước đây không atomic; UI có thể báo “điểm danh lại” nhưng DB `CONFLICT_IGNORE` không cập nhật | `DatabaseHelper`, `StudentListActivity`, `MockCheckinActivity`, `AttendanceManagementActivity` | Crash/lỗi giữa các bước để lại session không check-in; người dùng hiểu sai kết quả; có nguy cơ logic trùng giữa các luồng | Một transaction + enum kết quả + unique index là nguồn sự thật duy nhất | **Đã sửa** (`ee5f5a3`) |
| 5 | **High** | Face ID 1:N tổng quát bị đóng ngay khi không có `target_student_id` | `FaceRecognitionActivity` | Nút điểm danh Face ID tổng quát truyền null, Activity cũ từ chối nên chức năng không hoạt động | Null = 1:N trong môn; non-null = 1:1; skip embedding JSON lỗi; đóng model/resource | **Đã sửa** (`0f4ae8c`) |
| 6 | **High** | Email báo cáo gửi tất cả địa chỉ ở trường TO, làm lộ email giữa người nhận | `EmailService.sendAttendanceReportEmail` | Phụ huynh/người nhận nhìn thấy email của nhau; vi phạm riêng tư | TO là sender; danh sách thực dùng BCC; validate bỏ địa chỉ rỗng | **Đã sửa** (`5e792d2`) |
| 7 | **High** | Thống kê/export không biết sinh viên bắt đầu hoặc kết thúc học từ ngày nào | `enrollments`, `getStudentAttendanceStatsBySubject`, `AttendanceExportRepository` | Sinh viên mới thêm hôm nay có thể bị tính vắng cho mọi session cũ; sinh viên nghỉ môn vẫn có thể bị tính về sau | Thêm `enrolled_at`, `left_at` hoặc bảng enrollment history; migration và query theo khoảng hiệu lực | Chưa sửa, cần migration thiết kế kỹ |
| 8 | **High** | Vắng mặt được suy ra ngay khi session tồn tại, chưa chờ buổi học kết thúc | `class_sessions`, `getAttendanceRecordsBySubjectAndDate`, UI thống kê | Khi buổi học đang diễn ra, sinh viên chưa check-in đã bị hiển thị Vắng; không có “chưa điểm danh/chưa kết thúc” | Thêm start/end/status (`SCHEDULED/OPEN/CLOSED/CANCELLED`); chỉ chốt vắng khi CLOSED | Chưa sửa |
| 9 | **High** | Face embedding và đường dẫn ảnh lưu dạng rõ trong SQLite | `students.face_vector`, `admin_face_embedding`, `DatabaseHelper.updateAdminFaceId`, face classes | Lộ DB/backup làm lộ dữ liệu sinh trắc học không thể thay đổi như mật khẩu | Mã hóa trường bằng key trong Android Keystore; tách biometric store; khóa backup và quyền truy cập | Chưa sửa |
| 10 | **High** | Face ID không có liveness/anti-spoof; match yếu vẫn có nút xác nhận thủ công | `FaceRecognitionActivity:49-50,381-402` | Ảnh/video có thể đánh lừa; người vận hành có thể xác nhận nhầm match khoảng cách cao | Liveness challenge, multi-frame consistency, giảm/loại manual accept cho attendance thật, log override | Chưa sửa |
| 11 | **High** | Iris gallery/license/template dùng `/sdcard/iritech` và phụ thuộc quyền storage cũ | `iris/Constants.java:11`, `iris/IriController.java:184,277`, `StudentListActivity:725-744`, các Activity Iris | Android mới áp dụng scoped storage; `WRITE_EXTERNAL_STORAGE` không còn hợp lệ như trước, có thể chặn capture hoặc làm dữ liệu biometric nằm công khai | Xác nhận SDK vendor có hỗ trợ app-private/SAF; chỉ thay đường dẫn khi test thiết bị và license; không tự sửa SDK hiện tại | Chưa sửa vì rủi ro IriTech |
| 12 | **High** | Reset password dùng custom URI scheme và token dạng rõ trong URL/DB | `AndroidManifest.xml:105-121`, `PasswordResetHelper`, `ResetPasswordActivity` | App khác có thể đăng ký cùng scheme để chặn link; token có thể xuất hiện trong log/history | Verified HTTPS App Link, hash token ở DB, token one-time, invalidate session, giới hạn thử | Chưa sửa |
| 13 | **High** | Debug keystore/password được hard-code và dùng cả release | `app/build.gradle:21-27,57-65`, `app/keystore/debug.keystore_ubioxPro` | Bất kỳ ai có source có thể ký bản giống release; mất chuỗi tin cậy update | Xác nhận ràng buộc certificate với IriTech trước; sau đó dùng release keystore riêng qua CI secret/env, không commit | Chưa sửa do có thể ảnh hưởng license SDK |
| 14 | **High** | Nhiều DB query, PBKDF2, export XLSX/CSV và import admin chạy trên UI thread | `LoginActivity`, `AttendanceManagementActivity:725-826`, `AdminListActivity:173-253`, nhiều Activity | Database lớn/thiết bị yếu gây ANR, lag, ProgressDialog treo; lifecycle destroy vẫn callback | Executor/Coroutine tương đương Java, Repository async, lifecycle guard/ViewModel/WorkManager cho tác vụ dài | Import sinh viên đã sửa; phần còn lại chưa |
| 15 | **High** | Sheet chi tiết XLSX chưa đúng cấu trúc nghiệp vụ được yêu cầu | `AttendanceExcelExporter:98-140`, schema check-in | Hiện là ma trận; thiếu môn/ca/method/note theo từng dòng; không thể audit chính xác nguồn check-in | Migration thêm method/status/note/session id; exporter tạo mỗi `(session, student)` một dòng | Chưa sửa |
| 16 | **High** | Kết quả Iris identify được parse từ chuỗi hiển thị | `StudentListActivity:1019-1080` | SDK đổi wording/locale hoặc format distance làm không lấy được ID/nhận sai ID | Dùng extra/Parcelable có cấu trúc từ SDK; nếu SDK không có, tạo adapter parser có test và fail-closed | Chưa sửa, cần test SDK thật |
| 17 | **High** | Manifest xin nhiều quyền cũ/rộng; một số quyền không hợp lệ hoặc không dành cho app thường | `AndroidManifest.xml:8-35` | Tăng bề mặt tấn công, bị Play/Android từ chối, permission flow khó đoán; `READ_MEDIA_STORAGE` không phải quyền public hợp lệ | Lập permission matrix theo từng chức năng/API; bỏ quyền không dùng; dùng Bluetooth runtime mới; giữ quyền vendor tối thiểu | Chưa sửa vì phải kiểm thử thiết bị |
| 18 | **Medium** | Các import CSV Admin/attendance còn parser `split(",")`, không transaction đầy đủ và xử lý trên UI thread | `AdminListActivity:173-253`, `AttendanceManagementActivity` import | Tên có dấu phẩy làm lệch cột; lỗi giữa file tạo dữ liệu nửa chừng; stream/error handling chưa đồng nhất | Dùng parser chung đã triển khai ở `ImportCsvActivity`, transaction, executor, giới hạn dòng/file | Chưa sửa toàn bộ |
| 19 | **Medium** | Reset/login không có rate limit, lockout hoặc audit đăng nhập | `LoginActivity`, `DatabaseHelper.authenticateUser`, Face/Iris login | Brute force local; khó điều tra ai đăng nhập/thất bại | Throttle tăng dần, attempt counter/time, audit security event; không log password/embedding | Chưa sửa |
| 20 | **Medium** | Session người dùng dựa nhiều vào Intent extras/SharedPreferences và Activity flow | `LoginActivity`, `MainActivity`, `BiometricAuthHelper` | Process death, deep-link trực tiếp hoặc state cũ có thể làm role/identity không nhất quán | `SessionManager` trung tâm, validate lại admin từ DB, clear on logout/process restore, authorization trong repository | Chưa sửa |
| 21 | **Medium** | Database schema có constraint yếu | `students`, `checkin_history`, `enrollments` creation (`DatabaseHelper.java:141-170`) | Null/blank student name/date/time hoặc orphan semantics có thể lọt vào; xóa student/subject chưa có policy cascade rõ cho enrollment/check-in | Migration rebuild bảng với NOT NULL/CHECK/FK action; validate trước migration; transaction | Chưa sửa |
| 22 | **Medium** | Dọn duplicate migration giữ `MIN(checkin_id)`, không chắc là thời gian check-in sớm nhất | `DatabaseHelper.removeDuplicateCheckins:490-511` | Dữ liệu import không theo thứ tự có thể giữ bản ghi muộn, làm sai trạng thái late | Chọn bản ghi theo time hợp lệ sớm nhất rồi ID; log bản ghi bị loại; backup trước migration | Chưa sửa |
| 23 | **Medium** | Log chứa email, đường dẫn file, kết quả Iris/license/match metadata | `EmailService`, `LoginActivity`, `ProfileActivity`, `RegisterAdminActivity`, `BackupRestoreHelper`, `EncryptionHelper` | Logcat/bug report có thể lộ PII và cấu trúc dữ liệu | Logging facade, redaction, tắt debug log release, không log token/email/path đầy đủ | Chưa sửa |
| 24 | **Medium** | Direct SMTP từ app vẫn tồn tại cho debug | `EmailService`, `app/build.gradle` | Dù release đã tắt, debug APK cấu hình secret vẫn có thể bị chia sẻ và trích xuất | Chỉ dùng local testing; ưu tiên backend cho cả debug/staging; cảnh báo CI | Đã giảm rủi ro, chưa loại bỏ hoàn toàn |
| 25 | **Medium** | HTML email chèn trực tiếp subject/name do người dùng nhập | `AttendanceManagementActivity` xây HTML, `EmailService` | Ký tự HTML có thể phá layout hoặc chèn nội dung giả trong email | Escape HTML cho mọi field động; template builder riêng | Chưa sửa |
| 26 | **Medium** | Date/time lưu TEXT `dd/MM/yyyy`, so sánh bằng `substr`/chuỗi | `class_sessions`, `checkin_history`, nhiều query/export | Query range phức tạp, dữ liệu sai format gây sort sai; timezone không được lưu | Migration dần sang epoch/ISO-8601 UTC + timezone/locale display ở UI; vẫn giữ adapter legacy | Chưa sửa |
| 27 | **Medium** | Activity và `DatabaseHelper` quá lớn, code nghiệp vụ/UI/SQL trộn nhau | Các class lớn nêu trên | Khó test, dễ sửa một chỗ hỏng chỗ khác, nhiều logic lặp | Tách Repository → UseCase/Service → UI từng chức năng; không viết lại một lần | Chưa sửa |
| 28 | **Medium** | Dependency/build config pha trộn cũ-mới, AndroidX + support legacy, Compose plugin gần như không dùng | `app/build.gradle`, `iris/build.gradle` | Có nguy cơ duplicate/transitive conflict, thời gian build/R8 tăng; chưa xác minh vì build bị chặn | Sau khi build được, chạy dependencyInsight/lint; pin version; bỏ Compose nếu không dùng; không đổi SDK vendor tùy tiện | Chưa sửa |
| 29 | **Low** | Comment/version/tên method chưa nhất quán; một số cursor đóng thủ công và formatting khó đọc | `DatabaseHelper`, Activity khác | Tăng chi phí bảo trì, dễ quên close trong thay đổi tương lai | Formatter, try-with-resources, naming convention, cập nhật comment version | Một phần đã cải thiện |
| 30 | **Low** | Thiếu test tự động chính thức trong project | `app/src/test`, `androidTest` gần như trống | Regression attendance/export/migration/face matcher khó phát hiện | Thêm JVM tests cho model/export/parser/hash; instrumented DB migration; UI smoke test | Chưa sửa; đã có test fixture ngoài Gradle trong audit |

---

# Phần 3 – Đề xuất cải thiện

## 3.1 Cần sửa ngay

1. **Rotate Brevo SMTP credential đã lộ**; không chỉ đổi code.
2. Merge các commit security/attendance/face sau khi review và chạy build trên máy có SDK.
3. Kiểm thử migration database v24 → v25 trên bản sao dữ liệu thật.
4. Thiết kế `class_sessions` có giờ kết thúc/status để tránh đánh dấu vắng sớm.
5. Thiết kế enrollment có thời gian hiệu lực để số liệu lịch sử không sai.
6. Mã hóa Face embedding bằng Android Keystore; xác định chính sách bảo vệ iris gallery với vendor.
7. Xác nhận release signing certificate với IriTech rồi tách release keystore khỏi repository.
8. Sửa deep link reset password sang verified HTTPS và hash token.

## 3.2 Nên cải thiện

### Kiến trúc code

Không nên viết lại toàn bộ. Lộ trình ít rủi ro:

1. Giữ Activity/XML hiện tại.
2. Tách `AttendanceRepository`, `StudentRepository`, `AuthRepository`, `SubjectRepository` từ `DatabaseHelper`.
3. Tạo service/use-case Java như `RecordAttendanceUseCase`, `CloseClassSessionUseCase`, `ExportAttendanceUseCase`.
4. Thêm ViewModel dần cho `AttendanceManagementActivity` và `StudentListActivity`; state/filter sống qua rotation/process recreation.
5. Chuyển thao tác nặng sang `ExecutorService`; tác vụ dài/bền vững như backup/export/email dùng WorkManager khi thật sự cần.
6. Chỉ cân nhắc Room sau khi schema và migration test ổn định. Có thể dùng Room trên cùng SQLite từng phần, nhưng phải kiểm tra tương thích database/backup và tránh đổi table hàng loạt.

### Cơ sở dữ liệu

- Thêm session status/start/end/cutoff.
- Thêm enrollment effective dates.
- Thêm `session_id`, `attendance_method`, `status_snapshot`, `note`, `created_at`, `created_by` cho attendance event.
- Dùng transaction cho mọi batch import/update.
- Thêm index theo `(subject_id, session_date)`, `(student_id, subject_id)`, status/effective dates.
- Migration rebuild bảng với NOT NULL/CHECK sau khi làm sạch dữ liệu.
- Trước restore: tiếp tục staging/integrity/version/table validation; thêm xác minh manifest/checksum cho biometric files.

### Chức năng điểm danh

Luồng đề xuất:

1. Chọn môn.
2. Chọn/mở session có thời gian bắt đầu-kết thúc.
3. Xác thực Iris/Face/password.
4. Resolve identity thành `student_id` có cấu trúc.
5. Kiểm tra enrollment hiệu lực tại ngày session.
6. Kiểm tra check-in hiện có trong transaction.
7. Tính status theo start/late window/end.
8. Ghi attendance event kèm method/device/operator.
9. Khi session đóng, materialize hoặc suy ra vắng; session chưa đóng hiển thị `Chưa điểm danh`.
10. Ghi audit event cho success/duplicate/reject/manual override.

### Thống kê

- Bộ lọc `fromDate`, `toDate`, môn học.
- Chỉ tính session `CLOSED`, loại `CANCELLED`.
- Hiển thị tổng sinh viên hiệu lực, tổng lượt present/late/absent, tỷ lệ.
- Sort theo tên/vắng/tỷ lệ.
- Chi tiết từng sinh viên và empty/error/loading state.
- Quy ước rõ: `presentCount` có bao gồm late; late rate tính trên total sessions hay attended sessions.

### Xuất báo cáo

- Sheet 1: một dòng/sinh viên, dựa trên session trong range và enrollment hiệu lực.
- Sheet 2: một dòng/sinh viên/session, kể cả absent, với method/status/note.
- Tạo một `AttendanceReportDataset` dùng chung cho UI, CSV và XLSX để tránh số liệu lệch.
- Test invariant: tổng present + absent = total sessions cho từng sinh viên; late ≤ present; số sheet 2 = số enrollment-session hợp lệ.
- SAF đã giúp xử lý tên file/ghi đè; thêm cảnh báo rõ trong UI trước khi mở document picker nếu cần.

### Bảo mật

- PBKDF2 đã có; tiếp tục chuyển reset token sang hash.
- Android Keystore AES-GCM cho face embeddings/backup keys.
- Không lưu biometric dưới public storage nếu vendor cho phép.
- Redact log.
- Re-auth Super Admin trước xóa hàng loạt, export biometric, restore, đổi owner.
- Không gửi SMTP trực tiếp từ bản phân phối.

### UI/UX

- Giữ Material/XML, chuẩn hóa toolbar/button/loading/error/empty state.
- Dùng màu + icon + text, không chỉ màu cho status.
- Nội dung mô tả accessibility, touch target tối thiểu, contrast.
- RecyclerView/ScrollView đúng chỗ; tránh fixed height lớn.
- Dialog xác nhận xóa/restore ghi rõ đối tượng và hậu quả.
- Thao tác dài có progress có thể hủy và không giữ reference Activity sau destroy.

## 3.3 Có thể phát triển sau

- Dashboard theo tuần/tháng và cảnh báo chuyên cần.
- Audit trail có thể xuất.
- Đồng bộ server/offline-first nếu nhiều thiết bị.
- Phân quyền chi tiết theo môn/giảng viên.
- Liveness nâng cao cho Face ID.
- Instrumentation test trên ma trận thiết bị Android 8–14 và thiết bị IriTech thật.

---

# Phần 4 – Kế hoạch triển khai

## Giai đoạn 1 – Sửa lỗi nghiêm trọng và bảo vệ dữ liệu

**Công việc**

- Rotate SMTP key; tắt direct SMTP production.
- PBKDF2 migration mật khẩu.
- Tắt Auto Backup hệ thống; kiểm soát FileProvider.
- Hash reset token, verified App Link.
- Tách release signing secret sau khi xác nhận certificate IriTech.
- Redact log nhạy cảm.

**File dự kiến**

- `app/build.gradle`, manifest.
- `PasswordHasher`, `PasswordResetHelper`, `DatabaseHelper`, `EmailService`.
- `LoginActivity`, `ResetPasswordActivity`.

**Rủi ro**

- Người dùng legacy không login được nếu migration sai.
- Đổi certificate có thể làm SDK/license IriTech không hoạt động.
- Email reset bị gián đoạn khi chưa có backend/App Link.

**Cách kiểm thử**

- DB fixture với SHA-256/plaintext/PBKDF2.
- Login rồi kiểm tra hash được upgrade.
- Decompile APK release, xác nhận không có SMTP secret.
- Test deep link hợp lệ/hết hạn/reuse/app giả mạo.
- Test ký APK trên thiết bị IriTech.

**Tiêu chí hoàn thành**

- Không secret trong APK/repository.
- Password mới/migration hoạt động.
- Security-sensitive flow fail-closed.
- Release signing được quản lý ngoài source.

## Giai đoạn 2 – Cải thiện database và logic điểm danh

**Công việc**

- Giữ sửa transaction/unique/enrollment v25.
- Thiết kế migration v26 cho session start/end/status và enrollment effective dates.
- Migration v27 cho attendance method/status/note/audit.
- Chuẩn hóa date/time.
- Adapter kết quả Iris có cấu trúc.

**File dự kiến**

- `DatabaseHelper`, model attendance/session/enrollment.
- `StudentListActivity`, `MockCheckinActivity`, `AttendanceManagementActivity`.
- Lớp tích hợp Iris, không sửa native SDK nếu không cần.

**Rủi ro**

- Migration làm sai lịch sử.
- Xung đột với backup cũ.
- SDK Iris trả format khác giữa firmware/version.

**Cách kiểm thử**

- Migration copy DB thật ở nhiều version.
- Test duplicate/concurrency/rollback.
- Test trước-đúng-sau cutoff; session open/closed/cancelled.
- Test sinh viên vào/ra môn giữa kỳ.
- Hardware test Iris enroll/identify/verify.

**Tiêu chí hoàn thành**

- Một attendance hợp lệ/student/session.
- Không vắng trước khi đóng session.
- Lịch sử không tính ngoài thời gian enrollment.
- Mọi reject có lý do/audit.

## Giai đoạn 3 – Hoàn thiện thống kê và xuất báo cáo

**Công việc**

- Range filter, subject filter, sorting.
- Dataset dùng chung cho UI/CSV/XLSX.
- Sheet 2 dạng dòng chi tiết.
- Thêm method/note/shift/status.
- Chạy export nền, progress/error rõ.

**File dự kiến**

- `AttendanceExportRepository`, `AttendanceExportService`, `AttendanceExcelExporter`.
- `AttendanceManagementActivity`, adapters/layout.
- Model export mới.

**Rủi ro**

- XLSX custom writer lỗi với dữ liệu lớn/ký tự đặc biệt.
- UI/CSV/XLSX dùng query khác nhau dẫn đến lệch.

**Cách kiểm thử**

- Golden dataset: absent/late/present/never check-in/new enrollment.
- Mở XLSX bằng Excel/LibreOffice/openpyxl.
- Invariant giữa hai sheet và UI.
- Stress test hàng nghìn sinh viên/session.

**Tiêu chí hoàn thành**

- Hai sheet đúng schema yêu cầu.
- Không duplicate không cần thiết.
- Bao gồm sinh viên không check-in.
- Tổng hợp và chi tiết khớp tuyệt đối.

## Giai đoạn 4 – Cải thiện giao diện và cấu trúc code

**Công việc**

- Tách repository/service/use-case.
- ViewModel cho Student/Attendance.
- Loading/error/empty/accessibility.
- Loại query N+1 và thao tác UI thread.
- Dọn dependency/Compose không dùng sau khi build ổn.

**File dự kiến**

- Các Activity lớn, adapters/layout.
- Repository/ViewModel mới bằng Java.
- Gradle dependency.

**Rủi ro**

- Refactor lớn làm thay đổi navigation/state.
- Lifecycle bug khi chuyển async.

**Cách kiểm thử**

- Regression theo từng màn.
- Rotation/background/process recreation.
- StrictMode, profiler, database query count.
- Accessibility scanner/manual TalkBack.

**Tiêu chí hoàn thành**

- Activity chỉ điều phối UI.
- Không DB/file/network nặng trên main thread.
- Không regression Iris/Face/backup.

## Giai đoạn 5 – Kiểm thử và đóng gói

**Công việc**

- Unit/instrumentation/migration test.
- Build debug/release, lint, R8.
- Test Android 8, 10, 11, 13, 14.
- Test thiết bị IriTech thật.
- Test backup/restore round trip và rollback.
- Quản lý version/changelog/release signing.

**File dự kiến**

- `src/test`, `src/androidTest`, CI config, ProGuard rules, release docs.

**Rủi ro**

- Native library/ABI/license chỉ lỗi trên thiết bị.
- R8 loại class SDK/reflection.

**Cách kiểm thử**

- Smoke suite đầy đủ: login → môn → sinh viên → enroll → attendance → stats → export → backup → uninstall/reinstall/restore.
- So checksum/count trước và sau restore.
- Test offline, quyền bị từ chối, file hỏng, database mới hơn.

**Tiêu chí hoàn thành**

- Build debug/release thành công.
- Không crash/ANR trong smoke suite.
- Số liệu và restore round trip đúng.
- Iris/Face hoạt động trên thiết bị mục tiêu.

---

# Phần 5 – Các sửa code Critical/High đã thực hiện

## 5.1 Commit `c57a11e` – `security: harden credentials and password storage`

**Lỗi**

- Password student plaintext/admin legacy SHA-256.
- SMTP secret được nhúng vào APK.
- Android Auto Backup có thể sao lưu database sinh trắc học ngoài luồng backup có kiểm soát.
- FileProvider authority hard-code không bám `applicationId`.

**File sửa chính**

- Thêm `PasswordHasher.java`.
- `DatabaseHelper.java`, `PasswordResetHelper.java`.
- `AddStudentActivity.java`, `ImportCsvActivity.java`, `StudentListActivity.java`.
- `app/build.gradle`, `AndroidManifest.xml`, `EmailService.java`.
- `AdminDetailActivity.java`, `ProfileActivity.java`.
- Thêm `local.properties.example`.

**Cách sửa**

- PBKDF2-HMAC-SHA256 120.000 vòng, salt ngẫu nhiên 16 byte, hash 256 bit; fallback HMAC-SHA1 cho provider Android cũ.
- Tự nâng cấp legacy khi xác thực thành công.
- Release SMTP fail-closed và secret rỗng.
- `allowBackup=false`; provider `${applicationId}.fileprovider`.

**Kiểm tra đã chạy**

- Test hash có salt khác nhau, đúng/sai password, legacy admin/student: đạt.

**Cần test thủ công**

1. Login admin DB cũ → thành công → mở DB copy, xác nhận hash đổi sang `pbkdf2_*`.
2. Student password cũ → xác thực → hash được nâng cấp.
3. Tạo/sửa/import account mới.
4. Build release rồi dùng `strings`/jadx xác nhận không có SMTP key.
5. Gửi mail debug khi opt-in; release phải báo cấu hình backend an toàn.

## 5.2 Commit `ee5f5a3` – `fix: make attendance writes atomic and consistent`

**Lỗi**

- Enrollment status sai.
- Session/cutoff/check-in không atomic.
- Duplicate và “điểm danh lại” có hành vi mâu thuẫn.
- Ngày không có session vẫn có thể hiển thị vắng.
- Import student dễ tạo dữ liệu nửa chừng/chạy UI thread/parser yếu.

**File sửa chính**

- `DatabaseHelper.java` (DB v25, migration, indexes, API attendance).
- `AddStudentActivity.java`, `ImportCsvActivity.java`.
- `StudentListActivity.java`, `MockCheckinActivity.java`, `AttendanceManagementActivity.java`.

**Cách sửa**

- Chuẩn hóa `ACTIVE/INACTIVE`.
- Unique/index attendance.
- `recordDailyAttendance()` transaction và enum kết quả.
- Chỉ tính vắng khi có session.
- Import sinh viên nền + transaction + parser CSV quoted + giới hạn dòng.

**Kiểm tra đã chạy**

- SQLite fixture migration `Not Enrolled` → ACTIVE; INACTIVE giữ nguyên.
- Insert duplicate bị chặn; count còn 1.
- Kiểm tra tĩnh method/transaction/date-time parser.

**Cần test thủ công**

1. Upgrade bản sao DB v24 lên v25; so số student/enrollment/check-in.
2. Điểm danh cùng student/môn/ngày hai lần; lần hai phải báo trùng và không tạo row.
3. Điểm danh student không thuộc môn; không tạo session/check-in.
4. Chọn ngày chưa có session; không hiển thị toàn lớp là vắng.
5. Import CSV có dấu phẩy trong tên, dòng lỗi giữa file, file >10.000 dòng.

## 5.3 Commit `0f4ae8c` – `fix: restore generic face attendance flow`

**Lỗi**

- Generic Face attendance truyền null target nhưng Activity cũ đóng ngay.
- Embedding JSON hỏng có thể làm crash matcher.
- Asset/model stream chưa đóng nhất quán.

**File sửa chính**

- `FaceRecognitionActivity.java`.
- `face/FaceMatcher.java`.
- `face/FaceEmbeddingExtractor.java`.

**Cách sửa**

- 1:N theo môn khi không có target; 1:1 khi có target.
- Bỏ qua embedding lỗi/null/khác length.
- Try-with-resources cho model asset/file/channel; đóng extractor/executor lifecycle.

**Kiểm tra đã chạy**

- Test Euclidean 3-4-5, null/length guard: đạt.

**Cần test thủ công**

1. Generic Face ID nhận đúng sinh viên trong môn.
2. Không được match sinh viên chỉ thuộc môn khác hoặc INACTIVE.
3. 1:1 sau password vẫn chỉ chấp nhận target.
4. Embedding JSON hỏng không crash.
5. Camera rotate/back/background nhiều lần không leak/crash.
6. Test ảnh/video spoof để đánh giá rủi ro liveness còn lại.

## 5.4 Commit `731ad35` – `fix: exclude inactive enrollments from exports`

**Lỗi**

- Export repository vẫn lấy mọi enrollment dù các màn hình khác đã chuyển sang ACTIVE.

**File sửa**

- `export/AttendanceExportRepository.java`.

**Cách sửa**

- Cả danh sách sinh viên và matrix query thêm `enrollment_status = ACTIVE`.

**Cần test thủ công**

- Mark một enrollment INACTIVE; sinh viên không xuất hiện trong report mới nhưng dữ liệu lịch sử trong DB vẫn nguyên vẹn.

## 5.5 Commit `5e792d2` – `fix: protect attendance report recipient privacy`

**Lỗi**

- Toàn bộ recipients ở TO.

**File sửa**

- `EmailService.java`.

**Cách sửa**

- Validate danh sách; sender ở TO; recipients ở BCC; fail nếu không có địa chỉ hợp lệ.

**Cần test thủ công**

- Gửi tới 2–3 mailbox test và xác nhận mỗi người không thấy địa chỉ còn lại; test địa chỉ rỗng/sai.

## 5.6 Kết quả build và kiểm tra hiện tại

### Build Android

**Chưa thành công vì môi trường**, không phải vì đã xác nhận lỗi compile trong source:

```text
Command: bash gradlew :app:assembleDebug --offline --stacktrace
Downloading https://services.gradle.org/distributions/gradle-8.13-all.zip
java.net.UnknownHostException: services.gradle.org
EXIT_CODE=1
```

Ngoài ra Android SDK không được cài trong container và `local.properties` là đường dẫn Windows.

### Kiểm tra tĩnh/fixture đã đạt

- PasswordHasher test: đạt.
- FaceMatcher distance/guard test: đạt.
- SQLite migration/unique fixture: đạt.
- XLSX fixture mở được bằng `openpyxl`, có hai sheet và dữ liệu đọc được.

### Điều chưa thể tuyên bố

- Chưa thể nói APK compile thành công.
- Chưa thể nói IriTech SDK/license/native library hoạt động.
- Chưa kiểm thử camera/Face/Iris trên thiết bị.
- Chưa kiểm thử migration/backup bằng database production thật.
- Chưa kiểm thử R8/release signing.

---

# Hướng dẫn build và smoke test trên máy phát triển

1. Cài Android SDK Platform 34, build-tools phù hợp, NDK `25.1.8937393` và các component mà SDK IriTech yêu cầu.
2. Sửa `local.properties`:

```properties
sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk
```

3. Không điền Brevo key trừ debug local cần thiết; production dùng backend.
4. Chạy:

```bash
gradlew clean :app:assembleDebug --stacktrace
gradlew :app:testDebugUnitTest
gradlew :app:lintDebug
```

5. Cài APK lên thiết bị mục tiêu, test theo thứ tự:
   - Login legacy/new password.
   - Tạo môn/sinh viên/enrollment.
   - Iris enroll/identify/verify.
   - Face 1:1 và 1:N.
   - Duplicate/not-enrolled/cutoff.
   - Daily/student statistics.
   - CSV/XLSX/email BCC.
   - Backup → thay đổi dữ liệu → restore → đối chiếu count và biometric.

