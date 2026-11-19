# Báo cáo kỹ thuật — Recall Keeper 1.0.0

Ngày: 19/11/2026 · Repo: `robloxhihi/recall-keeper` · Package: `com.robloxhihi.recallkeeper`

## 1. Phạm vi

App đọc **thông báo đã tới máy của người dùng**, lưu lại nội dung vào database cục bộ, và giữ bản
lưu đó kể cả khi người gửi thu hồi tin nhắn trong Messenger / Facebook. Tin bị thu hồi được gắn
nhãn phân biệt với tin thường.

Việc app **không** làm, ghi rõ để không bán sai cho người dùng trả phí:

- Không xem được tin nhắn người khác **chưa gửi**. Nội dung đó nằm trên máy người gửi; không có
  API hay quyền Android nào trên máy người nhận đọc được. Messenger chỉ truyền trạng thái
  "đang gõ", không truyền nội dung.
- Không đọc trộm hội thoại của người khác, không chạy trên máy người khác, không gửi dữ liệu đi.
- Không dùng AccessibilityService, không hook, không sửa app Messenger.

## 2. Kiến trúc

| Thành phần | Tệp | Vai trò |
|---|---|---|
| Listener | `service/RecallListenerService.kt` | Nhận `onNotificationPosted` / `onNotificationRemoved` từ hệ thống |
| Bộ phát hiện | `core/RecallDetector.kt` | Nhận diện câu thông báo thu hồi (vi + en), chống khớp nhầm |
| Lưu trữ | `data/AppDb.kt`, `MessageDao.kt`, `MessageEntity.kt` | Room, bảng `messages`, chỉ mục theo hội thoại / thời gian |
| Cấu hình | `data/Store.kt` | SharedPreferences: tạm dừng, số ngày giữ, gói app theo dõi |
| Giao diện | `ui/MainScreen.kt`, `ui/Theme.kt` | Compose Material 3, badge 3 trạng thái, lọc, tìm, xuất JSON |

Luồng: hệ thống đăng notification → listener lọc theo gói app → tách nội dung → chống trùng bằng
SHA-256 → ghi Room → UI tự cập nhật qua `Flow`.

Gói app được theo dõi mặc định: `com.facebook.orca` (Messenger), `com.facebook.katana` (Facebook),
`com.facebook.lite`, `com.facebook.mlite`.

Nhãn app được hard-code thay vì gọi `PackageManager`, để **không** phải xin `QUERY_ALL_PACKAGES`
hay khai `<queries>` (từ Android 11 mới cần).

### Tách nội dung tin nhắn

Ưu tiên `Notification.EXTRA_MESSAGES` (MessagingStyle). Mỗi phần tử là một `Bundle` với khoá
`text`, `sender`, `time` — nhờ đó lấy đúng **người gửi và giờ của từng tin**, kể cả khi Messenger
gộp nhiều tin vào một notification. Nếu không có, rơi về `EXTRA_BIG_TEXT` rồi `EXTRA_TEXT`.

Chống trùng: khoá băm `SHA-256(convKey | sender | text | postedAt)`. Tin cũ xuất hiện lại trong
notification gộp sẽ không bị ghi đôi; `notifKey` được cập nhật lại để còn theo vết khi notification
bị xoá.

## 3. Cơ chế gắn nhãn thu hồi — hai mức tin cậy khác nhau

### Mức A — ĐÃ THU HỒI (xác nhận)

Khi người gửi thu hồi, Messenger **thay nội dung notification** thành một câu thông báo, ví dụ
"Nguyen Van A đã thu hồi một tin nhắn" / "Nguyen Van A unsent a message". `RecallDetector` khớp
các mẫu câu vi + en, rồi đánh dấu tin gần nhất còn ở trạng thái thường **trong cùng hội thoại**
là `CONFIRMED`, và lưu câu thông báo làm bằng chứng (`evidence`) hiện ngay dưới tin.

Nếu câu thông báo nói rõ số lượng ("thu hồi 3 tin nhắn"), app đánh dấu đúng 3 tin gần nhất.

