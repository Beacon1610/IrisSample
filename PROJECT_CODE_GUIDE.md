# Hướng dẫn cấu trúc và logic dự án IrisSample

## 1. Phạm vi tài liệu

Tài liệu này được lập từ mã nguồn hiện tại trong workspace. File `Project_Structure_Explanation.txt`
đã cũ: dự án hiện có ba module Gradle (`app`, `iris`, `iricapture+`) và đã bổ sung quản trị,
Face ID, báo cáo điểm danh, email, sao lưu mã hóa.

Các hàm getter/setter, callback Android lặp lại và hàm bind UI đơn giản được gom thành nhóm để
tài liệu dễ tra cứu. Các hàm có nghiệp vụ riêng được giải thích cụ thể.

## 2. Kiến trúc tổng thể

```text
IrisSample-IriAegis-26T/
|-- app/                 Ứng dụng Android: quản trị lớp học và điểm danh
|-- iris/                Lớp UI/controller tích hợp SDK iris
|-- iricapture+/         AAR đóng gói sẵn của SDK thiết bị iris
|-- build.gradle         Cấu hình plugin dùng chung
|-- settings.gradle      Khai báo ba module
|-- gradle.properties    Thuộc tính Gradle
|-- local.properties     SDK Android và cấu hình SMTP cục bộ, không commit secret
|-- irissample.json      Mẫu cấu hình quyền iris
|-- sample_*.csv         File CSV mẫu để import
|-- gradlew*             Gradle wrapper
`-- Project_Structure_Explanation.txt  Tài liệu cũ, không còn đầy đủ
```

Quan hệ phụ thuộc:

```text
app  --->  iris  --->  iricapture+.aar
 |                         |
 `-------------------------'
       dùng SDK trực tiếp ở một số màn hình mẫu
```

### 2.1. File ở thư mục gốc

- `.gitignore`: quy tắc loại cache, output build và file cục bộ khỏi Git.
- `build.gradle`: khai báo Android Gradle Plugin `8.13.2`, Kotlin `2.2.10`, Compose Compiler,
  repository dùng chung, NDK `25.1.8937393` và task `clean`.
- `settings.gradle`: đặt tên project `irissample`, include `:app`, `:iris`, `:iricapture+`;
  phần comment cuối file là cấu hình SDK source cũ không còn bật.
- `gradle.properties`: cấu hình JVM Gradle, AndroidX, Jetifier, BuildConfig và R class.
- `local.properties`: đường dẫn Android SDK và khóa cấu hình Brevo cục bộ. Không nên đưa file này
  lên Git hoặc công khai giá trị.
- `gradlew`, `gradlew.bat`: launcher Gradle wrapper cho Unix và Windows.
- `gradle/wrapper/gradle-wrapper.properties`: trỏ đến Gradle `8.13`.
- `gradle/wrapper/gradle-wrapper.jar`: mã launcher Gradle wrapper dạng nhị phân.
- `build_log.txt`: log build đã lưu để hỗ trợ chẩn đoán, không tham gia runtime.
- `Project_Structure_Explanation.txt`: mô tả cấu trúc cũ, chỉ nên dùng tham khảo lịch sử.
- `PROJECT_CODE_GUIDE.md`: tài liệu hiện tại.

## 3. Logic nghiệp vụ chính

### 3.1. Khởi động và đăng nhập

1. `LoginActivity` là launcher.
2. Ứng dụng tạo/mở SQLite và xin các quyền cần thiết.
3. Nếu chưa có Super Admin, ứng dụng cho đăng ký tài khoản đầu tiên hoặc khôi phục backup.
4. Admin đăng nhập bằng email + mật khẩu hoặc xác thực iris.
5. Sau khi thành công, ứng dụng mở `MainActivity`.

### 3.2. Phân quyền

- `Super Admin`: tạo/sửa/xóa Admin, import Admin, quản lý mọi môn học, gán giảng viên, backup/restore.
- `Admin`: quản lý môn học được gán, quản lý sinh viên, điểm danh và báo cáo.
- Một số thao tác nhạy cảm dùng khóa thiết bị qua `BiometricAuthHelper`.

### 3.3. Quản lý môn học và sinh viên

1. `SubjectListActivity` tải môn theo vai trò.
2. `AddSubjectActivity` tạo hoặc cập nhật môn. Super Admin có thể chọn giảng viên.
3. `StudentListActivity` tải sinh viên đã ghi danh vào môn.
4. Sinh viên được thêm/sửa bằng `AddStudentActivity` hoặc import CSV bằng `ImportCsvActivity`.
5. Ảnh khuôn mặt được chuyển thành embedding 192 chiều bởi `FaceEmbeddingExtractor`.

### 3.4. Điểm danh

`StudentListActivity` cung cấp ba cách xác thực:

