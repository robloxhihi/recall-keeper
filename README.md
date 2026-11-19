# Recall Keeper

Giữ lại tin nhắn Messenger / Facebook **đã tới máy bạn** kể cả khi người gửi thu hồi, và gắn nhãn
phân biệt với tin thường. Lưu tại máy, không có quyền INTERNET.

Báo cáo kỹ thuật đầy đủ, giới hạn thật và kế hoạch kiểm thử: [docs/REPORT.md](docs/REPORT.md).

## Nhãn

| Nhãn | Nghĩa |
|---|---|
| ĐÃ THU HỒI | Messenger đổi thông báo thành "đã thu hồi một tin nhắn" — có bằng chứng kèm theo |
| NGHI THU HỒI | Thông báo bị chính app xoá, không do bạn bấm hay gạt. Cũng có thể chỉ là bạn đã đọc |
| TIN THƯỜNG | Tin nhắn bình thường |

## Cài và chạy

1. Tải `RecallKeeper-*.apk` ở mục **Releases**, cài (cho phép cài từ nguồn ngoài).
2. Mở app, bấm **Cấp quyền truy cập thông báo**, bật **Recall Keeper**.
3. Quay lại app, thấy **Đang hoạt động** là xong.
4. Có tin nhắn tới → app tự lưu. Người gửi thu hồi → tin đổi nhãn.

Đối chiếu `HASHES.txt` (SHA-256 + chữ ký) và `PERMISSIONS.txt` trước khi cài.

## Không làm được

Không xem tin người khác **chưa gửi** — nội dung đó nằm trên máy họ, không quyền Android nào đọc
được. Không cứu được tin khi **máy tắt nguồn**, khi hội thoại **tắt thông báo**, hoặc khi bạn đang
mở đúng hội thoại đó. Chi tiết trong báo cáo.

## Build

CI-only. Push lên `main` hoặc đẩy tag `v*` → GitHub Actions build, kiểm tra không có quyền
`INTERNET`, tính hash, rồi đính APK vào Release.

```
gradle assembleRelease
```
