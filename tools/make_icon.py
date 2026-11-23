#!/usr/bin/env python3
"""Gera o icone do app (squircle escuro + anel prata + seta azul) em todos os mipmaps."""

import os

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'res')
S = 1024


def gradient(size, top, bottom):
    image = Image.new('RGBA', size)
    draw = ImageDraw.Draw(image)
    for y in range(size[1]):
        t = y / max(1, size[1] - 1)
        color = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,)
        draw.line([(0, y), (size[0], y)], fill=color)
    return image


def squircle_mask(size, inset, radius):
    mask = Image.new('L', size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [inset, inset, size[0] - inset, size[1] - inset], radius=radius, fill=255)
    return mask


def build():
    canvas = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    inset = 40
    radius = 232

    body = gradient((S, S), (27, 40, 54), (8, 13, 20))
    canvas.paste(body, (0, 0), squircle_mask((S, S), inset, radius))

    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle([inset, inset, S - inset, S - inset], radius=radius,
                           outline=(58, 78, 100, 190), width=6)
    draw.rounded_rectangle([inset + 6, inset + 6, S - inset - 6, S - inset - 6],
                           radius=radius - 6, outline=(120, 150, 185, 40), width=4)

    ring_box = [170, 290, 610, 730]
    ring_mask = Image.new('L', (S, S), 0)
    ImageDraw.Draw(ring_mask).arc(ring_box, start=45, end=315, fill=255, width=96)
    canvas.paste(gradient((S, S), (255, 255, 255), (170, 183, 199)), (0, 0), ring_mask)

    gloss = Image.new('L', (S, S), 0)
    ImageDraw.Draw(gloss).arc(ring_box, start=112, end=248, fill=110, width=26)
    canvas.paste(Image.new('RGBA', (S, S), (255, 255, 255, 255)), (0, 0), gloss)

    arrow_mask = Image.new('L', (S, S), 0)
    arrow_draw = ImageDraw.Draw(arrow_mask)
    arrow_draw.rounded_rectangle([330, 478, 560, 548], radius=35, fill=255)
    arrow_draw.polygon([(540, 420), (540, 606), (800, 513)], fill=255)

    shadow = Image.new('L', (S, S), 0)
    shadow.paste(arrow_mask, (0, 18))
    shadow = shadow.filter(ImageFilter.GaussianBlur(24))
    canvas.paste(Image.new('RGBA', (S, S), (0, 0, 0, 255)), (0, 0),
                 shadow.point(lambda value: int(value * 0.45)))

    canvas.paste(gradient((S, S), (92, 190, 255), (10, 95, 216)), (0, 0), arrow_mask)

    lip = Image.new('L', (S, S), 0)
    ImageDraw.Draw(lip).rounded_rectangle([330, 478, 560, 508], radius=26, fill=70)
    canvas.paste(Image.new('RGBA', (S, S), (255, 255, 255, 255)), (0, 0), lip)

    return canvas


def main():
    icon = build()
    targets = {
        'mipmap-mdpi': 48,
        'mipmap-hdpi': 72,
        'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi': 192,
    }
    for folder, size in targets.items():
        path = os.path.join(RES, folder)
        os.makedirs(path, exist_ok=True)
        icon.resize((size, size), Image.LANCZOS).save(
            os.path.join(path, 'ic_launcher.png'), optimize=True)
        print('ok', folder, size)

    drawable = os.path.join(RES, 'drawable')
    os.makedirs(drawable, exist_ok=True)
    icon.resize((256, 256), Image.LANCZOS).save(
        os.path.join(drawable, 'ic_logo.png'), optimize=True)
    print('ok drawable/ic_logo.png 256')


if __name__ == '__main__':
    main()