- Mật khẩu sinh viên.
- Iris qua `iris/CaptureActivity`.
- Face ID qua `FaceRecognitionActivity`.

Khi xác thực thành công, ứng dụng ghi một dòng vào `checkin_history`. Màn hình cũng có chế độ
mock để demo và kiểm thử thủ công.

### 3.5. Báo cáo và email

`AttendanceManagementActivity` có ba nhóm chức năng:

- Xem danh sách điểm danh theo ngày và từ khóa.
- Export CSV qua Storage Access Framework.
- Quản lý người nhận và gửi báo cáo CSV qua Brevo SMTP.

### 3.6. Sao lưu và khôi phục

`BackupRestoreHelper` đóng gói database và avatar Admin vào ZIP, sau đó mã hóa AES-256-GCM.
`BackupRestoreActivity` chỉ cho Super Admin vận hành giao diện backup/restore.

### 3.7. Luồng SDK iris

1. Màn hình nghiệp vụ tạo `Intent` với action capture/enroll/verify/identify/unenroll.
2. `CaptureActivity` mở `CaptureFragment`.
3. `CaptureFragment` ánh xạ action sang `ActionType`, gọi `IriController`.
4. `IriController` mở camera, quản lý license, gọi SDK trong `iricapture+.aar`.
5. Kết quả template, ảnh và trạng thái được trả về bằng `Intent extra`.

## 4. Database

File: `app/src/main/java/com/iritech/irissample/DatabaseHelper.java`

Database SQLite là `attendance.db`, version `20`.

| Bảng | Vai trò |
|---|---|
| `admin` | Tài khoản quản trị, role, mã Admin, token reset, trạng thái đăng nhập lần đầu, trạng thái iris |
| `subjects` | Môn học, ca học, người tạo, giảng viên được gán, trạng thái |
| `students` | Sinh viên, thông tin liên hệ, mật khẩu, embedding khuôn mặt JSON, đường dẫn avatar |
| `enrollments` | Quan hệ nhiều-nhiều giữa sinh viên và môn học |
| `checkin_history` | Lịch sử điểm danh theo sinh viên, môn, ngày và giờ |
| `email_recipients` | Danh sách email nhận báo cáo theo môn |

Nhóm hàm:

- `onCreate()`: tạo toàn bộ bảng.
- `onOpen()`: bật foreign key SQLite.
- `onUpgrade()`: xóa và tạo lại schema khi đổi version.
- `hashPassword()`: băm mật khẩu Admin bằng SHA-256.
- `generateAdminCode()`: sinh mã Admin; hiện không phải luồng chính.
- `isSuperAdminExists()`, `isEmailExists()`, `authenticateUser()`, `verifyAdminPassword()`:
  kiểm tra và xác thực Admin.
- `insertSuperAdmin()`, `insertAdmin()`, `deleteAdmin()`, `getAllAdmins()`, `getAdminByEmail()`,
  `updateAdmin()`, `updateAdminEmail()`: CRUD Admin.
- `isFirstLogin()`, `markFirstLoginComplete()`: theo dõi lần đăng nhập đầu.
- `getAdminIdByEmail()`, `getEmailByAdminId()`, `getAdminRole()`, `isAdminIdExists()`:
  truy vấn tiện ích cho Admin.
- `updateAdminIrisEnrollment()`, `hasAdminEnrolledIris()`, `hasAnyAdminWithIris()`:
  lưu và kiểm tra trạng thái đăng ký iris của Admin.
- `createSubject()`, `assignInstructor()`, `isSubjectOwnedByAdmin()`, `getSubjectById()`,
  `getAllSubjectsWithInstructor()`, `getSubjectsByInstructor()`, `updateSubjectBySuperAdmin()`,
  `updateSubjectByAdmin()`: CRUD và phân quyền môn học.
- `getStudentPassword()`, `getStudentNameById()`, `isStudentInSubject()`:
  tra cứu sinh viên.
- `getStudentFaceVectorsBySubject()`, `getAllStudentFaceVectors()`:
  tải embedding để nhận dạng Face ID.
- `getTodayForAttendance()`: trả ngày hiện tại theo định dạng điểm danh thông thường.

## 5. Module `app`

### 5.1. Cấu hình

- `app/build.gradle`: module application; namespace `com.iritech.irissample`, min SDK `24`,
  target/compile SDK `34`, Java `17`; khai báo AndroidX, CameraX, ML Kit Face Detection,
  TensorFlow Lite, Gson, JavaMail và hai module iris. File đọc các khóa `brevo.*` từ
  `local.properties` rồi đưa vào `BuildConfig`.
- `app/src/main/AndroidManifest.xml`: quyền camera, network, storage, USB/iris; khai báo launcher,
  deep link reset password `irissample://reset-password`, deep link login `irissample://login`,
  các Activity và `FileProvider`.
