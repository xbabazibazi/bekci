# Bağlı Android cihazdan mağaza ekran görüntüsü alır.
#
# Emülatör bu makinede çalışmıyor (hypervisor sürücüsü kurulu değil), bu
# yüzden görüntüler GERÇEK cihazdan alınıyor — Play Store için zaten daha
# iyisi.
#
# Kullanım:
#   .\tools\ekran_goruntusu.ps1 -Ad "01-kutu"
#   .\tools\ekran_goruntusu.ps1 -Ad "02-uyari"
#
# Görüntüler store/ekran-goruntuleri/ altına kaydedilir.

param(
    [Parameter(Mandatory = $true)][string]$Ad,
    [switch]$Kur   # -Kur verilirse önce güncel APK'yı yükler
)

$ErrorActionPreference = "Stop"
$kok = Split-Path -Parent $PSScriptRoot
$adb = "C:\AndroidBuildTools\sdk\platform-tools\adb.exe"
$hedefDizin = Join-Path $kok "store\ekran-goruntuleri"

if (-not (Test-Path $adb)) { throw "adb bulunamadı: $adb" }

# Cihaz bağlı mı? "unauthorized" ayrı bir durum: kablo takılı ama telefonda
# "USB hata ayıklamaya izin ver" onayı verilmemiş demektir.
$cihazlar = & $adb devices | Select-Object -Skip 1 | Where-Object { $_.Trim() -ne "" }
if (-not $cihazlar) {
    throw "Cihaz bulunamadı. USB kablosunu takıp telefonda hata ayıklamaya izin verin."
}
if ($cihazlar -match "unauthorized") {
    throw "Cihaz yetkisiz. Telefondaki 'USB hata ayıklamaya izin ver' penceresini onaylayın."
}
Write-Host "Cihaz: $($cihazlar[0])" -ForegroundColor Green

if ($Kur) {
    $apk = Get-ChildItem (Join-Path $kok "dagitim") -Filter "*.apk" |
           Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $apk) { throw "dagitim/ altında APK yok." }
    Write-Host "Kuruluyor: $($apk.Name)"
    # -r: mevcut kurulumun üzerine yaz, veriyi koru.
    & $adb install -r $apk.FullName
}

New-Item -ItemType Directory -Force $hedefDizin | Out-Null
$hedef = Join-Path $hedefDizin "$Ad.png"

# exec-out: ikili veriyi doğrudan alır. `adb shell screencap > dosya`
# Windows'ta satır sonu dönüşümü yüzünden PNG'yi BOZAR.
& $adb exec-out screencap -p > $hedef

$boyut = (Get-Item $hedef).Length
if ($boyut -lt 5000) {
    Remove-Item $hedef
    throw "Görüntü alınamadı (dosya çok küçük). Ekran kilitli olabilir."
}

# Play, telefon görüntüsünde en az 320px kenar ve 16:9–9:16 arası oran ister.
Add-Type -AssemblyName System.Drawing
$img = [System.Drawing.Image]::FromFile($hedef)
$olcu = "$($img.Width)×$($img.Height)"
$img.Dispose()

Write-Host "Kaydedildi: store\ekran-goruntuleri\$Ad.png  ($olcu, $([math]::Round($boyut/1KB)) KB)" -ForegroundColor Green
