"""Play Store mağaza görsellerini üretir.

Play, uygulama ikonundan ayrı olarak bir **özellik grafiği** (feature
graphic, 1024×500) ister; bu, mağaza sayfasının en üstünde görünen
bannerdır ve olmadan liste yayınlanamaz.

Markanın kendi paletiyle üretilir — Theme.kt'deki değerler.

Çalıştırma:  python tools/magaza_gorsel_uret.py
Gereken:     PyMuPDF (fitz)
"""
from __future__ import annotations

from pathlib import Path

import fitz

ROOT = Path(__file__).resolve().parent.parent
STORE = ROOT / "store"

GUARD = "#0F6B4F"   # bekçi yeşili
GUARD_DK = "#0A4A37"
PAPER = "#F6F4EF"   # sıcak kağıt
SIGNAL = "#B93A2B"  # kiremit — dolandırıcılık

# Özellik grafiği: 1024×500. Metin kasıtlı olarak AZ — Play bu görseli
# küçük boyutlarda da gösteriyor ve uzun metin okunmuyor. Ayrıca Play
# politikası görselde "İndir", "Ücretsiz" gibi çağrı metinlerini yasaklıyor.
FEATURE = """
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 500" width="1024" height="500">
  <!-- DÜZ RENK, gradyan değil: markanın kendi kuralı "altı renk, gradyan
       yok" (Theme.kt). Ayrıca PyMuPDF'in SVG çizici linearGradient'i
       desteklemiyor ve sessizce SİYAH bırakıyordu. -->
  <rect width="1024" height="500" fill="{guard}"/>

  <!-- Kalkan işareti, solda -->
  <g transform="translate(96 118) scale(2.64)">
    <path d="M50 9 16 21v28c0 23 14 37 34 43 20-6 34-20 34-43V21Z" fill="{paper}"/>
    <rect x="38" y="34" width="24" height="30" rx="5" fill="none" stroke="{guard}" stroke-width="4.5"/>
    <circle cx="50" cy="49" r="4.5" fill="{guard}"/>
  </g>

  <!-- Metin, sağda -->
  <text x="418" y="212" font-family="Helvetica,Arial,sans-serif" font-size="76"
        font-weight="bold" fill="{paper}" letter-spacing="-2">Bekçi</text>
  <text x="420" y="272" font-family="Helvetica,Arial,sans-serif" font-size="31"
        fill="{paper}" opacity="0.92">Türkçe SMS dolandırıcılık filtresi</text>

  <!-- Dolandırıcılık vurgusu: ürünün ne yaptığını tek bakışta anlatan şerit -->
  <rect x="420" y="316" width="14" height="14" rx="4" fill="{signal}"/>
  <text x="448" y="329" font-family="Helvetica,Arial,sans-serif" font-size="24"
        fill="{paper}" opacity="0.82">Sahte icra, bahis, kargo, banka taklidi</text>
  <rect x="420" y="360" width="14" height="14" rx="4" fill="{paper}" opacity="0.6"/>
  <text x="448" y="373" font-family="Helvetica,Arial,sans-serif" font-size="24"
        fill="{paper}" opacity="0.82">Sunucu yok — her şey telefonunuzda</text>
</svg>
"""


def yaz(svg: str, hedef: Path, genislik: int, yukseklik: int) -> None:
    hedef.parent.mkdir(parents=True, exist_ok=True)
    doc = fitz.open(stream=svg.encode("utf-8"), filetype="svg")
    pix = doc.load_page(0).get_pixmap(alpha=False)
    if (pix.width, pix.height) != (genislik, yukseklik):
        out = fitz.open()
        page = out.new_page(width=genislik, height=yukseklik)
        page.insert_image(fitz.Rect(0, 0, genislik, yukseklik), pixmap=pix)
        pix = page.get_pixmap(alpha=False)
    pix.save(hedef)
    print(f"  {hedef.relative_to(ROOT)}  {genislik}×{yukseklik}")


def main() -> None:
    print("Play Store görselleri:")
    yaz(
        FEATURE.format(guard=GUARD, guard_dk=GUARD_DK, paper=PAPER, signal=SIGNAL),
        STORE / "play-feature-1024x500.png", 1024, 500,
    )
    print("\nUygulama ikonu (512×512) `tools/ikon_uret.py` ile üretiliyor.")
    print("Ekran görüntüleri gerçek cihazdan alınmalı — bkz. store/PLAY-STORE.md")


if __name__ == "__main__":
    main()