- `app/src/main/assets/mobile_face_net.tflite`: model tạo embedding khuôn mặt.
- `app/src/main/assets/test_2592_1200.raw`: dữ liệu ảnh raw mẫu.

### 5.2. Điểm vào và dashboard

#### `LoginActivity.java`

Màn hình vào ứng dụng.

- `onCreate()`: khởi tạo DB, thư mục iris, USB host, quyền hệ thống và listener đăng nhập.
- `attemptLogin()`: kiểm tra input, xác thực Admin, lưu session và mở dashboard.
- `loginWithIris()`, `checkIrisPermissionsAndStart()`: mở identify iris sau khi đủ quyền.
- `onActivityResult()`, `extractEmailFromResult()`: đọc kết quả iris và lấy email Admin khớp.
- `onNewIntent()`, `handleDeepLink()`: nhận deep link login.
- `checkForBackupOnStartup()`: nếu chưa có Super Admin thì kiểm tra backup để đề xuất restore.
- Các hàm `show*Restore*Dialog()`, `restoreBackup()`, `restartApp()`: luồng khôi phục ban đầu.
- `initIrisFolders()`, `requestCriticalPermissions()`, callback permission: chuẩn bị môi trường.

#### `MainActivity.java`

Dashboard sau đăng nhập và nơi giữ các chức năng SDK iris mẫu.

- `onCreate()`: đọc session/role, dựng dashboard, kiểm tra lần đăng nhập đầu, giữ wake lock,
  tải config và gắn menu.
- `checkFirstLoginAndShowDialog()`: nhắc người dùng cập nhật hồ sơ lần đầu.
- `onResume()`: làm mới trạng thái khi quay lại.
- `loadConfigs()`: đọc `/sdcard/iritech/irissample.json`.
- `startCaptureActivity()`, `checkIrisPermissionsAndStart()`: mở SDK sau khi đủ quyền.
- `onActivityResult()`, `processResult()`: phân loại kết quả capture/enroll/verify/identify.
- `processCaptureResult()`: lưu template, BMP, JP2 và hiển thị ảnh tốt nhất.
- `saveFile()`: ghi buffer xuống storage.
- Các hàm permission, `initFolder()`, `checkStorage()`, `checkNetWork()`: chuẩn bị thiết bị.
- `writeWakeLock()`, `onDestroy()`: quản lý wake lock.
- `authenticateDeviceLockForAdminManagement()`: bảo vệ màn hình quản trị Admin.
- Callback menu: mở profile, quản lý Admin, backup/restore, logout.

### 5.3. Tài khoản Admin

#### `RegisterAdminActivity.java`

Tạo Super Admin đầu tiên hoặc Admin thường.

- `onCreate()`: xác định chế độ đăng ký, dựng form và listener.
- `attemptRegister()`: validate email, mật khẩu, số điện thoại, mã Admin rồi insert DB.
- `showError()`: hiển thị lỗi cạnh input.
- `setupDatePicker()`: chọn ngày sinh.
- `authenticateDeviceLockForCreateAdmin()`: yêu cầu xác thực thiết bị khi Super Admin tạo Admin.
- `enrollIrisForAdmin()`, `checkIrisPermissionsAndStart()`, `onActivityResult()`:
  đăng ký iris tùy chọn và cập nhật trạng thái.
- `initIrisFolders()`, `requestCriticalPermissions()`, callback permission: chuẩn bị SDK.
- `getAdminCodeByEmail()`: hàm tra mã hỗ trợ; không phải luồng chính hiện tại.

#### `AdminListActivity.java`

Danh sách Admin thường cho Super Admin.

- `onCreate()`, `onResume()`: dựng màn hình và tải lại danh sách.
- `loadAdminList()`: đọc Admin từ DB.
- `openCsvFilePicker()`, `onActivityResult()`, `importAdminsFromCsv()`: import Admin CSV.
- `showImportReport()`: tổng kết số dòng import thành công/thất bại.
- `showDeleteConfirmDialog()`: xác nhận xóa; hàm tồn tại nhưng không phải đường thao tác chính.
- Lớp lồng `AdminAdapter`: `getCount()`, `getItem()`, `getItemId()`, `getView()` bind item.
- `getInitials()`: sinh chữ đại diện avatar.

#### `AdminDetailActivity.java`

Xem, sửa, xóa Admin.

- `onCreate()`, `loadAdminData()`: tải bản ghi mục tiêu.
- `setViewMode()`, `setEditMode()`: chuyển trạng thái giao diện.
- `attemptSave()`, `updateAdmin()`: validate và cập nhật DB.
- `confirmDelete()`, `deleteAdmin()`: xóa Admin sau xác nhận.
- Các hàm camera/gallery/avatar: cập nhật ảnh hồ sơ.
- Các hàm Device Lock và callback: bảo vệ save/delete.
- Các hàm gửi email: thông báo cập nhật hoặc xóa tài khoản.