Chống khớp nhầm: người ta chat "mày thu hồi tin nhắn đi" cũng khớp chuỗi. Nên chỉ coi là thông báo
thu hồi khi câu **≤ 140 ký tự, một dòng, và cụm khớp nằm trong 45 ký tự đầu** (ngay sau tên người
gửi). Đây là heuristic, không phải bảo đảm tuyệt đối.

Nếu không tìm thấy tin gốc trong bộ nhớ (ví dụ tin tới trước khi cài app), app vẫn ghi một dòng
trạng thái `CONFIRMED` với nội dung "[Có tin bị thu hồi nhưng app chưa lưu được tin gốc]" —
**không bịa nội dung tin**.

### Mức B — NGHI THU HỒI (suy đoán)

`onNotificationRemoved(sbn, rankingMap, reason)` cho biết **vì sao** notification biến mất. App
chỉ dùng `REASON_APP_CANCEL` (chính app xoá một notification cụ thể), và loại trừ:

- `REASON_CLICK` đã xảy ra trước đó → người dùng bấm vào notification, không phải thu hồi.
- `REASON_CANCEL` / gạt tay → bỏ qua hoàn toàn.
- `REASON_APP_CANCEL_ALL` → bỏ qua hoàn toàn, vì mở app cũng xoá hết notification, quá mơ hồ.
- Đã có notification mới hơn trong cùng hội thoại → bị thay thế, không phải thu hồi.

**Đây là lý do nhãn B là "NGHI" chứ không phải "ĐÃ".** Kể cả sau khi lọc, `REASON_APP_CANCEL`
vẫn có thể chỉ là do người dùng đã đọc tin trong app. App ghi rõ điều đó trong phần bằng chứng,
và cho người dùng bấm "Đây là tin thường" để sửa lại nhãn.

Ngược lại, người dùng có thể bấm "Đánh dấu thu hồi" cho tin thường nếu tự biết chắc.

## 4. Pin và điều kiện chạy

App **không có** foreground service, không `AlarmManager`, không `WorkManager`, không polling,
không wakelock. Hệ thống bind listener và gọi callback đúng lúc có notification. Việc ghi database
chạy trên `Dispatchers.IO` rồi kết thúc. Dọn tin quá hạn chỉ chạy tối đa một lần mỗi giờ, ngay
trong lần bắt tin, không đặt lịch riêng.

| Tình huống | Có bắt được tin? |
|---|---|
| Màn hình tắt | Có |
| Doze / standby | Có |
| App bị đóng hẳn (swipe khỏi Recent) | Có — listener do hệ thống bind |
| Máy khởi động lại | Có, hệ thống bind lại listener |
| **Máy tắt nguồn** | **Không.** Không có tiến trình nào chạy khi máy tắt |
| Người gửi thu hồi trước khi notification tới máy | Không — chưa có gì để lưu |
| Hội thoại bị tắt thông báo / mute | Không có notification → không lưu được |
| Đang mở đúng hội thoại trong Messenger | Thường không phát notification → không lưu được |
| Tin chỉ có ảnh / sticker, không có chữ | Không có text để lưu |

Đây là giới hạn của cả họ app notification-history, không phải lỗi của bản build này.

## 5. Quyền và riêng tư

Manifest khai **đúng một quyền**: `android.permission.POST_NOTIFICATIONS` (để app hiện thông báo
trạng thái của chính nó trên Android 13+).

Không có `INTERNET`, không `ACCESS_NETWORK_STATE`, không quyền bộ nhớ, không Accessibility.
CI kiểm tra điều này bằng hai nguồn độc lập và **fail build** nếu phát hiện `INTERNET`:

1. `aapt2 dump xmltree` trên chính file APK đã build.
2. Báo cáo merged manifest của AGP (`manifest-merger-release-report.txt`).

Kết quả được đính kèm release dưới tên `PERMISSIONS.txt`.

Các chốt khác:

- `allowBackup="false"` → nội dung tin không bị đẩy lên cloud backup hay chuyển sang máy khác.
- Xuất dữ liệu dùng SAF (`ACTION_CREATE_DOCUMENT`), người dùng tự chọn chỗ lưu; app không cần
  quyền bộ nhớ.
