#!/usr/bin/env python3
"""Minimal PNG decoder + dominant-colour report. No third-party deps.

Reproduces the measurement behind decision #132: run it against the community's
logo and favicon to re-derive the two brand hue clusters (~218deg and ~250deg).

    python3 extract-brand-colors.py logo.png "LOGO"
"""
import sys, zlib, struct
from collections import Counter

def decode(path):
    d = open(path, "rb").read()
    assert d[:8] == b"\x89PNG\r\n\x1a\n", "not a PNG"
    pos, idat, pal, trns = 8, b"", None, None
    w = h = depth = ctype = interlace = None
    while pos < len(d):
        ln = struct.unpack(">I", d[pos:pos+4])[0]
        typ = d[pos+4:pos+8]
        body = d[pos+8:pos+8+ln]
        if typ == b"IHDR":
            w, h, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
        elif typ == b"PLTE":
            pal = body
        elif typ == b"tRNS":
            trns = body
        elif typ == b"IDAT":
            idat += body
        elif typ == b"IEND":
            break
        pos += 12 + ln
    assert depth == 8, f"unsupported bit depth {depth}"
    assert interlace == 0, "interlaced PNG unsupported"
    nch = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    raw = zlib.decompress(idat)
    stride = w * nch
    out, prev = [], bytearray(stride)
    p = 0
    for _ in range(h):
        f = raw[p]; p += 1
        line = bytearray(raw[p:p+stride]); p += stride
        for i in range(stride):
            a = line[i-nch] if i >= nch else 0
            b = prev[i]
            c = prev[i-nch] if i >= nch else 0
            if f == 1:   line[i] = (line[i] + a) & 255
            elif f == 2: line[i] = (line[i] + b) & 255
            elif f == 3: line[i] = (line[i] + (a + b) // 2) & 255
            elif f == 4:
                pp = a + b - c
                pa, pb, pc = abs(pp-a), abs(pp-b), abs(pp-c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        out.append(bytes(line)); prev = line
    return w, h, ctype, nch, pal, trns, out

def pixels(path):
    w, h, ctype, nch, pal, trns, rows = decode(path)
    for row in rows:
        for x in range(w):
            px = row[x*nch:(x+1)*nch]
            if ctype == 6:   r, g, b, a = px
            elif ctype == 2: r, g, b = px; a = 255
            elif ctype == 3:
                i = px[0]; r, g, b = pal[i*3:i*3+3]
                a = trns[i] if trns and i < len(trns) else 255
            elif ctype == 0: r = g = b = px[0]; a = 255
            else:            r = g = b = px[0]; a = px[1]
            yield r, g, b, a

def hexs(c): return "#%02x%02x%02x" % c

def report(path, label):
    total = 0
    exact = Counter()
    for r, g, b, a in pixels(path):
        if a < 128:
            continue
        total += 1
        exact[(r, g, b)] += 1
    print(f"\n{'='*62}\n{label}  —  {total} opaque pixels\n{'='*62}")

    print("\nMost frequent exact colours:")
    for c, n in exact.most_common(12):
        print(f"  {hexs(c):9} {n*100.0/total:5.1f}%  rgb{c}")

    # Chromatic only: drop near-greyscale and near-black/white to find the real hues.
    chroma = Counter()
    for (r, g, b), n in exact.items():
        mx, mn = max(r, g, b), min(r, g, b)
        if mx - mn < 28 or mx < 40 or mn > 225:
            continue
        chroma[(r // 16 * 16 + 8, g // 16 * 16 + 8, b // 16 * 16 + 8)] += n
    csum = sum(chroma.values())
    print(f"\nReal hues (greys and extremes dropped) — {csum*100.0/total:.1f}% of the image:")
    for c, n in chroma.most_common(10):
        r, g, b = c
        mx, mn = max(c), min(c)
        # hue in degrees
        if mx == mn: hue = 0
        elif mx == r: hue = (60 * ((g - b) / (mx - mn)) + 360) % 360
        elif mx == g: hue = 60 * ((b - r) / (mx - mn)) + 120
        else:         hue = 60 * ((r - g) / (mx - mn)) + 240
        sat = 0 if mx == 0 else (mx - mn) / mx
        print(f"  {hexs(c):9} {n*100.0/csum:5.1f}% de lo cromatico   hue {hue:5.0f}deg  sat {sat:.2f}")

for path, label in [(sys.argv[1], sys.argv[2])]:
    report(path, label)