#### `ProfileActivity.java`

Hồ sơ của Admin đang đăng nhập.

- `onCreate()`, `initializeViews()`, `setupGenderSpinner()`, `setupListeners()`: dựng form.
- `loadUserData()`, `setSpinnerValue()`: đổ dữ liệu hồ sơ.
- `setViewMode()`, `setEditMode()`: điều khiển trạng thái xem/sửa.
- Các hàm camera/gallery và `saveBitmapToInternalStorage()`: cập nhật avatar.
- `showBiometricAuthentication()`: xác thực trước khi lưu.
- `attemptSave()`: validate, cập nhật hồ sơ/email và đánh dấu hoàn tất lần đăng nhập đầu.
- Các hàm iris và callback permission/result: enroll iris cho hồ sơ hiện tại.

### 5.4. Quên và đặt lại mật khẩu

#### `ForgotPasswordActivity.java`

- `onCreate()`: dựng hai lựa chọn reset bằng email hoặc iris.
- Luồng email: kiểm tra địa chỉ, gọi `PasswordResetHelper`, gửi deep link reset.
- Luồng iris: identify Admin, tạo token tạm và mở `ResetPasswordActivity`.
- Callback permission/result: xử lý kết quả SDK.

#### `ResetPasswordActivity.java`

- `onCreate()`: nhận token từ deep link hoặc extra iris.
- Hàm validate form: kiểm tra token, mật khẩu mới và xác nhận mật khẩu.
- Hàm reset: gọi `PasswordResetHelper`, báo kết quả và quay lại login.

#### `PasswordResetHelper.java`

- Hàm tạo token email: sinh token reset có hạn dùng ngắn.
- Hàm tạo token iris: sinh token tạm sau identify iris.
- Hàm kiểm tra token: đối chiếu token và thời hạn.
- Hàm reset mật khẩu: băm mật khẩu mới và cập nhật DB.

#### `PasswordValidationHelper.java`

- Hàm xác minh: gọi DB để so khớp mật khẩu Admin hiện tại.

### 5.5. Môn học

#### `SubjectListActivity.java`

- `onCreate()`, `onResume()`: dựng danh sách và tải theo role.
- `loadSubjects()`: Super Admin thấy mọi môn; Admin chỉ thấy môn được gán.
- Hàm search/filter: lọc danh sách hiển thị.
- Hàm menu item: mở sửa hoặc xác nhận xóa.
- Hàm xóa: xóa enrollment, check-in rồi subject trong transaction.
- Hàm import CSV: đọc môn học và giảng viên tùy chọn.
- Hàm báo cáo import và trạng thái rỗng: cập nhật UI.

#### `AddSubjectActivity.java`

- `onCreate()`: nhận chế độ add/edit, tải dữ liệu và listener.
- Hàm tải danh sách giảng viên: phục vụ Super Admin.
- Hàm validate/save: tạo hoặc cập nhật subject theo role.
- Hàm fill form: nạp subject hiện có khi sửa.

#### `adapter/SubjectWithSchedulesAdapter.java`

- Constructor: nhận danh sách và callback thao tác.
- `onCreateViewHolder()`, `onBindViewHolder()`, `getItemCount()`: chuẩn RecyclerView.
- `ViewHolder.bind()`: hiển thị tên, mã, lịch, giảng viên và nút action.

#### `model/SubjectWithSchedules.java`

- Constructor đầy đủ và tương thích cũ: tạo model subject.
- Getter/setter: đọc ghi dữ liệu subject.
- `isAssigned()`: cho biết subject đã có giảng viên.

### 5.6. Sinh viên và điểm danh

#### `StudentListActivity.java`

Màn hình nghiệp vụ điểm danh chính theo một subject.

- `onCreate()`: tải subject, dựng danh sách, search/filter, nút add/import/export và identify.
- `reloadStudents()`, `loadStudentsForSubject()`: query sinh viên đã enroll và trạng thái hôm nay.
- `setupSearchAndFilter()`, `applyFilters()`: lọc theo tên/mã/trạng thái.
- `hasStudentCheckedInToday()`: kiểm tra đã điểm danh.
- `showPasswordDialog()`: xác thực bằng mật khẩu sinh viên.
- `showMockCheckinDialog()`: chọn sinh viên để điểm danh mô phỏng.
- `performCheckinLogic()`: kiểm tra lặp, xin xác nhận và ghi điểm danh.
- `markAttendance()`: insert `checkin_history`.
- `startFaceIdAuth()`: mở camera nhận dạng khuôn mặt.
- Các hàm iris: xin quyền, mở identify/enroll/unenroll, parse kết quả.
- `onActivityResult()`: điều phối kết quả iris, Face ID, add/edit/import.
- `exportAttendanceToCSV()` và các hàm CSV/storage: export cũ; UI hiện điều hướng sang
  `AttendanceManagementActivity`.

