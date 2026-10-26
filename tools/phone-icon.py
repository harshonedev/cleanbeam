"""Render the Clean Beam phone remote icon (108-unit adaptive-icon grid) to PNGs.

    python3 tools/phone-icon.py remote/phone docs/images/remote-icon.png     (needs Pillow)

Writes the Android mipmaps and the iOS AppIcon set; the adaptive icon (Android 8+) is the vector
drawable/ic_launcher_foreground.xml drawn on the same grid, so change both together.
"""
import json, sys
from pathlib import Path
from PIL import Image, ImageDraw

BG, BG2 = (0x0B, 0x10, 0x20), (0x16, 0x1B, 0x2E)
BODY, BLUE, WHITE, KEY = (0x22, 0x2A, 0x4A), (0x5B, 0x8C, 0xFF), (255, 255, 255), (0x8F, 0xA8, 0xFF)


def render(px, background=True, round_mask=False):
    s = 8 * px / 108.0          # supersample
    W = int(108 * s)
    im = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    u = lambda v: v * s
    if background:
        d.rectangle([0, 0, W, W], fill=BG)
        d.polygon([(0, u(54)), (W, 0), (W, W)], fill=BG2)
    d.rounded_rectangle([u(37), u(21), u(71), u(87)], radius=u(13), fill=BODY)
    cx, cy = 54, 43
    d.ellipse([u(cx - 13), u(cy - 13), u(cx + 13), u(cy + 13)], fill=BLUE)
    d.ellipse([u(cx - 7), u(cy - 7), u(cx + 7), u(cy + 7)], fill=BODY)
    d.rectangle([u(cx - 3), u(cy - 3), u(cx + 3), u(cy + 3)], fill=WHITE)
    for y in (66, 75):
        for x in (46, 54, 62):
            d.ellipse([u(x - 2.5), u(y - 2.5), u(x + 2.5), u(y + 2.5)], fill=KEY)
    im = im.resize((px, px), Image.LANCZOS)
    if round_mask:
        m = Image.new("L", (px * 8, px * 8), 0)
        ImageDraw.Draw(m).ellipse([0, 0, px * 8, px * 8], fill=255)
        im.putalpha(m.resize((px, px), Image.LANCZOS))
    return im


def main(phone):
    phone = Path(phone)
    res = phone / "android/app/src/main/res"
    for name, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        render(px).save(res / f"mipmap-{name}/ic_launcher.png")
    ios = phone / "ios/Runner/Assets.xcassets/AppIcon.appiconset"
    for img in json.loads((ios / "Contents.json").read_text())["images"]:
        px = round(float(img["size"].split("x")[0]) * int(img["scale"][0]))
        render(px).convert("RGB").save(ios / img["filename"])   # iOS icons must not carry alpha
    render(512).convert("RGB").save(sys.argv[2])                  # preview / README copy


if __name__ == "__main__":
    main(sys.argv[1])
