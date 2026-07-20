#!/usr/bin/env python3
"""生成中国象棋 App 图标：深棕木纹底 + 红方"帅"字棋子（与游戏内棋子风格一致）。

用法: python tools/generate_icons.py
输出: 覆盖 app/src/main/res/mipmap-*/ 下的 ic_launcher*.png，
     并更新 mipmap-anydpi-v26 的自适应图标 XML。
"""
import math
import os
import shutil

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
FONT_PATH = "C:/Windows/Fonts/STKAITI.TTF"  # 华文楷体，与游戏棋子字体一致

# 游戏配色（见 ui/theme/Color.kt 与 BoardCanvas.kt）
BG_INNER = (0x2D, 0x1F, 0x14)   # 径向光晕
BG_OUTER = (0x0F, 0x0C, 0x0A)   # 深棕黑
PIECE_LIGHT = (0xFF, 0xF8, 0xE7)
PIECE_DARK = (0xE8, 0xD5, 0xB0)
RED_OUTER = (0x9B, 0x1C, 0x1C)
RED_INNER = (0xC0, 0x39, 0x2B)
RED_TEXT = (0xB9, 0x1C, 0x1C)
GOLD = (0xD4, 0xA0, 0x17)

LEGACY_SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
ADAPTIVE_SIZES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}


def radial_gradient(size, inner, outer, center=(0.5, 0.42), radius=0.78):
    """小尺寸渲染径向渐变后放大，避免逐像素大循环。"""
    small = 128
    img = Image.new("RGB", (small, small))
    px = img.load()
    cx, cy = center[0] * (small - 1), center[1] * (small - 1)
    maxd = radius * small
    for y in range(small):
        for x in range(small):
            d = min(1.0, math.hypot(x - cx, y - cy) / maxd)
            px[x, y] = tuple(int(inner[i] + (outer[i] - inner[i]) * d) for i in range(3))
    return img.resize((size, size), Image.BICUBIC)


def draw_piece(diameter):
    """绘制一枚红方帅字棋子，返回 RGBA 图（4x 超采样后缩小）。"""
    ss = 4
    d = diameter * ss
    img = Image.new("RGBA", (d, d), (0, 0, 0, 0))
    cx = cy = d / 2
    r = d / 2 * 0.97

    # 投影
    shadow = Image.new("RGBA", (d, d), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).ellipse(
        [cx - r, cy - r + d * 0.03, cx + r, cy + r + d * 0.03], fill=(0, 0, 0, 110)
    )
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(d * 0.02)))

    # 棋子底盘：米白径向渐变（光源在左上）
    base = radial_gradient(d, PIECE_LIGHT, PIECE_DARK, center=(0.38, 0.34), radius=0.85).convert("RGBA")
    mask = Image.new("L", (d, d), 0)
    ImageDraw.Draw(mask).ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)
    img.paste(base, (0, 0), mask)

    draw = ImageDraw.Draw(img)
    # 外圈 / 内圈描边
    w_outer = max(1, int(d * 0.022))
    w_inner = max(1, int(d * 0.013))
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=RED_OUTER, width=w_outer)
    ri = r * 0.78
    draw.ellipse([cx - ri, cy - ri, cx + ri, cy + ri], outline=RED_INNER, width=w_inner)

    # 帅字（楷体，视觉中心略上移）
    font = ImageFont.truetype(FONT_PATH, int(d * 0.52))
    draw.text((cx, cy * 0.98), "帅", font=font, fill=RED_TEXT, anchor="mm")

    return img.resize((diameter, diameter), Image.LANCZOS)


def make_background(size):
    return radial_gradient(size, BG_INNER, BG_OUTER).convert("RGBA")


def make_legacy(size):
    """方形图标：满底背景 + 居中棋子。"""
    img = make_background(size)
    piece = draw_piece(int(size * 0.68))
    img.alpha_composite(piece, ((size - piece.width) // 2, (size - piece.height) // 2))
    return img


def make_round(size):
    """圆形图标：在方形基础上套圆形蒙版。"""
    ss = 4
    big = make_legacy(size * ss)
    mask = Image.new("L", (size * ss, size * ss), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size * ss, size * ss], fill=255)
    out = Image.new("RGBA", (size * ss, size * ss), (0, 0, 0, 0))
    out.paste(big, (0, 0), mask)
    return out.resize((size, size), Image.LANCZOS)


def make_foreground(size):
    """自适应图标前景：透明底，棋子控制在安全区内（直径 ~60%）。"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    piece = draw_piece(int(size * 0.60))
    img.alpha_composite(piece, ((size - piece.width) // 2, (size - piece.height) // 2))
    return img


ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
"""


def main():
    for dpi, size in LEGACY_SIZES.items():
        out_dir = os.path.join(ROOT, f"mipmap-{dpi}")
        os.makedirs(out_dir, exist_ok=True)
        make_legacy(size).save(os.path.join(out_dir, "ic_launcher.png"))
        make_round(size).save(os.path.join(out_dir, "ic_launcher_round.png"))
    for dpi, size in ADAPTIVE_SIZES.items():
        out_dir = os.path.join(ROOT, f"mipmap-{dpi}")
        os.makedirs(out_dir, exist_ok=True)
        make_background(size).save(os.path.join(out_dir, "ic_launcher_background.png"))
        make_foreground(size).save(os.path.join(out_dir, "ic_launcher_foreground.png"))

    # 更新自适应图标 XML
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        with open(os.path.join(ROOT, "mipmap-anydpi-v26", name), "w", encoding="utf-8") as f:
            f.write(ADAPTIVE_XML)

    # 清理旧资源（webp 与模板矢量图）
    for dpi in list(LEGACY_SIZES):
        for name in ("ic_launcher.webp", "ic_launcher_round.webp"):
            p = os.path.join(ROOT, f"mipmap-{dpi}", name)
            if os.path.exists(p):
                os.remove(p)
    for name in ("ic_launcher_background.xml", "ic_launcher_foreground.xml"):
        p = os.path.join(ROOT, "drawable", name)
        if os.path.exists(p):
            os.remove(p)

    # 预览图
    os.makedirs(os.path.dirname(os.path.abspath(__file__)), exist_ok=True)
    make_legacy(512).save(os.path.join(os.path.dirname(__file__), "icon_preview.png"))
    print("done")


if __name__ == "__main__":
    main()