#### `StudentAdapter.java`

- Constructor: nhận danh sách sinh viên và callback.
- `getCount()`, `getItem()`, `getItemId()`, `getView()`: bind avatar, thông tin, trạng thái và
  action check-in/enroll/unenroll/edit.

#### `AddStudentActivity.java`

- `onCreate()`: xác định add/edit và dựng form.
- Hàm camera/gallery: lấy ảnh khuôn mặt.
- Hàm xử lý bitmap: gọi `FaceEmbeddingExtractor`, giữ embedding JSON và lưu avatar crop.
- Hàm validate/save: thêm hoặc cập nhật sinh viên.
- Hàm enrollment: liên kết sinh viên với subject.
- `loadStudentInfo()`: nạp dữ liệu khi sửa.
- Callback permission/result: nhận ảnh camera/gallery.

#### `ImportCsvActivity.java`

- `onCreate()`: nhận subject và chọn file CSV.
- Hàm đọc CSV: parse từng dòng sinh viên.
- Hàm import: `INSERT OR IGNORE` student và `INSERT OR REPLACE` enrollment.
- Hàm báo cáo: hiển thị số bản ghi thành công/thất bại.

#### `MockCheckinActivity.java`

Màn hình demo điểm danh không phụ thuộc xác thực thật.

- `onCreate()`: dựng lựa chọn ngày giờ và danh sách sinh viên.
- Hàm load student: lấy sinh viên trong subject.
- Hàm check-in một sinh viên hoặc tất cả: ghi lịch sử với thời gian tùy chọn/ngẫu nhiên.
- Hàm clear today: xóa dữ liệu mock trong ngày.

#### `AttendanceManagementActivity.java`

Quản lý dữ liệu điểm danh, export và gửi email.

- `onCreate()`: nhận subject, dựng ba tab và listener.
- Hàm load summary: đếm tổng sinh viên và số đã điểm danh.
- Hàm chọn ngày/search/filter: tải danh sách xem báo cáo.
- Hàm query record: LEFT JOIN enrollment, student và check-in để hiển thị cả sinh viên vắng.
- Hàm import attendance CSV: đọc file vào danh sách xem tạm thời, không ghi DB.
- Hàm export CSV: sinh metadata + dữ liệu, tạo bản cache và mở `ACTION_CREATE_DOCUMENT`.
- Hàm recipient: thêm, xóa, import, chọn người nhận email theo subject.
- Hàm gửi mail: đính kèm CSV và gọi `EmailService`.
- Callback activity result: xử lý file import và nơi lưu export.

#### `adapter/AttendanceRecordAdapter.java`

- Constructor, `onCreateViewHolder()`, `onBindViewHolder()`, `getItemCount()`:
  hiển thị từng dòng báo cáo.
- `updateRecords()`: thay dữ liệu và refresh.
- `getCheckedInCount()`: đếm dòng đã điểm danh.

#### `adapter/EmailRecipientAdapter.java`

- Các hàm RecyclerView: bind tên, email và checkbox.
- Hàm selected email: trả tập địa chỉ được chọn.
- Hàm update/select all: cập nhật dữ liệu và trạng thái chọn.

#### `model/AttendanceRecord.java`

- Constructor/getter/setter: model một dòng điểm danh.
- `getShortDate()`, `getShortTime()`: rút gọn ngày và giờ cho UI.

#### `model/EmailRecipient.java`

- Constructor/getter/setter: model người nhận báo cáo.
- Hàm display name: chọn tên hoặc fallback email.

### 5.7. Face ID

#### `FaceRecognitionActivity.java`

- `onCreate()`: dựng CameraX preview và nút capture.
- Hàm xin quyền/bind camera: mở camera trước.
- Hàm capture: lấy ảnh, tạo embedding và gọi matcher.
- Hàm auto/manual decision: xác nhận kết quả theo ngưỡng khoảng cách.
- Hàm trả kết quả: gửi student id/name về màn hình gọi.

#### `face/FaceEmbeddingExtractor.java`

- Constructor: nạp model TFLite.
- `extract()`: tìm khuôn mặt lớn nhất, crop, resize và chạy model.
- Hàm chọn face lớn nhất: ưu tiên vùng mặt có diện tích cao nhất.
- Hàm crop/pad CPU: cắt ảnh quanh khuôn mặt.
- Hàm resize: chuẩn hóa về `112 x 112`.
- Hàm inference: chuyển bitmap thành buffer chuẩn hóa và đọc embedding `192` float.
- Hàm load model: mmap asset TFLite.
- `close()`: giải phóng interpreter.

