# sendfileLAN (Android → PC over LAN with TLS + QR pairing)

**Chế độ A – Web Share (Android → PC)**
- App Android quét **QR** từ PC để ghép cặp (one-time token), sau đó chọn **file/folder** và tải lên PC qua **HTTPS (TLS 1.3, self‑signed)**.
- PC chạy script **Python** làm receiver, sinh QR, xác nhận (Allow) và nhận file. Tự verify **SHA‑256**.
- Tối ưu: **đa luồng tự động**, chunk stream, resume (bản sau), gom folder (ZIP "store" bản sau).

## Thư mục
```
.
├── android-app/           # Android project (Kotlin)
├── pc-receiver/           # Python HTTPS receiver (Windows/macOS/Linux)
└── .github/workflows/     # GitHub Actions build APK
```

## Cách chạy PC receiver (Windows)
```bash
cd pc-receiver
python -m venv .venv && .venv\Scripts\activate
pip install -r requirements.txt
python receiver.py
```
- Console sẽ in **Fingerprint (SHA-256)** và mở server `https://0.0.0.0:8443`.
- Mở trình duyệt PC tới `https://127.0.0.1:8443/qr` (hoặc `https://<IP_LAN>:8443/qr`) để sinh mã QR.

## Ghép cặp và gửi file từ Android
1) Mở app Android → **Quét QR** → trỏ vào QR trên PC.
2) PC console hỏi `Cho phép kết nối? (y/n)` → bấm `y`.
3) Trên Android chọn **FILE/FOLDER** để gửi → PC lưu vào `pc-receiver/inbox/`.

## Build APK qua GitHub Actions
- Push repo, vào tab **Actions** → workflow **Android CI** → tải artifact `app-debug.apk`.

## Ghi chú
- Ưu tiên Wi‑Fi 5/6; PC nối dây LAN càng ổn định.
- Lần đầu trust **self‑signed cert** theo fingerprint (TOFU).
- Resume, ZIP "store", QUIC/Cronet: sẽ thêm ở phiên bản sau.