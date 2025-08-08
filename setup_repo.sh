#!/usr/bin/env bash
set -euo pipefail
REPO_SSH="${1:-git@github.com:Tuan-NV41/sendfileLAN.git}"

echo "[1/5] Init git"
git init
git config user.name "sendfileLAN-setup"
git config user.email "setup@local"

echo "[2/5] Add files"
git add .
git commit -m "Scaffold sendfileLAN (Android + PC receiver + CI)"

echo "[3/5] Set remote"
git branch -M main || true
git remote add origin "$REPO_SSH" || git remote set-url origin "$REPO_SSH"

echo "[4/5] Push"
git push -u origin main

echo "Done. Go to GitHub → Actions to grab the APK artifact."
