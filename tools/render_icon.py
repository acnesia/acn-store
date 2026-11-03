"""Render the ACN Store launcher icon to PNG/WebP with the same geometry as the adaptive vector
(res/drawable/ic_launcher_*.xml, 108x108 viewport).

Writes branding/icon-512.png (catalog, GitHub) and the legacy mipmap WebPs.
Usage: python tools/render_icon.py
"""
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

STORE_DIR = Path(__file__).resolve().parent.parent
RES = STORE_DIR / "app" / "src" / "main" / "res"
SS = 2048  # supersampled canvas
K = SS / 108


def p(*xy):
    return [v * K for v in xy]


def render() -> Image.Image:
    # Diagonal gradient #12B07D -> #01674A
    a, b = (0x12, 0xB0, 0x7D), (0x01, 0x67, 0x4A)
    ramp = Image.linear_gradient("L").resize((SS, SS))
    diag = ImageChops.add(ramp, ramp.rotate(90), scale=2)
    bg = Image.composite(Image.new("RGB", (SS, SS), b), Image.new("RGB", (SS, SS), a), diag).convert("RGBA")

    mask = Image.new("L", (SS, SS), 0)
    d = ImageDraw.Draw(mask)
    # Rounded trapezoid body: straight edges joined by quadratic corners (same points as the vector)
    body = [(39, 44), (69, 44), ("Q", (72, 44), (72.41, 46.97)), (76.31, 75.05), ("Q", (77, 80), (72, 80)),
            (36, 80), ("Q", (31, 80), (31.69, 75.05)), (35.59, 46.97), ("Q", (36, 44), (39, 44))]
    pts, cur = [], None
    for seg in body:
        if seg[0] == "Q":
            (cx, cy), (ex, ey) = seg[1], seg[2]
            for i in range(1, 17):
                t = i / 16
                pts.append(((1 - t) ** 2 * cur[0] + 2 * (1 - t) * t * cx + t * t * ex,
                            (1 - t) ** 2 * cur[1] + 2 * (1 - t) * t * cy + t * t * ey))
            cur = (ex, ey)
        else:
            pts.append(seg)
            cur = seg
    d.polygon([(x * K, y * K) for x, y in pts], fill=255)
    # Cut-out: download arrow
    d.rectangle(p(50.5, 55, 57.5, 64), fill=0)
    d.polygon([tuple(p(45, 64)), tuple(p(63, 64)), tuple(p(54, 73))], fill=0)
    # Handle: r9 half circle + stubs into the body top, stroke 4, butt caps.
    # PIL strokes arcs inward from the bbox, so the bbox is the outer edge (r 11)
    w = 4 * K
    d.arc(p(43, 31, 65, 53), start=180, end=360, fill=255, width=int(w))
    for x in (45, 63):
        d.line(p(x, 42, x, 47), fill=255, width=int(w))
    bg.paste((255, 255, 255, 255), (0, 0), mask)
    return bg


def main():
    full = render()
    branding = STORE_DIR / "branding"
    branding.mkdir(exist_ok=True)
    full.resize((512, 512), Image.LANCZOS).save(branding / "icon-512.png", optimize=True)

    # Legacy launchers: rounded square and circle crops of the full-bleed art (center 72/108)
    crop = full.crop((int(18 * K), int(18 * K), int(90 * K), int(90 * K)))
    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for density, px in sizes.items():
        big = crop.resize((px * 4, px * 4), Image.LANCZOS)
        for name, shape in (("ic_launcher", "square"), ("ic_launcher_round", "circle")):
            m = Image.new("L", big.size, 0)
            md = ImageDraw.Draw(m)
            if shape == "circle":
                md.ellipse([0, 0, big.width - 1, big.height - 1], fill=255)
            else:
                md.rounded_rectangle([0, 0, big.width - 1, big.height - 1], radius=big.width * 0.22, fill=255)
            out = Image.new("RGBA", big.size, (0, 0, 0, 0))
            out.paste(big, (0, 0), m)
            out.resize((px, px), Image.LANCZOS).save(RES / f"mipmap-{density}" / f"{name}.webp", "WEBP", lossless=True)
    print(f"[Icon] {branding / 'icon-512.png'} + legacy mipmaps")


if __name__ == "__main__":
    main()
