#!/usr/bin/env python3
"""Build bundled Twemoji country flags for the server list.

Input: Twitter Twemoji v14.0.2 source ZIP (CC BY 4.0), e.g.:
  curl -fL -o /tmp/twemoji-14.zip https://github.com/twitter/twemoji/archive/refs/tags/v14.0.2.zip
  python3 tools/generate-flags.py /tmp/twemoji-14.zip
Dependencies: pip install cairosvg pillow

Outputs: 258 lossless WebP flag images (72x72), plus a static Kotlin map of resources.
They are bundled in the APK, so flags render correctly even on phones whose emoji
font displays regional indicator pairs as two plain Latin letters. No CDN/runtime
network request is used. Attribution/license: app/src/main/assets/licenses/LICENSE-twemoji.txt.
"""
import io
import re
import sys
import zipfile
from pathlib import Path

import cairosvg
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
DRAWABLES = ROOT / "app/src/main/res/drawable-nodpi"
MAP = ROOT / "app/src/main/java/nzvpn/nzbozorg/ir/ui/components/FlagDrawables.kt"
LICENSE = ROOT / "app/src/main/assets/licenses/LICENSE-twemoji.txt"
PATTERN = re.compile(r"assets/svg/(1f1[ef][0-9a-f])-(1f1[ef][0-9a-f])\.svg$")


def main(archive: Path) -> None:
    DRAWABLES.mkdir(parents=True, exist_ok=True)
    LICENSE.parent.mkdir(parents=True, exist_ok=True)
    flags = []
    with zipfile.ZipFile(archive) as source:
        for path in source.namelist():
            match = PATTERN.search(path)
            if not match:
                continue
            points = [int(point, 16) for point in match.groups()]
            if any(not 0x1F1E6 <= point <= 0x1F1FF for point in points):
                continue
            country = "".join(chr(point - 0x1F1E6 + ord("A")) for point in points)
            svg = source.read(path)
            png = cairosvg.svg2png(bytestring=svg, output_width=72, output_height=72)
            image = Image.open(io.BytesIO(png)).convert("RGBA")
            image.save(DRAWABLES / f"flag_{country.lower()}.webp", format="WEBP", lossless=True)
            flags.append(country)

        license_text = source.read("twemoji-14.0.2/LICENSE-GRAPHICS")
        LICENSE.write_text(
            "Twemoji graphics by Twitter / X (https://github.com/twitter/twemoji)\n"
            "Licensed under Creative Commons Attribution 4.0 International (CC BY 4.0).\n"
            "Flag imagery generated from Twemoji v14.0.2 assets/svg/*.svg.\n"
            "\n" + license_text.decode("utf-8"),
            encoding="utf-8",
        )

    assert len(flags) == 258, f"Expected all regional flag pairs, found {len(flags)}"
    cases = "\n".join(
        f'        "{code}" -> R.drawable.flag_{code.lower()}' for code in sorted(flags)
    )
    MAP.write_text(
        """package nzvpn.nzbozorg.ir.ui.components

import nzvpn.nzbozorg.ir.R

/** پرچم‌های ایموجی Twemoji؛ دارایی محلی، بی‌نیاز از فونت ایموجی گوشی. */
internal fun flagDrawable(code: String): Int? = when (code) {
""" + cases + "\n        else -> null\n    }\n",
        encoding="utf-8",
    )
    print(f"Generated {len(flags)} flags (72px, WebP) and {MAP}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python3 tools/generate-flags.py /path/to/twemoji-14.zip")
    main(Path(sys.argv[1]))