#### `face/FaceMatcher.java`

- Constructor: nhận DB.
- `findBestMatch()`: tải embedding theo subject, tính khoảng cách Euclidean và chọn gần nhất.
- `euclideanDistance()`: tính khoảng cách giữa hai vector.

#### `face/FaceMatchResult.java`

- Constructor/getter: giữ student id, tên và distance của kết quả gần nhất.

### 5.8. Sao lưu, mã hóa và xác thực thiết bị

#### `BackupRestoreActivity.java`

- `onCreate()`: kiểm tra Super Admin và dựng giao diện.
- Hàm backup: yêu cầu xác nhận, chạy background và hiển thị kết quả.
- Hàm restore latest/chọn file: yêu cầu xác nhận, gọi helper rồi restart app.
- Hàm delete all: xóa backup sau xác nhận.
- Hàm list backup: hiện còn TODO cho giao diện danh sách đầy đủ.

#### `BackupRestoreHelper.java`

- Hàm tạo backup: copy database + avatar Admin vào ZIP, mã hóa rồi lưu trong Documents.
- Hàm restore: giải mã file `.enc`, giải nén, kiểm tra file DB, thay database và avatar.
- Hàm restore legacy: hỗ trợ backup DB dạng cũ.
- Hàm latest/list/delete: quản lý các file backup.
- Hàm cập nhật avatar path: sửa đường dẫn avatar sau restore.
- Hàm ZIP an toàn: ngăn path traversal khi giải nén.
- Hàm metadata/date/size: hỗ trợ hiển thị thông tin backup.

#### `EncryptionHelper.java`

- Hàm encrypt: sinh salt + IV, dẫn xuất key PBKDF2 và mã hóa AES-256-GCM.
- Hàm decrypt: đọc header, dẫn xuất lại key và giải mã.
- `hashPassword()`: băm chuỗi dùng cho helper.
- Hàm checksum: tạo checksum file.

#### `BiometricAuthHelper.java`

- Hàm authenticate: ưu tiên biometric + credential thiết bị; fallback credential-only.
- Fallback app password: yêu cầu mật khẩu Admin nếu thiết bị không hỗ trợ.
- Callback: báo success/error về màn hình gọi.

### 5.9. Email và tiện ích

#### `EmailService.java`

- Constructor: đọc cấu hình Brevo SMTP từ `BuildConfig`.
- Hàm tạo session SMTP: TLS và xác thực.
- Hàm gửi đăng ký, tạo tài khoản, cập nhật, xóa: tạo HTML tương ứng.
- Hàm gửi reset password: gửi deep link token.
- Hàm gửi attendance: gửi CSV đính kèm bất đồng bộ.
- Hàm gửi HTML chung: thực thi gửi mail ở background.

#### `InitialsAvatarHelper.java`

- Hàm lấy initials: lấy chữ đầu tên.
- Hàm tạo bitmap avatar: vẽ vòng tròn màu ổn định và initials.
- Hàm màu: ánh xạ chuỗi thành màu đại diện.

#### `LicenseCheckHelper.java`

- Hàm kiểm tra license: bỏ qua trong mock mode; nếu thiếu license thì mở dialog đăng ký;
  nếu hợp lệ thì mở `CaptureActivity`.

### 5.10. Kotlin theme mẫu

- `model/ui/theme/Color.kt`: bảng màu Compose.
- `model/ui/theme/Theme.kt`: `IrissampleTheme`, hỗ trợ dynamic color Android 12.
- `model/ui/theme/Type.kt`: typography Compose.

Phần lớn UI nghiệp vụ hiện dùng XML layout; ba file Compose là template hỗ trợ.

### 5.11. Resource của `app`

- `res/layout/activity_*.xml`: layout cho từng Activity cùng tên.
- `res/layout/item_admin.xml`, `item_student.xml`, `item_subject.xml`,
  `item_attendance_record.xml`, `item_email_recipient.xml`: item cho adapter.
- `res/layout-land/activity_main.xml`: dashboard khi xoay ngang.
- `res/drawable/*`: shape, button, icon và avatar mặc định.
- `res/menu/menu_main.xml`, `menu_subject_options.xml`: menu dashboard và subject.
- `res/values/*`: string, màu, kích thước, style, theme và array.
- `res/xml/file_paths.xml`: vùng file được chia sẻ qua `FileProvider`.
- `res/mipmap*/*`: launcher icon.

## 6. Module `iris`

### 6.1. Cấu hình

- `iris/build.gradle`: Android library; min SDK `19`, target/compile SDK `33`, Java `8`;
  phụ thuộc `:iricapture+`.
- `iris/src/main/AndroidManifest.xml`: khai báo màn hình capture/test và quyền thiết bị.
- `res/xml/device_filter.xml`: danh sách USB vendor/product cho IriEnvoy, iM20T, iM20Q, iC30M,
  IriEgis, iM26T.
