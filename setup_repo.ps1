param(
  [string]$Repo = "git@github.com:Tuan-NV41/sendfileLAN.git"
)

Write-Host "[1/5] Init git"
git init
git config user.name "sendfileLAN-setup"
git config user.email "setup@local"

Write-Host "[2/5] Add files"
git add .
git commit -m "Scaffold sendfileLAN (Android + PC receiver + CI)"

Write-Host "[3/5] Set remote"
git branch -M main
try { git remote add origin $Repo } catch { git remote set-url origin $Repo }

Write-Host "[4/5] Push"
git push -u origin main

Write-Host "Done. Open GitHub → Actions to download APK."