- Số ngày giữ tin chọn được 7 / 30 / 90, tự dọn.
- Nút "Tạm dừng" ngừng ghi ngay, "Xoá hết" xoá sạch có xác nhận.
- Trong app có đoạn công bố rõ app đọc gì và không làm được gì.

## 6. Những gì CI đã chứng minh, và những gì CHƯA

Đã kiểm chứng bằng máy:

- Build thành công trên GitHub Actions (JDK 17, AGP 8.7.3, Gradle 8.10.2, compileSdk 35).
- APK không có quyền `INTERNET`, đối chiếu hai nguồn.
- SHA-256, SHA-1, kích thước và chứng thư người ký của APK (`HASHES.txt`).

**CHƯA kiểm chứng — phải nói thật:**

- Chưa chạy trên thiết bị thật. CI chỉ chứng minh nó biên dịch và đúng danh sách quyền, **không**
  chứng minh việc bắt tin và gắn nhãn chạy đúng trong Messenger thật.
- Câu thông báo thu hồi thật của Messenger trên máy người dùng có thể khác mẫu trong
  `RecallDetector` (phụ thuộc ngôn ngữ app và phiên bản). Nếu khác, mức A sẽ không kích hoạt và
  cần thêm mẫu.
- Mức B chưa được đo tỷ lệ báo nhầm trên thực tế.
- Hành vi với nhóm chat đông người, tin trả lời (reply), tin có media chưa được kiểm tra.

## 7. Kế hoạch kiểm thử trên máy thật

1. Cài APK, mở app, bấm "Cấp quyền truy cập thông báo", bật Recall Keeper.
2. Quay lại app: dòng trạng thái phải thành "Đang hoạt động".
3. Từ tài khoản khác gửi một tin nhắn văn bản → mở app, tin phải xuất hiện với nhãn TIN THƯỜNG,
   đúng người gửi và đúng giờ.
4. Thu hồi tin đó từ tài khoản gửi → tin trong app phải đổi thành ĐÃ THU HỒI, kèm câu bằng chứng.
   Nếu nó chỉ thành NGHI THU HỒI, ghi lại nội dung notification thật để bổ sung mẫu câu.
5. Gửi tin rồi tự gạt notification đi → nhãn phải **giữ** TIN THƯỜNG (không được thành NGHI).
6. Gửi tin rồi bấm vào notification để đọc → nhãn phải giữ TIN THƯỜNG.
7. Tắt màn hình, gửi tin → vẫn phải bắt được.
8. Tắt nguồn máy, gửi tin rồi thu hồi trước khi bật máy → xác nhận app **không** cứu được, đúng
   như đã công bố.
9. Bấm "Xuất JSON", mở file kiểm tra đủ trường và đúng tiếng Việt có dấu.

## 8. Chữ ký và phân phối

Nếu repo chưa có secret `RK_KEYSTORE_B64`, CI ký bằng **debug key của runner**. APK đó cài và chạy
được ngay, nhưng khoá đổi theo lần build nên **không cập nhật đè được** — người dùng phải gỡ bản cũ.

Trước khi phát cho người dùng trả phí, cần một khoá release cố định:

```
keytool -genkeypair -v -keystore rk.jks -alias rk -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 rk.jks
```

 rồi thêm 4 secret vào repo: `RK_KEYSTORE_B64`, `RK_KEYSTORE_PASSWORD`, `RK_KEY_ALIAS`,
`RK_KEY_PASSWORD`. File `.jks` **không** được commit (đã chặn trong `.gitignore`). CI tự dùng khoá
đó khi thấy secret, và ghi `signing: release-key-from-secret` vào `HASHES.txt`.

## 9. Tích hợp vào app có sẵn

Ba tệp `service/`, `core/`, `data/` là một khối độc lập, không dính UI. Muốn nhúng vào app chính:
copy ba thư mục đó, khai service + quyền trong manifest như bản này, rồi gọi `Store.init(context)`
trong `Application.onCreate`. Phần `ui/` là tuỳ chọn.