- `res/raw/*`: âm thanh hướng dẫn và trạng thái capture.
- `res/layout/*`: UI camera/capture, dialog license/settings và best image.
- `res/values/attrs.xml`: thuộc tính custom view `ArcProgress`.

### 6.2. API và UI capture

#### `Constants.java`

- Khai báo action và key `Intent extra` dùng chung giữa `app` và module `iris`.

#### `ActionType.java`

- Enum thao tác SDK: `CAPTURE`, `ENROLL`, `VERIFY`, `UNENROLL`, `IDENTIFY`.

#### `CapturePurpose.java`

- Enum mục đích capture nội bộ: `NONE`, `ENROLL`, `VERIFY`.

#### `CaptureActivity.java`

- `onCreate()`: chọn layout UVC/non-UVC, tạo fragment và gắn nút stop.
- Hàm scale layout: điều chỉnh UI landscape.
- Hàm stop/back: dừng capture và trả kết quả.
- `setUsbActivity()`: giữ Activity host cho thiết bị USB.
- `getResultImages()`: proxy lấy ảnh tốt nhất từ controller.

#### `CaptureFragment.java`

- `onCreate()`, `onCreateView()`, `onViewCreated()`: đọc action, dựng UI và khởi tạo controller.
- Hàm map action: chuyển `Intent action` thành `ActionType`.
- Hàm bắt đầu thao tác: capture/enroll/verify/identify/unenroll.
- Callback controller: cập nhật tiến độ hoặc đóng màn hình.
- Các overload `finish()`: đóng Activity với lỗi, template, kết quả verify hoặc danh sách identify.

### 6.3. Controller SDK

#### `IriController.java`

Singleton trung tâm của module iris.

- `getInstance()`: lấy singleton.
- `initialize()`: nạp settings/license, tạo `IrisCapture`, `IrisReg`, camera, gallery repo và
  đăng ký listener USB.
- Hàm license: load/reload/request license server và retry khi có network.
- `setLayout()`: inflate view capture UVC và giữ tham chiếu thanh tiến độ/trạng thái.
- `startCapture()`: mở camera và gọi SDK theo action.
- Hàm enroll: capture template rồi đăng ký vào gallery.
- Hàm verify: chạy xác minh template.
- Hàm identify: tìm user khớp trong gallery.
- Hàm unenroll: xóa user khỏi gallery.
- `stopCapture()`, `release()`: dừng SDK, camera, listener, media.
- Hàm camera: mở camera SDK, cấu hình preview và đăng ký callback.
- Callback USB/audio: phát âm thanh khi gắn/tháo thiết bị.
- `onCaptureNotify()`: nhận progress, indication, completed, timeout, abort và lỗi license.
- Hàm UI progress/status: cập nhật thanh trái/phải, message và indicator.
- Hàm result image/template: trả dữ liệu capture cho Activity gọi.
- `getIrisReg()`: truy cập registry SDK.
- `setFlipImages()`: điểm mở rộng flip ảnh; hiện chỉ trả giá trị cố định.

#### `CustomIrisCamera.java`

Camera tùy biến, có fallback camera Android mặc định.

- Constructor: thử nạp implementation ngoài từ `/sdcard/iritech/iriscamera.dex` bằng reflection.
- `open()`, `close()`, `startPreview()`, `stopPreview()`: vòng đời camera.
- `isOpened()`: kiểm tra trạng thái; implementation hiện trả `false`.
- Hàm IR/resolution/parameter: điều khiển thiết bị nếu implementation ngoài hỗ trợ.
- Hàm streaming: đẩy frame raw từ thiết bị hoặc asset test qua thread.

#### `Settings.java`

- Nhóm getter/setter SharedPreferences: scale preview, flip ảnh, license id/key và cờ xin license mới.

#### `LicenseInfo.java`

- `getInstance()`: singleton thông tin license.
- Hàm load/save: đồng bộ license với `Settings`.
- Getter/setter: đọc ghi id và key.

#### `DeveloperSettings.java`

- Getter/setter mock mode: bật chế độ không cần thiết bị thật.
- Hàm fake template: cung cấp template mẫu trong mock mode.

#### `MediaData.java`

- `getInstance()`: singleton phát âm thanh.
- Hàm play tương ứng sự kiện capture/USB.
- `release()`: giải phóng media player.

#### `Utilities.java`

- `convertRawImageToBitmap()`: chuyển ảnh xám raw sang byte BMP 8-bit.
- Hàm ghi số little-endian: xây header BMP.

#### `ImageDataParcelable.java`

- Constructor/`writeToParcel()`/`CREATOR`: bridge `ImageData` qua Android Parcel.

#### `TestActivity.java`

