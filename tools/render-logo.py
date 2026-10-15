#!/usr/bin/env python3
"""Render Android icon assets from the user-approved NZVPN concept.

Run from anywhere: python3 tools/render-logo.py
Requires Pillow (pip install Pillow). The master artwork lives at
logo/nzvpn-concept.png and remains untouched. Outputs a transparent color
foreground, a white silhouette for Android themed icons, legacy mipmaps and a
color bitmap for the expanded notification. Android requires a monochrome small
status-bar icon; that simplified vector is app/src/main/res/drawable/ic_stat_vpn.xml.
"""
from collections import Counter
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
MASTER = ROOT / "logo/nzvpn-concept.png"
NODPI = RES / "drawable-nodpi"


def save_webp(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="WEBP", lossless=True, method=5)


def circle_mask(size: int) -> Image.Image:
    big = size * 4
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, big - 1, big - 1), fill=255)
    return mask.resize((size, size), Image.Resampling.LANCZOS)


def main() -> None:
    original = Image.open(MASTER).convert("RGB")
    assert original.size == (1024, 1024), "Expected the selected 1024x1024 concept image"
    background = Counter(original.getdata()).most_common(1)[0][0]
    # The master has an almost-uniform dark field (#181d21) and a copper mark.
    # 34..58 is the anti-aliased transition to the artwork. Treat the field as
    # transparent instead of baking a square into the adaptive foreground.
    rgba = Image.new("RGBA", original.size, (0, 0, 0, 0))
    pixels_in = original.load()
    pixels_out = rgba.load()
    for y in range(original.height):
        for x in range(original.width):
            r, g, b = pixels_in[x, y]
            v = max(r, g, b)
            if v <= 34:
                continue
            alpha = min(255, max(0, round((v - 34) * 255 / 24)))
            if alpha == 0:
                continue
            if alpha < 255:
                # Remove the sampled dark field from partially transparent edges.
                r, g, b = (
                    min(255, max(0, round((c - (1 - alpha / 255) * base) * 255 / alpha)))
                    for c, base in zip((r, g, b), background)
                )
            pixels_out[x, y] = (r, g, b, alpha)

    # 432/108=4:1 supplies full XXXHDPI detail for adaptive launcher icons.
    # Shrink to 92% of the canvas so even the diagonal corners of the N and
    # arrowhead stay inside Android's 66dp circular safe area when mask shapes vary.
    full = rgba.resize((432, 432), Image.Resampling.LANCZOS)
    scaled = full.resize((398, 398), Image.Resampling.LANCZOS)
    foreground = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    foreground.paste(scaled, ((432 - 398) // 2, (432 - 398) // 2))
    save_webp(foreground, NODPI / "nz_logo_foreground.webp")
    save_webp(rgba.resize((256, 256), Image.Resampling.LANCZOS), NODPI / "nz_logo_mark.webp")

    # Android 13+ themed launcher icon: alpha silhouette, system supplies the tint.
    white = Image.new("RGBA", foreground.size, "white")
    white.putalpha(foreground.getchannel("A"))
    save_webp(white, NODPI / "nz_logo_monochrome.webp")

    square = original.resize((192, 192), Image.Resampling.LANCZOS).convert("RGBA")
    save_webp(square, NODPI / "nz_logo_notification.webp")

    for folder, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96),
                         ("xxhdpi", 144), ("xxxhdpi", 192)):
        icon = original.resize((size, size), Image.Resampling.LANCZOS).convert("RGBA")
        output = RES / f"mipmap-{folder}"
        save_webp(icon, output / "ic_launcher.webp")
        round_icon = icon.copy()
        round_icon.putalpha(circle_mask(size))
        save_webp(round_icon, output / "ic_launcher_round.webp")
    print("Icon assets regenerated from", MASTER.relative_to(ROOT))
    print("Background color:", "#%02x%02x%02x" % background)
    print("Foreground 432px bbox:", foreground.getchannel("A").getbbox())


if __name__ == "__main__":
    main()
