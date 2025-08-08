import os, ssl, secrets, hashlib, socket
from pathlib import Path
from typing import Optional
from fastapi import FastAPI, Request, Header, HTTPException, UploadFile, File
from fastapi.responses import PlainTextResponse, FileResponse
import uvicorn, qrcode

APP_DIR = Path(__file__).parent
CERT_FILE = APP_DIR / "cert.pem"
KEY_FILE = APP_DIR / "key.pem"
DEST_DIR = APP_DIR / "inbox"
DEST_DIR.mkdir(exist_ok=True)

app = FastAPI()
PAIR_TOKEN = None
SESSION_TOKEN = None


def local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
    except Exception:
        ip = "127.0.0.1"
    finally:
        s.close()
    return ip


def ensure_cert():
    if CERT_FILE.exists() and KEY_FILE.exists():
        return
    from OpenSSL import crypto
    k = crypto.PKey()
    k.generate_key(crypto.TYPE_RSA, 2048)
    cert = crypto.X509()
    cert.get_subject().CN = local_ip()
    cert.set_serial_number(secrets.randbits(64))
    cert.gmtime_adj_notBefore(0)
    cert.gmtime_adj_notAfter(365 * 24 * 60 * 60)
    cert.set_issuer(cert.get_subject())
    cert.set_pubkey(k)
    cert.sign(k, "sha256")
    with open(CERT_FILE, "wb") as f:
        f.write(crypto.dump_certificate(crypto.FILETYPE_PEM, cert))
    with open(KEY_FILE, "wb") as f:
        f.write(crypto.dump_privatekey(crypto.FILETYPE_PEM, k))


def cert_fingerprint():
    der = ssl.PEM_cert_to_DER_cert(open(CERT_FILE, "rt").read())
    return hashlib.sha256(der).hexdigest()


@app.get("/qr", response_class=FileResponse)
def get_qr():
    global PAIR_TOKEN
    PAIR_TOKEN = secrets.token_urlsafe(24)
    url = f"https://{local_ip()}:8443/pair?token={PAIR_TOKEN}"
    img = qrcode.make(url)
    out = APP_DIR / "pair.png"
    img.save(out)
    return FileResponse(str(out), media_type="image/png")


@app.post("/pair")
def pair(request: Request, token: Optional[str] = None):
    global SESSION_TOKEN
    if token != PAIR_TOKEN:
        raise HTTPException(401, "Invalid or expired token")
    print("\n=== YÊU CẦU GHÉP CẶP TỪ ANDROID ===")
    print("Fingerprint (PC):", cert_fingerprint())
    ans = input("Cho phép kết nối? (y/n): ").strip().lower()
    if ans != "y":
        raise HTTPException(403, "Denied by user")
    SESSION_TOKEN = secrets.token_urlsafe(32)
    return PlainTextResponse("paired", headers={"X-Session-Token": SESSION_TOKEN})


@app.put("/upload")
async def upload(
    name: str,
    size: int,
    sha256: str,
    authorization: Optional[str] = Header(None),
    file: UploadFile = File(...),
):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "Missing token")
    token = authorization.split(" ", 1)[1]
    if token != SESSION_TOKEN:
        raise HTTPException(403, "Invalid token")

    dest = DEST_DIR / name
    hasher = hashlib.sha256()
    written = 0
    with open(dest, "wb") as f:
        while True:
            chunk = await file.read(1 << 20)  # 1MB
            if not chunk:
                break
            f.write(chunk)
            hasher.update(chunk)
            written += len(chunk)
    if written != size:
        raise HTTPException(400, f"Size mismatch: {written} != {size}")
    if hasher.hexdigest() != sha256:
        raise HTTPException(400, "Checksum mismatch")

    return {"ok": True, "saved": str(dest)}


if __name__ == "__main__":
    ensure_cert()
    print("Fingerprint (SHA-256):", cert_fingerprint())
    print("Sinh QR tại: https://<IP>:8443/qr")
    uvicorn.run(
        app,
        host="0.0.0.0",
        port=8443,
        ssl_keyfile=str(KEY_FILE),
        ssl_certfile=str(CERT_FILE),
    )