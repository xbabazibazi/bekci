"""Bekçi uygulama ikonunu tek kaynaktan üretir.

Kaynak, markanın kalkan/fener işaretidir — Theme.swift'teki `BekciMark`
ve `ic_launcher_foreground.xml` ile aynı geometri. Burada tek yerde
tanımlanır, tüm hedef boyutlar buradan türetilir; elle PNG düzenlemek
yerine üretmek, marka değişince tek komutla tazelenebilmesi demektir.

Üretilenler:
  android/app/src/main/res/mipmap-*/ic_launcher.png       (eski cihazlar)
  android/app/src/main/res/mipmap-*/ic_launcher_round.png (yuvarlak maske)
  ios/AppIcon/AppIcon-1024.png                            (App Store)
  store/play-icon-512.png                                 (Play Store)

Çalıştırma:  python tools/ikon_uret.py
Gereken:     PyMuPDF (fitz)
"""
from __future__ import annotations

from pathlib import Path

import fitz

ROOT = Path(__file__).resolve().parent.parent

# Marka renkleri — Theme.swift / Theme.kt ile aynı
GUARD = "#0F6B4F"   # bekçi yeşili, zemin
PAPER = "#F6F4EF"   # sıcak kağıt, işaret

# Kalkan + fener işareti, 100x100'lük kutuda (BekciMark ile aynı oranlar).
MARK = """
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="{size}" height="{size}">
  <rect width="100" height="100" fill="{bg}"/>
  <g transform="translate(50 50) scale({scale}) translate(-50 -50)">
    <path d="M50 9 16 21v28c0 23 14 37 34 43 20-6 34-20 34-43V21Z" fill="{fg}"/>
    <rect x="38" y="34" width="24" height="30" rx="5" fill="none" stroke="{bg}" stroke-width="4.5"/>
    <circle cx="50" cy="49" r="4.5" fill="{bg}"/>
  </g>
</svg>
"""

# Android mipmap yoğunlukları — launcher ikonu kenar uzunlukları
MIPMAP = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def yaz(svg: str, hedef: Path, size: int) -> None:
    hedef.parent.mkdir(parents=True, exist_ok=True)
    doc = fitz.open(stream=svg.encode("utf-8"), filetype="svg")
    pix = doc.load_page(0).get_pixmap(alpha=True)
    # SVG genişliği zaten `size` verilmiş olsa da PyMuPDF yuvarlayabilir;
    # hedef boyutu garanti altına almak için gerekirse ölçekliyoruz.
    if pix.width != size or pix.height != size:
        doc2 = fitz.open()
        page = doc2.new_page(width=size, height=size)
        page.insert_image(fitz.Rect(0, 0, size, size), pixmap=pix)
        pix = page.get_pixmap(alpha=True)
    pix.save(hedef)
    print(f"  {hedef.relative_to(ROOT)}  {size}×{size}")


def main() -> None:
    print("Android mipmap (kare + yuvarlak):")
    res = ROOT / "android/app/src/main/res"
    for yogunluk, boy in MIPMAP.items():
        # Kare: kenarlara kadar zemin, işaret %64 ölçekli (Android güvenli alanı)
        yaz(MARK.format(size=boy, bg=GUARD, fg=PAPER, scale=0.64),
            res / f"mipmap-{yogunluk}/ic_launcher.png", boy)
        # Yuvarlak: aynı görsel, maske sistem tarafından uygulanır
        yaz(MARK.format(size=boy, bg=GUARD, fg=PAPER, scale=0.58),
            res / f"mipmap-{yogunluk}/ic_launcher_round.png", boy)

    # Adaptive icon ön planı BİLEREK ÜRETİLMİYOR: `drawable/
    # ic_launcher_foreground.xml` vektörü zaten var ve API 26+ (minSdk 26)
    # tüm cihazlarda adaptive icon kullanılıyor. Aynı adla bir .png üretmek
    # "duplicate resource" derleme hatası verir; vektör hem daha küçük hem
    # her maske boyutunda net.

    print("\nMağaza varlıkları:")
    yaz(MARK.format(size=512, bg=GUARD, fg=PAPER, scale=0.64),
        ROOT / "store/play-icon-512.png", 512)
    # App Store ikonu ŞEFFAF OLAMAZ ve köşe yuvarlatma Apple tarafından
    # uygulanır — bu yüzden tam kare, opak zemin. Doğrudan asset
    # catalog'a yazılır ki Xcode'da elle sürükleme adımı olmasın.
    yaz(MARK.format(size=1024, bg=GUARD, fg=PAPER, scale=0.64),
        ROOT / "ios/BekciApp/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png", 1024)

    print("\nBitti.")


if __name__ == "__main__":
    main()