- `onCreate()`: màn hình test tối giản cho module.

### 6.4. Settings, dialog và custom view

#### `com/iritech/android/iirisservicesettings/IriConstants.java`

- Hằng số key/default/threshold cho service settings iris.

#### `IriServiceSettings.java`

- Getter/setter SharedPreferences: lưu cấu hình dịch vụ.
- Hàm JSON: serialize/deserialize settings.
- Hàm force request license: ép tải license mới.

#### `TextValidator.java`

- Adapter `TextWatcher`: bỏ qua callback không cần dùng và gọi validate sau khi text đổi.

#### `widget/alertdialog/RegisterLicenseDialog.java`

- Constructor và `show()`: dựng form license.
- Hàm validate/save: lưu id/key, đánh dấu request mới nếu thay đổi.
- Hàm tiếp tục: mở pending Activity sau khi đăng ký license.

#### `widget/alertdialog/SettingDialog.java`

- Constructor/`show()`: dựng cấu hình camera, flip, mock mode và license.
- Hàm validate/save: ghi `Settings` và yêu cầu restart luồng capture.

#### `widget/alertdialog/BestImageDialog.java`

- Setter title/message/images và `show()`: hiển thị ảnh iris tốt nhất cùng kết quả.

#### `widget/circleprogress/Utils.java`

- `dp2px()`, `sp2px()`: đổi đơn vị Android.

#### `widget/circleprogress/ArcProgress.java`

- Constructor/init: đọc custom attribute.
- `onDraw()`: vẽ cung nền, cung tiến độ và text.
- Getter/setter: tùy biến màu, stroke, progress, max và text.
- Hàm save/restore state: giữ trạng thái khi recreate view.

## 7. Module `iricapture+`

- `iricapture+/build.gradle`: export file AAR dưới configuration mặc định.
- `iricapture+/iricapture+.aar`: SDK nhị phân đóng gói sẵn. Mã nguồn bên trong không nằm trong
  workspace, nên chỉ có thể mô tả API qua cách module `iris` gọi nó.

## 8. File dữ liệu và file hỗ trợ

- `irissample.json`: cấu hình permission `com.id2mp.permissions.IRIS`.
- `sample_admins_import.csv`: mẫu import Admin với email, tên, mật khẩu, điện thoại, mã Admin.
- `sample_subjects_import.csv`: mẫu import subject với mã, tên và ca học; code còn hỗ trợ
  `instructor_id` tùy chọn.
- `sample_students_import.csv`: mẫu import sinh viên, nhưng header hiện không khớp hoàn toàn parser.
- `gradlew`, `gradlew.bat`, `gradle/wrapper/*`: chạy Gradle đúng version.
- `.idea/`, `.gradle/`, `.kotlin/`, `build/`, `*/build/`: metadata IDE/cache/output sinh tự động,
  không chứa nghiệp vụ cần đọc thủ công.
- `app/src/test`, `app/src/androidTest`, `iris/src/test`, `iris/src/androidTest`: test mẫu mặc định,
  chưa bao phủ nghiệp vụ.

## 9. Lưu ý kỹ thuật

Các mục dưới đây là rủi ro cần xử lý nếu dùng ngoài môi trường demo:

1. `students.password` đang lưu và so sánh dạng plain text. Mật khẩu Admin dùng SHA-256 không salt;
   nên thay bằng password hashing thích nghi như PBKDF2/bcrypt/scrypt/Argon2.
2. `DatabaseHelper.onUpgrade()` xóa toàn bộ bảng. Khi nâng schema sẽ mất dữ liệu nếu không có migration.
3. Khóa SMTP được đưa vào `BuildConfig`, vì vậy có thể trích xuất từ APK. Production nên gửi mail qua backend.
4. Điểm danh thường dùng `dd/MM/yyyy`, nhưng `MockCheckinActivity` ghi `yyyy-MM-dd`; báo cáo có thể
   lọc sai dữ liệu mock.
5. `sample_students_import.csv` ghi cột thứ năm là `photo_path`, trong khi parser đọc cột thứ năm
   như `password` và cột thứ sáu mới là `photo_path`.
6. Parser CSV dùng tách chuỗi đơn giản, chưa xử lý dấu phẩy nằm trong giá trị được quote.
7. Khi sửa sinh viên mà không chọn lại ảnh mặt, cần kiểm tra `AddStudentActivity` để tránh ghi rỗng
   `face_vector`.
8. `CustomIrisCamera.isOpened()` hiện luôn trả `false`; cần xác nhận lại nếu SDK dựa vào trạng thái này.
9. `IriController.setFlipImages()` hiện là placeholder, nên cấu hình flip chưa tác động thực tế.
10. `BackupRestoreActivity` còn TODO cho màn hình danh sách toàn bộ backup.
