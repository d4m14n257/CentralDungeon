#!/usr/bin/env python3
"""Builds the CentralDungeon design-system bundle.

This file is the source of truth for every design token value (decision #130).
Editing a colour here and re-running is the only supported way to change the
palette: `out/theme.css` is what gets transcribed into the frontend's @theme,
and every contrast pair is measured on each run so a change that breaks WCAG AA
fails loudly instead of shipping.

Only this script is versioned; `out/` is regenerated. Same rule as docs/diagramas.
"""
import os, sys

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out")

# ---------- contrast math (WCAG 2.1) ----------
def _srgb(c):
    c = c / 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

def lum(h):
    h = h.lstrip("#")
    r, g, b = (int(h[i:i+2], 16) for i in (0, 2, 4))
    return 0.2126 * _srgb(r) + 0.7152 * _srgb(g) + 0.0722 * _srgb(b)

def ratio(a, b):
    la, lb = lum(a), lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)

def r2(a, b):
    return round(ratio(a, b), 2)

def badge_aa(v):
    return "AA" if v >= 4.5 else ("AA-lg" if v >= 3 else "FAIL")

# ---------- palette ----------
NEUTRAL = {
    "50": "#f8fafc", "100": "#f1f5f9", "200": "#e2e8f0", "300": "#cbd5e1",
    "400": "#94a3b8", "500": "#64748b", "600": "#475569", "700": "#334155",
    "800": "#1e293b", "900": "#0f172a", "950": "#020617",
}

# Measured from the community's own assets, not chosen:
#   links.centraldungeon.org gradient: #214b90 #070c12 #211949 #3e308b
#   logo + favicon pixels: two hue clusters, ~218deg (27% of chroma) and ~250deg (17%)
BRAND_SOURCE = {
    "gradient": ["#214b90", "#070c12", "#211949", "#3e308b"],
    "logo_blue": ["#083898", "#083888", "#083878", "#082868", "#082858"],
    "logo_violet": ["#483898", "#382888", "#382878", "#282868", "#181838"],
}

# Both candidates now come from the brand. The question is which hue leads.
ACCENTS = {
    "blue": {
        "label": "A — Azul de marca (~218deg)",
        "note": "El hue dominante del logo: 27% de lo cromatico. Choca con state-active (InProgress), "
                "que es el estado mas frecuente de la aplicacion.",
        "300": "#8fb4e8", "400": "#5b8fd6", "500": "#3a6cbd",
        "600": "#214b90", "700": "#1a3c73", "800": "#152f5a",
    },
    "violet": {
        "label": "B — Violeta de marca (~250deg)",
        "note": "El segundo hue del logo: 17% de lo cromatico, y el mas distintivo de los dos. "
                "Choca con state-paused (Pause), que es un estado raro.",
        "300": "#b3a6ee", "400": "#8e7ce0", "500": "#6f59cf",
        "600": "#5442ab", "700": "#3e308b", "800": "#322672",
    },
}

# 9 table states + 5 registration states collapse into 9 unique semantic tokens.
STATES = [
    ("draft",    "Unassigned / Deleted",  "gris"),
    ("pending",  "Preparation / PauseRequested / Candidate", "ambar"),
    ("warning",  "ChangesRequested",      "naranja"),
    ("open",     "Opened / Player",       "verde"),
    ("active",   "InProgress",            "azul"),
    ("paused",   "Pause",                 "violeta"),
    ("done",     "Finished",              "gris azulado"),
    ("canceled", "Canceled / Rejected",   "rojo apagado"),
    ("blocked",  "Blocked",               "rosa apagado"),
]

# Dark surfaces are tinted toward the brand void (#070c12), not neutral slate:
# the community's own background already reads blue-black.
DARK = {
    "canvas": "#070c12", "surface": "#0f1626", "raised": "#1b2438",
    "border": "#1b2438", "border-strong": "#2b3854",
    "fg": "#eef2f9", "fg-muted": "#9aa9c4", "fg-subtle": "#6c7c99",
    "state": {
        "draft":    {"dot": "#94a3b8", "bg": "#1e293b", "fg": "#cbd5e1"},
        "pending":  {"dot": "#fbbf24", "bg": "#422006", "fg": "#fcd34d"},
        "warning":  {"dot": "#fb923c", "bg": "#431407", "fg": "#fdba74"},
        "open":     {"dot": "#4ade80", "bg": "#052e16", "fg": "#86efac"},
        "active":   {"dot": "#60a5fa", "bg": "#172554", "fg": "#93c5fd"},
        "paused":   {"dot": "#a78bfa", "bg": "#2e1065", "fg": "#c4b5fd"},
        "done":     {"dot": "#7f9cc0", "bg": "#1a2536", "fg": "#b0c2d9"},
        "canceled": {"dot": "#e57373", "bg": "#3f1212", "fg": "#fca5a5"},
        "blocked":  {"dot": "#e879a6", "bg": "#3f1229", "fg": "#f9a8d4"},
    },
}

LIGHT = {
    "canvas": "#f8fafc", "surface": "#ffffff", "raised": "#f1f5f9",
    "border": "#e2e8f0", "border-strong": "#cbd5e1",
    "fg": "#0f172a", "fg-muted": "#475569", "fg-subtle": "#64748b",
    "state": {
        "draft":    {"dot": "#64748b", "bg": "#f1f5f9", "fg": "#475569"},
        "pending":  {"dot": "#d97706", "bg": "#fef3c7", "fg": "#92400e"},
        "warning":  {"dot": "#ea580c", "bg": "#ffedd5", "fg": "#9a3412"},
        "open":     {"dot": "#16a34a", "bg": "#dcfce7", "fg": "#15803d"},
        "active":   {"dot": "#2563eb", "bg": "#dbeafe", "fg": "#1d4ed8"},
        "paused":   {"dot": "#7c3aed", "bg": "#ede9fe", "fg": "#6d28d9"},
        "done":     {"dot": "#52708f", "bg": "#e6edf5", "fg": "#3d5670"},
        "canceled": {"dot": "#dc2626", "bg": "#fee2e2", "fg": "#b91c1c"},
        "blocked":  {"dot": "#db2777", "bg": "#fce7f3", "fg": "#be185d"},
    },
}

SPACING = [("1", "4px"), ("2", "8px"), ("3", "12px"), ("4", "16px"),
           ("5", "20px"), ("6", "24px"), ("8", "32px"), ("10", "40px"),
           ("12", "48px"), ("16", "64px")]
RADII = [("sm", "4px"), ("md", "6px"), ("lg", "8px"), ("xl", "12px"), ("2xl", "16px")]
SIZES = [("xs", "12px", "16px"), ("sm", "14px", "20px"), ("base", "16px", "24px"),
         ("lg", "18px", "28px"), ("xl", "20px", "28px"), ("2xl", "24px", "32px"),
         ("3xl", "30px", "36px"), ("4xl", "36px", "40px")]

# ---------- shared preview chrome ----------
BASE_CSS = """
*,*::before,*::after{box-sizing:border-box}
body{margin:0;font-family:Inter,ui-sans-serif,system-ui,-apple-system,"Segoe UI",sans-serif;
     -webkit-font-smoothing:antialiased}
h1,h2,h3{font-family:Spectral,ui-serif,Georgia,serif;font-weight:600;margin:0}
h1{font-size:30px;line-height:36px}
h2{font-size:20px;line-height:28px}
.wrap{padding:32px;display:flex;flex-direction:column;gap:32px}
.pane{padding:24px;border-radius:12px;border:1px solid}
.grid{display:grid;gap:12px}
.row{display:flex;align-items:center;gap:12px;flex-wrap:wrap}
.lbl{font-size:12px;line-height:16px;letter-spacing:.04em;text-transform:uppercase}
.mono{font-family:"JetBrains Mono",ui-monospace,SFMono-Regular,Menlo,monospace;
      font-size:12px;line-height:16px}
.badge{display:inline-flex;align-items:center;gap:8px;padding:4px 12px;border-radius:9999px;
       font-size:12px;line-height:16px;font-weight:500;white-space:nowrap}
.dot{width:8px;height:8px;border-radius:9999px;flex:none}
.btn{display:inline-flex;align-items:center;justify-content:center;padding:10px 20px;
     border-radius:8px;font-size:14px;line-height:20px;font-weight:600;border:1px solid transparent;
     cursor:pointer}
.sw{height:56px;border-radius:8px;border:1px solid rgba(127,127,127,.25)}
.pass{color:#4ade80}.warnc{color:#fbbf24}.fail{color:#f87171}
table{border-collapse:collapse;width:100%;font-size:13px}
th,td{text-align:left;padding:8px 10px;border-bottom:1px solid}
th{font-size:11px;letter-spacing:.04em;text-transform:uppercase}
"""

def page(title, group, body, theme=None):
    """theme=None means the body supplies its own panes."""
    return f"""<!-- @dsCard group="{group}" -->
<!DOCTYPE html>
<html lang="es"><head><meta charset="utf-8">
<title>{title}</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Spectral:wght@500;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
<style>{BASE_CSS}</style></head>
<body style="background:{DARK['canvas']};color:{DARK['fg']}">
<div class="wrap">{body}</div>
</body></html>"""

def pane(t, inner, label):
    """A themed pane (dark or light) that mimics the real app surface."""
    return (f'<section class="pane" style="background:{t["canvas"]};color:{t["fg"]};'
            f'border-color:{t["border-strong"]}">'
            f'<div class="lbl" style="color:{t["fg-subtle"]};margin-bottom:16px">{label}</div>'
            f'{inner}</section>')

def badge(t, key, text):
    s = t["state"][key]
    return (f'<span class="badge" style="background:{s["bg"]};color:{s["fg"]}">'
            f'<span class="dot" style="background:{s["dot"]}"></span>{text}</span>')

os.makedirs(OUT, exist_ok=True)
report = []

# ============ 1. accent decision ============
def accent_block(t, akey, theme_name):
    a = ACCENTS[akey]
    solid = a["500"] if theme_name == "dark" else a["600"]
    # Pick the readable foreground by measurement, not by assumption:
    # brass is light enough for near-black, copper is not.
    on_solid = max((NEUTRAL["950"], "#ffffff"), key=lambda c: ratio(c, solid))
    text_tone = a["400"] if theme_name == "dark" else a["700"]
    c_btn = r2(on_solid, solid)
    c_txt = r2(text_tone, t["canvas"])
    report.append((f'accent {akey}/{theme_name} button (text on fill)', c_btn))
    report.append((f'accent {akey}/{theme_name} text on canvas', c_txt))
    def cls(v):
        return "pass" if v >= 4.5 else ("warnc" if v >= 3 else "fail")
    return f"""
<div style="display:flex;flex-direction:column;gap:16px">
  <div class="row">
    <button class="btn" style="background:{solid};color:{on_solid}">Postularme</button>
    <span style="color:{text_tone};font-weight:600;font-size:14px">Ver mi mesa &rarr;</span>
    <span class="mono {cls(c_btn)}">boton {c_btn}:1</span>
    <span class="mono {cls(c_txt)}">texto {c_txt}:1</span>
  </div>
  <div class="row">
    <span class="lbl" style="color:{t['fg-subtle']}">junto a los estados que comparten familia:</span>
    {badge(t,'active','InProgress')}
    {badge(t,'paused','Pause')}
    <button class="btn" style="background:{solid};color:{on_solid};padding:4px 12px;font-size:12px">Accion</button>
  </div>
</div>"""

grad = ", ".join(BRAND_SOURCE["gradient"])
ev_sw = lambda lst: "".join(
    f'<div><div class="sw" style="background:{c};height:44px"></div>'
    f'<div class="mono" style="color:{DARK["fg-subtle"]};margin-top:5px">{c}</div></div>' for c in lst)

acc_body = ["<h1>Acento de marca — decision pendiente</h1>",
            f'<p style="color:{DARK["fg-muted"]};max-width:74ch;margin:0;font-size:14px;line-height:22px">'
            'Los dos candidatos salen de la marca real, medida de los assets de la comunidad — no son una eleccion '
            'estetica mia. El problema es que <strong>los dos hues de la marca ya estan ocupados por estados</strong>: '
            '<code>state-active</code> (InProgress) es azul y <code>state-paused</code> (Pause) es violeta. '
            'Mira cada candidato <strong>junto a esos badges</strong>: ahi se ve el choque, no en el swatch aislado.</p>',
            f"""<section class="pane" style="background:{DARK['surface']};border-color:{DARK['border-strong']}">
  <div class="lbl" style="color:{DARK['fg-subtle']};margin-bottom:14px">De donde salen — evidencia</div>
  <div style="display:flex;flex-direction:column;gap:18px">
    <div>
      <div style="font-size:13px;color:{DARK['fg-muted']};margin-bottom:8px">
        Gradiente animado de <code>links.centraldungeon.org</code></div>
      <div style="height:56px;border-radius:8px;background:linear-gradient(-45deg, {grad})"></div>
      <div class="mono" style="color:{DARK['fg-subtle']};margin-top:6px">{grad}</div>
    </div>
    <div>
      <div style="font-size:13px;color:{DARK['fg-muted']};margin-bottom:8px">
        Pixeles del logo — cluster azul, <strong>27% de lo cromatico</strong>, hue ~218&deg;</div>
      <div class="grid" style="grid-template-columns:repeat(5,1fr)">{ev_sw(BRAND_SOURCE['logo_blue'])}</div>
    </div>
    <div>
      <div style="font-size:13px;color:{DARK['fg-muted']};margin-bottom:8px">
        Cluster violeta, <strong>17% de lo cromatico</strong>, hue ~250&deg;</div>
      <div class="grid" style="grid-template-columns:repeat(5,1fr)">{ev_sw(BRAND_SOURCE['logo_violet'])}</div>
    </div>
  </div>
</section>"""]
for akey, a in ACCENTS.items():
    swatches = "".join(
        f'<div><div class="sw" style="background:{a[s]}"></div>'
        f'<div class="mono" style="color:{DARK["fg-subtle"]};margin-top:6px">{s} {a[s]}</div></div>'
        for s in ["300", "400", "500", "600", "700", "800"])
    acc_body.append(f"""
<div style="display:flex;flex-direction:column;gap:16px">
  <div><h2>{a['label']}</h2>
  <p style="color:{DARK['fg-muted']};margin:6px 0 0;font-size:14px">{a['note']}</p></div>
  <div class="grid" style="grid-template-columns:repeat(6,1fr)">{swatches}</div>
  <div class="grid" style="grid-template-columns:1fr 1fr">
    {pane(DARK, accent_block(DARK, akey, 'dark'), 'Tema oscuro')}
    {pane(LIGHT, accent_block(LIGHT, akey, 'light'), 'Tema claro')}
  </div>
</div>""")
open(f"{OUT}/accent-decision.html", "w").write(
    page("Acento de marca", "Brand", "".join(acc_body)))

# ============ 2. state badges ============
def states_table(t, theme_name):
    rows = []
    for key, meaning, reading in STATES:
        s = t["state"][key]
        c = r2(s["fg"], s["bg"])
        report.append((f'state {key}/{theme_name} text on badge', c))
        cl = "pass" if c >= 4.5 else ("warnc" if c >= 3 else "fail")
        rows.append(
            f'<tr><td>{badge(t,key,key)}</td>'
            f'<td style="color:{t["fg-muted"]}">{meaning}</td>'
            f'<td style="color:{t["fg-subtle"]}" class="mono">{reading}</td>'
            f'<td class="mono {cl}">{c}:1 {badge_aa(c)}</td></tr>')
    return (f'<table style="color:{t["fg"]}"><thead><tr>'
            f'<th style="color:{t["fg-subtle"]};border-color:{t["border-strong"]}">Token</th>'
            f'<th style="color:{t["fg-subtle"]};border-color:{t["border-strong"]}">Estados que cubre</th>'
            f'<th style="color:{t["fg-subtle"]};border-color:{t["border-strong"]}">Lectura</th>'
            f'<th style="color:{t["fg-subtle"]};border-color:{t["border-strong"]}">Contraste</th>'
            f'</tr></thead><tbody>'
            + "".join(rows).replace("<td", f'<td style="border-color:{t["border"]}" ', 1)
            + '</tbody></table>').replace('<td>', f'<td style="border-color:{t["border"]}">') \
                               .replace('<td style="color:', f'<td style="border-color:{t["border"]};color:')

st_body = ["<h1>Estados — 14 badges, 9 tokens</h1>",
           f'<p style="color:{DARK["fg-muted"]};max-width:70ch;margin:0;font-size:14px;line-height:22px">'
           'Nueve estados de mesa y cinco de postulacion comparten nueve tokens semanticos. '
           'El color <strong>nunca</strong> es el unico portador: cada badge lleva punto y etiqueta, '
           'porque <code>Pause</code> y <code>PauseRequested</code> comparten familia.</p>',
           f'<div class="grid" style="grid-template-columns:1fr">'
           f'{pane(DARK, states_table(DARK, "dark"), "Tema oscuro")}'
           f'{pane(LIGHT, states_table(LIGHT, "light"), "Tema claro")}</div>']
open(f"{OUT}/states.html", "w").write(page("Estados", "Colors", "".join(st_body)))

# ============ 3. neutrals + surfaces ============
sw = "".join(f'<div><div class="sw" style="background:{v}"></div>'
             f'<div class="mono" style="color:{DARK["fg-subtle"]};margin-top:6px">{k}<br>{v}</div></div>'
             for k, v in NEUTRAL.items())
c_fg = r2(DARK["fg"], DARK["canvas"]); c_mut = r2(DARK["fg-muted"], DARK["canvas"])
c_lfg = r2(LIGHT["fg"], LIGHT["canvas"]); c_lmut = r2(LIGHT["fg-muted"], LIGHT["canvas"])
report += [("neutral body text / dark", c_fg), ("neutral muted text / dark", c_mut),
           ("neutral body text / light", c_lfg), ("neutral muted text / light", c_lmut)]

def surfaces(t, cfg, cmut):
    return f"""<div style="display:flex;flex-direction:column;gap:12px">
  <div class="row">
    <div style="background:{t['surface']};border:1px solid {t['border-strong']};padding:16px;border-radius:8px;flex:1">
      <div style="font-weight:600">surface</div>
      <div class="mono" style="color:{t['fg-subtle']}">{t['surface']}</div></div>
    <div style="background:{t['raised']};border:1px solid {t['border-strong']};padding:16px;border-radius:8px;flex:1">
      <div style="font-weight:600">raised</div>
      <div class="mono" style="color:{t['fg-subtle']}">{t['raised']}</div></div>
  </div>
  <div style="font-size:14px">Texto principal <span class="mono pass">{cfg}:1</span></div>
  <div style="font-size:14px;color:{t['fg-muted']}">Texto atenuado <span class="mono pass">{cmut}:1</span></div>
</div>"""

col_body = ["<h1>Neutros y superficies</h1>",
            f'<p style="color:{DARK["fg-muted"]};max-width:70ch;margin:0;font-size:14px;line-height:22px">'
            'Base fria a proposito (#131): los catorce colores de estado necesitan respirar. '
            'El fondo se aparta, el acento da el caracter.</p>',
            f'<div class="grid" style="grid-template-columns:repeat(11,1fr)">{sw}</div>',
            f'<div class="grid" style="grid-template-columns:1fr 1fr">'
            f'{pane(DARK, surfaces(DARK, c_fg, c_mut), "Tema oscuro — por defecto")}'
            f'{pane(LIGHT, surfaces(LIGHT, c_lfg, c_lmut), "Tema claro")}</div>']
open(f"{OUT}/colors.html", "w").write(page("Neutros", "Colors", "".join(col_body)))

# ============ 4. typography + spacing ============
ty = "".join(
    f'<div style="display:flex;align-items:baseline;gap:20px;padding:10px 0;'
    f'border-bottom:1px solid {DARK["border"]}">'
    f'<span class="mono" style="color:{DARK["fg-subtle"]};width:120px;flex:none">{n} · {s}/{lh}</span>'
    f'<span style="font-size:{s};line-height:{lh};'
    f'{"font-family:Spectral,serif;font-weight:600" if n in ("2xl","3xl","4xl") else ""}">'
    f'La Cripta de Ondrak</span></div>'
    for n, s, lh in SIZES)
sp = "".join(f'<div class="row"><span class="mono" style="width:60px;color:{DARK["fg-subtle"]}">{n}</span>'
             f'<div style="height:16px;width:{v};background:{ACCENTS["violet"]["500"]};border-radius:2px"></div>'
             f'<span class="mono" style="color:{DARK["fg-subtle"]}">{v}</span></div>'
             for n, v in SPACING)
rd = "".join(f'<div><div style="height:56px;background:{DARK["raised"]};border:1px solid {DARK["border-strong"]};'
             f'border-radius:{v}"></div>'
             f'<div class="mono" style="color:{DARK["fg-subtle"]};margin-top:6px">{n} {v}</div></div>'
             for n, v in RADII)
ty_body = ["<h1>Tipografia, espaciado y radios</h1>",
           f'<p style="color:{DARK["fg-muted"]};max-width:70ch;margin:0;font-size:14px;line-height:22px">'
           '<strong>Spectral</strong> (serif) solo en titulos, <strong>Inter</strong> en el cuerpo, '
           '<strong>JetBrains Mono</strong> para ids y valores. Densidad media: base de 4px.</p>',
           f'<h2>Escala tipografica</h2>{ty}',
           f'<div class="grid" style="grid-template-columns:1fr 1fr">'
           f'<div><h2>Espaciado</h2><div style="display:flex;flex-direction:column;gap:8px;margin-top:12px">{sp}</div></div>'
           f'<div><h2>Radios</h2><div class="grid" style="grid-template-columns:repeat(5,1fr);margin-top:12px">{rd}</div></div>'
           f'</div>']
open(f"{OUT}/typography.html", "w").write(page("Tipografia", "Type", "".join(ty_body)))

# ============ 5. components ============
def karma(t, value, filled, n):
    a = ACCENTS["violet"]
    dots = "".join(
        f'<span class="dot" style="width:9px;height:9px;background:'
        f'{a["400"] if i < filled else t["border-strong"]}"></span>' for i in range(5))
    return (f'<div class="row" style="gap:10px"><span class="lbl" style="color:{t["fg-subtle"]}">Karma</span>'
            f'<span style="font-size:20px;font-weight:600;font-family:Spectral,serif">{value}</span>'
            f'<span class="row" style="gap:3px">{dots}</span>'
            f'<span style="font-size:12px;color:{t["fg-subtle"]}">(basado en {n} comentarios)</span></div>')

def table_card(t):
    a = ACCENTS["violet"]
    return f"""<div style="background:{t['surface']};border:1px solid {t['border-strong']};
     border-radius:12px;padding:20px;width:300px;display:flex;flex-direction:column;gap:10px">
  <div style="display:flex;justify-content:space-between;align-items:start;gap:12px">
    <div style="font-family:Spectral,serif;font-size:18px;font-weight:600;line-height:24px">La Cripta de Ondrak</div>
  </div>
  {badge(t,'open','Opened')}
  <div style="font-size:13px;color:{t['fg-muted']};line-height:20px">
    D&amp;D 5e · Roll20 · Corta<br>Martes 20:00 · 3h</div>
  <div style="display:flex;justify-content:space-between;align-items:center;
       border-top:1px solid {t['border']};padding-top:10px;margin-top:2px">
    <span style="font-size:13px;color:{t['fg-muted']}">3 / 5 jugadores</span>
    <span style="font-size:13px;color:{t['fg-muted']}">Ana · <span style="color:{a['400'] if t is DARK else a['700']}">8 240</span></span>
  </div>
</div>"""

def comps(t):
    a = ACCENTS["violet"]
    solid = a["500"] if t is DARK else a["600"]
    on_solid = max((NEUTRAL["950"], "#ffffff"), key=lambda c: ratio(c, solid))
    return f"""<div style="display:flex;flex-direction:column;gap:20px">
  <div class="row">
    <button class="btn" style="background:{solid};color:{on_solid}">Primario</button>
    <button class="btn" style="background:transparent;color:{t['fg']};border-color:{t['border-strong']}">Secundario</button>
    <button class="btn" style="background:transparent;color:{t['fg-muted']}">Fantasma</button>
    <button class="btn" style="background:{t['state']['canceled']['bg']};color:{t['state']['canceled']['fg']}">Destructivo</button>
  </div>
  <div class="row">{"".join(badge(t,k,k) for k,_,_ in STATES)}</div>
  {karma(t, "8 240", 4, 12)}
  {table_card(t)}
</div>"""

cp_body = ["<h1>Componentes base</h1>",
           f'<p style="color:{DARK["fg-muted"]};max-width:70ch;margin:0;font-size:14px;line-height:22px">'
           'El acento solo aparece como <strong>relleno solido</strong> (botones, foco, karma). '
           'Los estados solo como <strong>relleno suave con punto y etiqueta</strong>. '
           'Esa separacion de roles es lo que evita que el oro de marca se confunda con el ambar de pending.</p>',
           f'<div class="grid" style="grid-template-columns:1fr 1fr">'
           f'{pane(DARK, comps(DARK), "Tema oscuro — por defecto")}'
           f'{pane(LIGHT, comps(LIGHT), "Tema claro")}</div>']
open(f"{OUT}/components.html", "w").write(page("Componentes", "Components", "".join(cp_body)))

# ============ 6. theme.css ============
lines = ["/* CentralDungeon design tokens — Tailwind 4 @theme block.",
         "   Source of truth: the design system (decision #130).",
         "   Transcribed into frontend/src/styles/globals.css. */", "", "@theme {",
         "  /* -- typography -- */",
         '  --font-sans: "Inter", ui-sans-serif, system-ui, sans-serif;',
         '  --font-serif: "Spectral", ui-serif, Georgia, serif;',
         '  --font-mono: "JetBrains Mono", ui-monospace, SFMono-Regular, monospace;', ""]
for n, s, lh in SIZES:
    lines.append(f"  --text-{n}: {s};")
    lines.append(f"  --text-{n}--line-height: {lh};")
lines += ["", "  /* -- radii -- */"] + [f"  --radius-{n}: {v};" for n, v in RADII]
lines += ["", "  /* -- neutrals -- */"] + [f"  --color-neutral-{k}: {v};" for k, v in NEUTRAL.items()]
lines += ["", "  /* -- brand accent (candidate B: violet, from the logo) -- */"] + \
         [f"  --color-brand-{k}: {v};" for k, v in ACCENTS["violet"].items() if k.isdigit()]
lines += ["", "  /* -- semantic surfaces, dark (default theme) -- */"]
for k in ["canvas", "surface", "raised", "border", "border-strong", "fg", "fg-muted", "fg-subtle"]:
    lines.append(f"  --color-{k}: {DARK[k]};")
lines += ["", "  /* -- state tokens: 9 table states + 5 registration states -- */"]
for key, meaning, _ in STATES:
    s = DARK["state"][key]
    lines.append(f"  /* {meaning} */")
    for part in ["dot", "bg", "fg"]:
        lines.append(f"  --color-state-{key}-{part}: {s[part]};")

# shadcn/ui's generated components (button, card, dialog...) style themselves with a fixed set
# of token names (--background, --primary, --card...) that this design system never had a reason
# to use on its own. This bridge aliases them to the tokens already decided above - no new
# colour, just the name shadcn's Tailwind classes expect. Aliases written as var() auto-track
# the light override below; only the two solid-fill foregrounds are computed per theme, because
# "which text reads best on this fill" can flip between the dark and light accent shade.
def on_solid(hex_color):
    return max((NEUTRAL["950"], "#ffffff"), key=lambda c: ratio(c, hex_color))

dark_primary = ACCENTS["violet"]["500"]
light_primary = ACCENTS["violet"]["600"]
dark_destructive = DARK["state"]["canceled"]["dot"]
light_destructive = LIGHT["state"]["canceled"]["dot"]

lines += ["", "  /* -- shadcn/ui bridge: aliases of the tokens above, not new colours -- */",
          "  --color-background: var(--color-canvas);",
          "  --color-foreground: var(--color-fg);",
          "  --color-card: var(--color-surface);",
          "  --color-card-foreground: var(--color-fg);",
          "  --color-popover: var(--color-raised);",
          "  --color-popover-foreground: var(--color-fg);",
          "  --color-primary: var(--color-brand-500);",
          f"  --color-primary-foreground: {on_solid(dark_primary)};",
          "  --color-secondary: var(--color-raised);",
          "  --color-secondary-foreground: var(--color-fg);",
          "  --color-muted: var(--color-raised);",
          "  --color-muted-foreground: var(--color-fg-muted);",
          "  --color-accent: var(--color-raised);",
          "  --color-accent-foreground: var(--color-fg);",
          "  --color-destructive: var(--color-state-canceled-dot);",
          f"  --color-destructive-foreground: {on_solid(dark_destructive)};",
          "  --color-input: var(--color-border);",
          "  --color-ring: var(--color-brand-500);"]

lines += ["}", "", "/* Light theme overrides the semantic layer only; primitives never change. */",
          ':root[data-theme="light"] {']
for k in ["canvas", "surface", "raised", "border", "border-strong", "fg", "fg-muted", "fg-subtle"]:
    lines.append(f"  --color-{k}: {LIGHT[k]};")
lines.append("  --color-brand-500: " + ACCENTS["violet"]["600"] + ";  /* darker on light for AA */")
for key, _, _ in STATES:
    s = LIGHT["state"][key]
    for part in ["dot", "bg", "fg"]:
        lines.append(f"  --color-state-{key}-{part}: {s[part]};")
lines.append(f"  --color-primary-foreground: {on_solid(light_primary)};")
lines.append(f"  --color-destructive-foreground: {on_solid(light_destructive)};")
lines.append("}")
open(f"{OUT}/theme.css", "w").write("\n".join(lines) + "\n")

# ---------- contrast report; non-zero exit if anything drops below AA ----------
fails = [(n, v) for n, v in report if v < 4.5]
print(f"\nMeasured contrast pairs: {len(report)}   |   below AA (4.5:1): {len(fails)}\n")
for n, v in sorted(report, key=lambda x: x[1]):
    flag = "OK " if v >= 4.5 else ("!! " if v >= 3 else "XX ")
    print(f"  {flag}{v:5.2f}:1  {n}")


# ============ 7. screens (frontend-diseno.md section 4) ============
ACC = ACCENTS["violet"]
def acc_solid(t): return ACC["500"] if t is DARK else ACC["600"]
def acc_text(t):  return ACC["400"] if t is DARK else ACC["700"]
def on_acc(t):    return max((NEUTRAL["950"], "#ffffff"), key=lambda c: ratio(c, acc_solid(t)))

def chip(t, label, caret=True):
    return (f'<span style="display:inline-flex;align-items:center;gap:6px;padding:6px 12px;'
            f'border:1px solid {t["border-strong"]};border-radius:8px;font-size:13px;'
            f'color:{t["fg-muted"]}">{label}{" &#9662;" if caret else ""}</span>')

def avatar(t, initials, size=32, tone=None):
    return (f'<span style="width:{size}px;height:{size}px;border-radius:9999px;flex:none;'
            f'background:{tone or t["raised"]};color:{t["fg"]};display:inline-flex;'
            f'align-items:center;justify-content:center;font-size:{max(10,size//3)}px;'
            f'font-weight:600">{initials}</span>')

def shell(t, ctx, inner):
    return f"""<div style="background:{t['canvas']};color:{t['fg']};border-radius:12px;overflow:hidden;
     border:1px solid {t['border-strong']}">
  <header style="display:flex;align-items:center;gap:16px;padding:12px 20px;
       background:{t['surface']};border-bottom:1px solid {t['border']}">
    <span style="font-family:Spectral,serif;font-weight:700;font-size:16px;letter-spacing:.01em">
      Central<span style="color:{acc_text(t)}">Dungeon</span></span>
    {chip(t, ctx)}
    <span style="flex:1"></span>
    <span style="position:relative;font-size:15px;color:{t['fg-muted']}">&#9733;
      <span style="position:absolute;top:-6px;right:-9px;background:{acc_solid(t)};color:{on_acc(t)};
        font-size:10px;font-weight:700;border-radius:9999px;padding:1px 5px">3</span></span>
    {avatar(t, "AV", 30)}
  </header>
  <div style="padding:20px">{inner}</div>
</div>"""

def screen(name, title, group, builder, note):
    body = [f"<h1>{title}</h1>",
            f'<p style="color:{DARK["fg-muted"]};max-width:76ch;margin:0;font-size:14px;line-height:22px">{note}</p>',
            f'<div class="grid" style="grid-template-columns:1fr">'
            f'{pane(DARK, builder(DARK), "Tema oscuro — por defecto")}'
            f'{pane(LIGHT, builder(LIGHT), "Tema claro")}</div>']
    open(f"{OUT}/{name}", "w").write(page(title, group, "".join(body)))

# --- 1. explorar mesas ---
def sc_explore(t):
    cards = "".join(table_card(t) for _ in range(3))
    return shell(t, "Jugador", f"""
  <div class="row" style="gap:8px;margin-bottom:18px">
    <span style="flex:1;min-width:220px;padding:8px 12px;border:1px solid {t['border-strong']};
      border-radius:8px;font-size:13px;color:{t['fg-subtle']}">Buscar mesas&hellip;</span>
    {chip(t,'Sistema')}{chip(t,'Tags')}{chip(t,'Plataforma')}{chip(t,'Tipo')}
  </div>
  <div style="display:flex;gap:16px;flex-wrap:wrap">{cards}</div>""")

# --- 2. detalle de mesa ---
def sc_detail(t):
    return shell(t, "Jugador", f"""
  <div style="display:flex;justify-content:space-between;align-items:start;gap:16px;margin-bottom:6px">
    <div><div style="font-family:Spectral,serif;font-size:24px;font-weight:600">La Cripta de Ondrak</div>
    <div style="font-size:13px;color:{t['fg-muted']};margin-top:4px">
      Master: Ana (karma <span style="color:{acc_text(t)}">8 240</span>) &middot; Co-master: Beto</div></div>
    {badge(t,'open','Opened')}
  </div>
  <div style="border-top:1px solid {t['border']};margin:16px 0;padding-top:16px;
       display:flex;flex-direction:column;gap:14px">
    <div style="font-size:14px;line-height:22px;color:{t['fg-muted']};max-width:64ch">
      Una cripta sellada hace siglos vuelve a abrirse. El grupo entra buscando un artefacto
      y encuentra algo que preferiria no haber despertado.</div>
    <div><div class="lbl" style="color:{t['fg-subtle']};margin-bottom:6px">Requisitos</div>
      <div style="font-size:14px;line-height:24px">&middot; Ficha de personaje nivel 3<br>
      &middot; Contar por que queres entrar</div></div>
    <div style="display:flex;gap:32px;flex-wrap:wrap">
      <div><div class="lbl" style="color:{t['fg-subtle']}">Agenda</div>
        <div style="font-size:14px;margin-top:4px">Martes 20:00 &middot; 3h &middot; 12 sesiones</div>
        <div style="font-size:12px;color:{t['fg-subtle']}">en tu hora local</div></div>
      <div><div class="lbl" style="color:{t['fg-subtle']}">Cupo</div>
        <div style="font-size:14px;margin-top:4px">3 / 5</div></div>
    </div>
  </div>
  <div style="border-top:1px solid {t['border']};padding-top:16px;display:flex;
       justify-content:flex-end;gap:12px;align-items:center">
    <span style="font-size:13px;color:{t['fg-subtle']}">Cierra postulaciones en 4 dias</span>
    <button class="btn" style="background:{acc_solid(t)};color:{on_acc(t)}">Postularme</button>
  </div>""")

# --- 3. gestion de mesa (master) ---
def sc_master(t):
    tabs = ["Candidatos","Jugadores","Agenda","Sesiones","Peticiones","Archivos","Estado"]
    tab_html = "".join(
        f'<span style="padding:8px 12px;font-size:13px;'
        + (f'color:{t["fg"]};border-bottom:2px solid {acc_solid(t)};font-weight:600'
           if i == 0 else f'color:{t["fg-subtle"]};border-bottom:2px solid transparent')
        + f'">{x}</span>' for i, x in enumerate(tabs))
    def cand(n, name, initials, karma, when):
        return f"""<div style="display:flex;align-items:center;gap:12px;padding:10px 0;
             border-bottom:1px solid {t['border']}">
          <span class="mono" style="color:{t['fg-subtle']};width:16px">{n}</span>
          {avatar(t, initials, 28)}
          <span style="font-size:14px;flex:1">{name}</span>
          <span style="font-size:13px;color:{t['fg-muted']}">karma {karma}</span>
          <span style="font-size:12px;color:{t['fg-subtle']};width:80px">{when}</span>
          <span style="font-size:12px;color:{acc_text(t)}">Ver</span>
          <button class="btn" style="padding:4px 10px;font-size:12px;background:{acc_solid(t)};
            color:{on_acc(t)}">Aceptar</button>
          <button class="btn" style="padding:4px 10px;font-size:12px;background:transparent;
            color:{t['fg-muted']};border-color:{t['border-strong']}">Rechazar</button>
        </div>"""
    return shell(t, "Master", f"""
  <div style="display:flex;justify-content:space-between;align-items:center;gap:16px">
    <div class="row"><span style="font-family:Spectral,serif;font-size:20px;font-weight:600">
      La Cripta de Ondrak</span>{badge(t,'open','Opened')}</div>
    {chip(t,'Acciones')}
  </div>
  <div style="display:flex;gap:2px;border-bottom:1px solid {t['border']};margin:14px 0 12px;
       flex-wrap:wrap">{tab_html}</div>
  <div class="lbl" style="color:{t['fg-subtle']};margin-bottom:4px">Cola de candidatos — en orden de llegada</div>
  {cand(1,"Carla","CM","8 400","hace 2 dias")}
  {cand(2,"Diego","DR","6 100","hace 1 dia")}
  {cand(3,"Eva","EL","8 000","hace 4 horas")}
  <div style="font-size:12px;color:{t['fg-subtle']};margin-top:10px">
    El orden es por fecha de postulacion y no se reordena. Aceptar al que completa el cupo
    rechaza al resto automaticamente — se avisa antes de confirmar.</div>""")

# --- 4. bandeja de admins ---
def sc_queue(t):
    def item(kind, what, when, mine=False):
        return f"""<div style="display:flex;align-items:center;gap:12px;padding:12px 0;
             border-bottom:1px solid {t['border']}">
          <span class="dot" style="background:{acc_solid(t) if not mine else t['state']['open']['dot']}"></span>
          <span style="font-size:14px;width:150px">{kind}</span>
          <span style="font-size:13px;color:{t['fg-muted']};flex:1">{what}</span>
          <span style="font-size:12px;color:{t['fg-subtle']};width:80px">{when}</span>
          {'<span style="font-size:12px;color:'+t['fg-subtle']+'">reservado por vos</span>' if mine else ''}
          <button class="btn" style="padding:5px 12px;font-size:12px;
            background:{acc_solid(t) if mine else 'transparent'};
            color:{on_acc(t) if mine else t['fg']};
            border-color:{'transparent' if mine else t['border-strong']}">
            {'Resolver' if mine else 'Reservar'}</button>
        </div>"""
    return shell(t, "Admin", f"""
  <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
    <div class="row"><span style="font-family:Spectral,serif;font-size:20px;font-weight:600">Bandeja</span>
      <span style="font-size:12px;color:{t['state']['open']['fg']};display:inline-flex;
        align-items:center;gap:6px"><span class="dot" style="background:{t['state']['open']['dot']}"></span>
        en vivo</span></div>
    {chip(t,'Solo lo mio')}
  </div>
  {item("Mesa por revisar", "&laquo;Hijos del Vacio&raquo;", "hace 10 min")}
  {item("Comentario", "moderacion pendiente", "hace 1 h")}
  {item("Pausa solicitada", "&laquo;La Cripta de Ondrak&raquo;", "hace 2 h", mine=True)}
  {item("Feedback", "del sistema", "hace 3 h")}
  <div style="font-size:12px;color:{t['fg-subtle']};margin-top:10px">
    Al reservar, el item desaparece de la bandeja del resto. Una reserva sin resolver
    se libera sola a los 15 minutos.</div>""")

# --- 5. perfil ---
def sc_profile(t):
    def comment(kind, direction, when, text):
        s = t["state"][kind]
        return f"""<div style="padding:12px 0;border-bottom:1px solid {t['border']}">
          <div class="row" style="gap:8px;margin-bottom:6px">
            <span class="dot" style="background:{s['dot']}"></span>
            <span style="font-size:12px;color:{s['fg']}">{'Positivo' if kind=='open' else 'Negativo'}</span>
            <span style="font-size:12px;color:{t['fg-subtle']}">&middot; {direction} &middot; {when}</span></div>
          <div style="font-size:14px;line-height:22px;color:{t['fg-muted']}">&laquo;{text}&raquo;</div>
        </div>"""
    return shell(t, "Jugador", f"""
  <div class="row" style="gap:16px;align-items:start">
    {avatar(t, "AV", 56)}
    <div style="flex:1">
      <div style="font-family:Spectral,serif;font-size:22px;font-weight:600">Ana Valdez</div>
      <div class="row" style="gap:14px;margin-top:8px">
        {karma(t, "8 240", 4, 12)}
        <span style="font-size:13px;color:{t['fg-muted']}">Jugador &middot; Master</span>
      </div>
      <div style="font-size:13px;color:{t['fg-muted']};margin-top:6px">
        Asistencia: 18 de 20 sesiones
        <span style="color:{t['fg-subtle']}">— no esta incluida en el karma</span></div>
    </div>
  </div>
  <div style="border-top:1px solid {t['border']};margin-top:16px;padding-top:12px">
    <div class="lbl" style="color:{t['fg-subtle']};margin-bottom:4px">Comentarios recibidos</div>
    {comment("open","master a jugador","hace 2 meses","Siempre puntual y con la ficha lista.")}
    {comment("canceled","jugador a jugador","hace 5 meses","Interrumpia bastante durante las sesiones.")}
    <div style="font-size:12px;color:{t['fg-subtle']};margin-top:10px">
      Los comentarios no muestran autor. Nunca, para nadie.</div>
  </div>""")

screen("screen-explore.html", "Explorar mesas — /", "Screens", sc_explore,
       "Fusiona las cuatro listas del legacy en una sola con filtros. Los filtros de catalogo "
       "resuelven por grupo de sinonimos. Las mesas donde el usuario tiene un veto no aparecen.")
screen("screen-table-detail.html", "Detalle de mesa — /tables/:id", "Screens", sc_detail,
       "Una sola ruta para los tres detalles del legacy: la vista cambia segun la relacion del "
       "usuario con la mesa, no la URL. El boton de accion siempre dice por que, nunca queda gris sin explicacion.")
screen("screen-master-table.html", "Gestion de mesa — /master/tables/:id", "Screens", sc_master,
       "Las pestanas son rutas hijas, no useState. La cola de candidatos va en orden de llegada y no se reordena.")
screen("screen-admin-queue.html", "Bandeja de admins — /admin/queue", "Screens", sc_queue,
       "No son notificaciones: son items de trabajo que ya viven en sus tablas. La bandeja es una "
       "vista sobre eso, por eso 'si la toma uno baja para todos' sale gratis.")
screen("screen-profile.html", "Perfil — /profile y /users/:id", "Screens", sc_profile,
       "El karma se explica con los comentarios listados, no con un grafico. La asistencia va al "
       "lado pero no esta incluida en el karma: son dos senales distintas.")

# The build summary lives at the very end of this file, after every screen is written.


# ============ 8. player context: the remaining screens ============
REG = {"Candidate": "pending", "Player": "open", "Rejected": "canceled",
       "Blocked": "blocked", "Deleted": "draft"}

def field(t, label, value, kind="text", hint=None):
    """A form control rendered flat — the real one is shadcn's <Input>/<Select>."""
    inner = (f'<span style="color:{t["fg"] if kind != "placeholder" else t["fg-subtle"]}">{value}</span>'
             + ('<span style="float:right;color:' + t['fg-subtle'] + '">&#9662;</span>' if kind == "select" else ''))
    return f"""<label style="display:block">
  <span class="lbl" style="color:{t['fg-subtle']}">{label}</span>
  <div style="margin-top:6px;padding:9px 12px;border:1px solid {t['border-strong']};
       border-radius:8px;font-size:14px;background:{t['canvas']}">{inner}</div>
  {f'<div style="font-size:12px;color:{t["fg-subtle"]};margin-top:5px">{hint}</div>' if hint else ''}
</label>"""

def dialog(t, title, desc, inner, actions, width=440):
    return f"""<div style="background:{t['canvas']};border:1px solid {t['border-strong']};
     border-radius:12px;padding:28px;display:flex;align-items:center;justify-content:center">
  <div style="width:{width}px;max-width:100%;background:{t['surface']};border:1px solid {t['border-strong']};
       border-radius:12px;padding:22px;box-shadow:0 18px 40px rgba(0,0,0,.45)">
    <div style="font-family:Spectral,serif;font-size:19px;font-weight:600">{title}</div>
    <div style="font-size:13px;color:{t['fg-muted']};line-height:20px;margin-top:6px">{desc}</div>
    <div style="display:flex;flex-direction:column;gap:14px;margin-top:18px">{inner}</div>
    <div style="display:flex;justify-content:flex-end;gap:10px;margin-top:20px">{actions}</div>
  </div>
</div>"""

def btn(t, text, kind="primary", small=False):
    pad = "5px 12px" if small else "10px 20px"
    fs = "12px" if small else "14px"
    if kind == "primary":
        st = f"background:{acc_solid(t)};color:{on_acc(t)}"
    elif kind == "ghost":
        st = f"background:transparent;color:{t['fg-muted']}"
    else:
        st = f"background:transparent;color:{t['fg']};border-color:{t['border-strong']}"
    return f'<button class="btn" style="{st};padding:{pad};font-size:{fs}">{text}</button>'

def row(t, cells, last=False):
    bd = "" if last else f"border-bottom:1px solid {t['border']}"
    return (f'<div style="display:flex;align-items:center;gap:12px;padding:11px 0;{bd}">'
            + "".join(cells) + "</div>")

def cell(t, text, w=None, muted=False, size="14px", mono=False):
    return (f'<span style="font-size:{size};{"flex:1" if w is None else f"width:{w}"};'
            f'color:{t["fg-muted"] if muted else t["fg"]};'
            f'{"font-family:JetBrains Mono,monospace" if mono else ""}">{text}</span>')

def section(t, title, inner, action=None):
    return f"""<div style="margin-bottom:18px">
  <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px">
    <span class="lbl" style="color:{t['fg-subtle']}">{title}</span>{action or ''}</div>
  {inner}</div>"""

# --- /onboarding ---
def sc_onboarding(t):
    return dialog(t, "Antes de entrar",
        "Dos datos y listo. Se piden una sola vez: no hay pantalla de configuracion en la que volver a cambiarlos.",
        field(t, "Como queres que te llamen", "Ana Valdez",
              hint="Viene de tu Discord (<strong>anavaldez</strong>). Podes cambiarlo — tu usuario de "
                   "Discord se conserva igual para los admins.")
        + field(t, "Pais", "Argentina", kind="select",
                hint="Sirve para que un master vea si tu horario le cierra."),
        btn(t, "Entrar"), width=460)

# --- /my/applications ---
def sc_applications(t):
    def app(table, state, label, when, extra=None):
        cells = [cell(t, table), badge(t, REG[state], label),
                 cell(t, when, w="90px", muted=True, size="12px"),
                 btn(t, "Ver mesa", "secondary", small=True)]
        base = row(t, cells)
        if extra:
            base += (f'<div style="font-size:13px;color:{t["fg-muted"]};padding:0 0 11px;'
                     f'border-bottom:1px solid {t["border"]};margin-top:-6px">{extra}</div>')
        return base
    return shell(t, "Jugador", section(t, "Mis postulaciones",
        app("La Cripta de Ondrak", "Candidate", "Candidate", "hace 2 dias")
        + app("Hijos del Vacio", "Player", "Aceptada", "hace 3 semanas")
        + app("El Rio Negro", "Rejected", "Rechazada", "hace 1 mes",
              extra="&laquo;La mesa se completo con las postulaciones anteriores.&raquo;")
        + app("Tumbas de Sal", "Candidate", "Candidate", "hace 5 horas")))

# --- /my/tables ---
def sc_my_tables(t):
    def tbl(name, state, label, next_session, players, master):
        return row(t, [
            cell(t, f'<span style="font-family:Spectral,serif;font-weight:600">{name}</span>'),
            badge(t, state, label),
            cell(t, next_session, w="170px", muted=True, size="13px"),
            cell(t, players, w="70px", muted=True, size="13px"),
            cell(t, master, w="90px", muted=True, size="13px"),
            btn(t, "Abrir", "secondary", small=True)])
    return shell(t, "Jugador", section(t, "Mesas donde juego — solo las que siguen vivas",
        tbl("Hijos del Vacio", "active", "InProgress", "Proxima: jueves 19:00", "5 / 5", "Beto")
        + tbl("La Cripta de Ondrak", "open", "Opened", "Empieza el 12 de septiembre", "3 / 5", "Ana")
        + tbl("Tumbas de Sal", "paused", "Pause", "En pausa desde el 4 de agosto", "4 / 6", "Cira"),
        action=f'<span style="font-size:12px;color:{acc_text(t)}">Ver historial &rarr;</span>'))

# --- /my/tables/:id ---
def sc_my_table_detail(t):
    def sess(n, when, attended):
        mark = (f'<span style="color:{t["state"]["open"]["fg"]}">Asististe</span>' if attended == 1
                else (f'<span style="color:{t["state"]["canceled"]["fg"]}">Faltaste</span>' if attended == 0
                      else f'<span style="color:{t["fg-subtle"]}">&mdash;</span>'))
        return row(t, [cell(t, f"Sesion {n}", w="90px"), cell(t, when, muted=True, size="13px"),
                       cell(t, mark, w="90px", size="13px")])
    def task(title, due, done):
        return row(t, [
            cell(t, title),
            cell(t, due, w="130px", muted=True, size="13px"),
            (badge(t, "open", "Entregado") if done else badge(t, "pending", "Pendiente")),
            btn(t, "Ver" if done else "Entregar", "secondary" if done else "primary", small=True)])
    return shell(t, "Jugador", f"""
  <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
    <div class="row"><span style="font-family:Spectral,serif;font-size:20px;font-weight:600">
      Hijos del Vacio</span>{badge(t,'active','InProgress')}</div>
    <span style="font-size:13px;color:{t['fg-muted']}">Master: Beto</span>
  </div>
  <div style="display:flex;gap:32px;flex-wrap:wrap;padding-bottom:16px;
       border-bottom:1px solid {t['border']};margin-bottom:16px">
    <div><div class="lbl" style="color:{t['fg-subtle']}">Agenda</div>
      <div style="font-size:14px;margin-top:4px">Jueves 19:00 &middot; 3h</div>
      <div style="font-size:12px;color:{t['fg-subtle']}">en tu hora local</div></div>
    <div><div class="lbl" style="color:{t['fg-subtle']}">Proxima sesion</div>
      <div style="font-size:14px;margin-top:4px">Jueves 4 de septiembre</div></div>
    <div><div class="lbl" style="color:{t['fg-subtle']}">Tu asistencia</div>
      <div style="font-size:14px;margin-top:4px">7 de 8</div></div>
  </div>
  {section(t, "Peticiones del master", task("Ficha de personaje nivel 5", "vence en 3 dias", False)
           + task("Trasfondo del personaje", "entregado hace 2 semanas", True))}
  {section(t, "Sesiones", sess(8, "jueves 28 de agosto", 1) + sess(7, "jueves 21 de agosto", 1)
           + sess(6, "jueves 14 de agosto", 0) + sess(9, "jueves 4 de septiembre", None))}""")

# --- /my/history ---
def sc_history(t):
    def h(name, state, label, period, attendance, commented):
        return row(t, [
            cell(t, f'<span style="font-family:Spectral,serif;font-weight:600">{name}</span>'),
            badge(t, state, label),
            cell(t, period, w="180px", muted=True, size="13px"),
            cell(t, attendance, w="110px", muted=True, size="13px"),
            (cell(t, f'<span style="color:{t["fg-subtle"]}">Comentario dejado</span>', w="150px", size="12px")
             if commented else cell(t, btn(t, "Dejar comentario", "primary", small=True), w="150px")),
            btn(t, "Abrir", "secondary", small=True)])
    return shell(t, "Jugador", section(t, "Historial — mesas terminadas y canceladas",
        h("El Rio Negro", "done", "Finished", "mar 2026 &ndash; jul 2026", "18 de 20", True)
        + h("La Torre Invertida", "done", "Finished", "sep 2025 &ndash; feb 2026", "11 de 12", False)
        + h("Ecos de Kalasar", "canceled", "Canceled", "may 2025 &ndash; jun 2025", "3 de 4", True))
        + f'<div style="font-size:12px;color:{t["fg-subtle"]}">El comentario se puede dejar una sola vez '
          f'por persona y por mesa, y no se puede editar despues de confirmarlo.</div>')

# --- /my/files ---
def sc_files(t):
    def f(name, kind, size, used, where):
        return row(t, [
            cell(t, name), cell(t, kind, w="110px", muted=True, size="13px"),
            cell(t, size, w="70px", muted=True, size="12px", mono=True),
            cell(t, used, w="110px", muted=True, size="12px"),
            cell(t, where, w="170px", muted=True, size="12px"),
            btn(t, "Reusar", "secondary", small=True)])
    return shell(t, "Jugador", section(t, "Mis archivos",
        f("ficha-thalia-n5.pdf", "Personaje", "820 KB", "hace 3 dias", "Hijos del Vacio")
        + f("trasfondo-thalia.pdf", "Personaje", "140 KB", "hace 2 semanas", "Hijos del Vacio")
        + f("retrato-thalia.png", "Imagen", "1.2 MB", "hace 2 meses", "sin usar")
        + f("ficha-korvin-n3.pdf", "Personaje", "760 KB", "hace 8 meses", "El Rio Negro"),
        action=btn(t, "Subir archivo", "primary", small=True))
        + f'<div style="font-size:12px;color:{t["fg-subtle"]}">Al adjuntar un archivo a una mesa podes '
          f'subir uno nuevo o reusar uno de esta lista: no se duplica. Los que llevan mucho sin usarse '
          f'se avisan antes de liberarse.</div>')

# --- /notifications ---
def sc_notifications(t):
    def n(text, when, unread, kind=None):
        dot = (f'<span class="dot" style="background:{acc_solid(t)}"></span>' if unread
               else '<span class="dot" style="background:transparent"></span>')
        return row(t, [
            f'<span style="width:8px;flex:none">{dot}</span>',
            cell(t, text, size="14px"),
            (badge(t, kind, kind) if kind else ""),
            cell(t, when, w="90px", muted=True, size="12px")])
    return shell(t, "Jugador", section(t, "Notificaciones",
        n("Te aceptaron en <strong>Hijos del Vacio</strong>.", "hace 2 h", True, "open")
        + n("Beto publico una peticion en <strong>Hijos del Vacio</strong>.", "hace 1 d", True)
        + n("<strong>Tumbas de Sal</strong> paso a pausa.", "hace 3 d", False, "paused")
        + n("Tu postulacion a <strong>El Rio Negro</strong> fue rechazada.", "hace 1 mes", False, "canceled"),
        action=f'<span style="font-size:12px;color:{acc_text(t)}">Marcar todo como leido</span>'))

# --- global feedback dialog ---
def sc_feedback(t):
    normal = dialog(t, "Comentar el sistema",
        "Sobre la plataforma, no sobre personas. <strong>Es anonimo</strong>: no se guarda quien lo escribio, "
        "ni siquiera para los admins.",
        f"""<div><span class="lbl" style="color:{t['fg-subtle']}">Tu comentario</span>
        <div style="margin-top:6px;padding:11px 12px;border:1px solid {t['border-strong']};border-radius:8px;
             min-height:88px;font-size:14px;color:{t['fg-subtle']};background:{t['canvas']}">
          Que cambiarias, que te falta, que no se entiende&hellip;</div></div>""",
        btn(t, "Cancelar", "ghost") + btn(t, "Enviar"))
    limited = dialog(t, "Comentar el sistema",
        "Sobre la plataforma, no sobre personas. <strong>Es anonimo</strong>.",
        f"""<div style="padding:12px 14px;border-radius:8px;
             background:{t['state']['pending']['bg']};color:{t['state']['pending']['fg']};
             font-size:13px;line-height:20px">
          Ya enviaste uno en las ultimas 24 horas. Vas a poder mandar otro mas tarde.
        </div>""",
        btn(t, "Cerrar", "secondary"))
    return (f'<div class="grid" style="grid-template-columns:1fr 1fr">'
            f'<div>{normal}</div><div>{limited}</div></div>')

screen("screen-onboarding.html", "Onboarding — /onboarding", "Screens", sc_onboarding,
       "Bloquea a proposito: si se pudiera saltar, name y country volverian a quedar nulos y el problema "
       "seguiria existiendo. Es el unico momento en que se piden — no hay pantalla de configuracion en v1.")
screen("screen-my-applications.html", "Mis postulaciones — /my/applications", "Screens", sc_applications,
       "Los cinco estados de postulacion con sus tokens. El rechazo muestra su justificacion, que es "
       "obligatoria: un rechazo sin motivo no existe en el modelo.")
screen("screen-my-tables.html", "Mesas donde juego — /my/tables", "Screens", sc_my_tables,
       "Solo mesas vivas. Las terminadas y canceladas viven en /my/history, con ruta propia.")
screen("screen-my-table.html", "Mi mesa — /my/tables/:id", "Screens", sc_my_table_detail,
       "La vista del jugador, no la del master: agenda en hora local, su propia asistencia, y las "
       "peticiones que le tocan. Las entregas se acumulan y no bloquean.")
screen("screen-my-history.html", "Historial — /my/history", "Screens", sc_history,
       "Lleva columnas que /my/tables no tiene —asistencia final y si dejaste comentario—, que es lo que "
       "justifica la ruta separada. El comentario se deja una vez y no se edita.")
screen("screen-my-files.html", "Mis archivos — /my/files", "Screens", sc_files,
       "Subir o reusar: el archivo no se duplica al adjuntarlo a otra mesa. La ultima fecha de uso es lo "
       "que alimenta la retencion por desuso.")
screen("screen-notifications.html", "Notificaciones — /notifications", "Screens", sc_notifications,
       "Una fila por persona en la tabla notifications. El punto del acento marca lo no leido; el badge "
       "reusa el token del estado que causo la notificacion.")
screen("screen-feedback-dialog.html", "Feedback del sistema — accion global", "Components", sc_feedback,
       "No es una ruta: vive en el layout. A la derecha, el limite de una cada 24 horas. La interfaz no lo "
       "predice — pide y traduce el 429, porque predecirlo obligaria a exponer el estado de la cuota que "
       "el token opaco justamente esconde.")


# ---------- build summary ----------
# Registered with atexit so it always runs last: new screen sections get appended
# to the bottom of this file, and a plain call here would report a stale count and
# short-circuit the contrast gate before half the build had run.
import atexit

@atexit.register
def _summary():
    print(f"\nWrote {len(os.listdir(OUT))} files to {OUT}")
    if fails:
        print(f"FAILED: {len(fails)} pair(s) below WCAG AA. Fix the palette before publishing.")
        sys.stdout.flush()
        os._exit(1)


# ============ 9. master context ============
def sc_master_dashboard(t):
    def work(table, what, detail, when, urgent=False):
        return row(t, [
            f'<span style="width:8px;flex:none"><span class="dot" style="background:'
            f'{t["state"]["pending"]["dot"] if urgent else t["border-strong"]}"></span></span>',
            cell(t, f'<span style="font-family:Spectral,serif;font-weight:600">{table}</span>', w="180px"),
            cell(t, what, w="190px"),
            cell(t, detail, muted=True, size="13px"),
            cell(t, when, w="90px", muted=True, size="12px"),
            btn(t, "Resolver", "primary" if urgent else "secondary", small=True)])
    return shell(t, "Master", f"""
  <div style="margin-bottom:6px">
    <div style="font-family:Spectral,serif;font-size:20px;font-weight:600">Lo que espera tu respuesta</div>
    <div style="font-size:13px;color:{t['fg-muted']};margin-top:4px">
      Tres mesas activas. Nada de contadores: esto es lo accionable.</div>
  </div>
  <div style="margin-top:14px">
  {work("La Cripta de Ondrak", "3 candidatos sin responder", "el mas viejo lleva 2 dias", "hace 2 d", True)}
  {work("Hijos del Vacio", "2 entregas sin revisar", "ficha de personaje nivel 5", "hace 1 d", True)}
  {work("Hijos del Vacio", "Sesion sin registrar", "jueves 28 de agosto", "hace 6 h")}
  {work("Tumbas de Sal", "Pausa esperando al admin", "solicitada por vos", "hace 2 d")}
  </div>
  <div style="font-size:12px;color:{t['fg-subtle']};margin-top:12px">
    Sin reserva: el trabajo de una mesa tiene un solo dueno. La bandeja compartida es la de admins.</div>""")

def sc_master_tables(t):
    def tbl(name, state, label, meta, action):
        return row(t, [
            cell(t, f'<span style="font-family:Spectral,serif;font-weight:600">{name}</span>', w="200px"),
            badge(t, state, label),
            cell(t, meta, muted=True, size="13px"),
            btn(t, action, "secondary", small=True)])
    return shell(t, "Master", section(t, "Mis mesas",
        tbl("La Cripta de Ondrak", "open", "Opened", "3 / 5 jugadores &middot; 3 candidatos esperando", "Gestionar")
        + tbl("Hijos del Vacio", "active", "InProgress", "sesion 8 de 12 &middot; jueves 19:00", "Gestionar")
        + tbl("Tumbas de Sal", "pending", "PauseRequested", "esperando a un admin desde hace 2 dias", "Ver")
        + tbl("El Jardin de Hierro", "warning", "ChangesRequested", "un admin pidio correcciones", "Corregir")
        + tbl("Ecos de Kalasar", "draft", "Preparation", "sin enviar a revision", "Seguir editando")
        + tbl("El Rio Negro", "done", "Finished", "termino en julio &middot; 20 sesiones", "Ver"),
        action=btn(t, "Crear mesa", "primary", small=True))
        + f'<div style="font-size:12px;color:{t["fg-subtle"]}">El boton de crear solo aparece con el rol '
          f'<strong>Master</strong>. Quien dirige una mesa por asignacion de un admin la ve aca, pero no crea mesas.</div>')

def sc_master_states(t):
    """The same table, four states: what the master can actually do changes with it."""
    def variant(state, label, headline, actions, body):
        return f"""<div style="background:{t['surface']};border:1px solid {t['border-strong']};
             border-radius:10px;padding:16px;margin-bottom:12px">
          <div style="display:flex;justify-content:space-between;align-items:center;gap:12px">
            <div class="row" style="gap:10px">{badge(t, state, label)}
              <span style="font-size:14px;color:{t['fg-muted']}">{headline}</span></div>
            <div class="row" style="gap:8px">{actions}</div>
          </div>
          <div style="font-size:13px;color:{t['fg-subtle']};margin-top:10px;line-height:20px">{body}</div>
        </div>"""
    return shell(t, "Master", f"""
  <div style="margin-bottom:14px">
    <div style="font-family:Spectral,serif;font-size:20px;font-weight:600">La misma mesa, cinco estados</div>
    <div style="font-size:13px;color:{t['fg-muted']};margin-top:4px">
      Lo que el master puede hacer sale del estado, no del rol. Las pestanas y las acciones cambian con el.</div>
  </div>
  {variant("draft", "Preparation", "sin enviar a revision",
     btn(t,"Editar","secondary",True) + btn(t,"Enviar a revision","primary",True),
     "Solo <strong>editar</strong> y <strong>estado</strong>. No hay candidatos ni sesiones porque la mesa "
     "todavia no existe para nadie mas.")}
  {variant("warning", "ChangesRequested", "un admin pidio correcciones",
     btn(t,"Ver que pidieron","secondary",True) + btn(t,"Reenviar","primary",True),
     "Igual que Preparation, mas la justificacion del admin visible arriba. La justificacion es obligatoria: "
     "no existe un rechazo sin motivo.")}
  {variant("open", "Opened", "3 / 5 jugadores, 3 candidatos",
     btn(t,"Candidatos","primary",True) + btn(t,"Acciones","secondary",True),
     "Se habilitan <strong>candidatos</strong> y <strong>jugadores</strong>. Aceptar al que completa el cupo "
     "rechaza al resto automaticamente, y se avisa <em>antes</em> de confirmar.")}
  {variant("active", "InProgress", "sesion 8 de 12",
     btn(t,"Registrar asistencia","primary",True) + btn(t,"Publicar peticion","secondary",True)
     + btn(t,"Pedir pausa","secondary",True),
     "Se habilitan <strong>sesiones</strong>, <strong>peticiones</strong> y <strong>vetos</strong>. "
     "Es el unico estado donde se registra asistencia y se publican requisitos nuevos.")}
  {variant("paused", "Pause", "congelada desde el 4 de agosto",
     btn(t,"Pedir reanudacion","primary",True),
     "Todo lectura. No se registran sesiones ni se aceptan candidatos: la mesa esta detenida hasta que "
     "un admin la reanude.")}""")

def sc_master_candidate(t):
    return shell(t, "Master", f"""
  <div class="row" style="gap:8px;font-size:13px;color:{t['fg-subtle']};margin-bottom:14px">
    <span>La Cripta de Ondrak</span><span>&rsaquo;</span><span>Candidatos</span>
    <span>&rsaquo;</span><span style="color:{t['fg']}">Carla Medina</span></div>
  <div style="display:flex;gap:20px;flex-wrap:wrap;align-items:start">
    <div style="flex:1;min-width:300px">
      <div class="row" style="gap:14px;align-items:start">
        {avatar(t, "CM", 52)}
        <div style="flex:1">
          <div style="font-family:Spectral,serif;font-size:20px;font-weight:600">Carla Medina</div>
          <div style="font-size:12px;color:{t['fg-subtle']}">Discord: <strong>carlamed</strong> &middot; Argentina</div>
          <div style="margin-top:10px">{karma(t, "8 400", 4, 9)}</div>
          <div style="margin-top:8px;display:flex;gap:14px;flex-wrap:wrap;font-size:13px">
            <span class="row" style="gap:6px">
              <span class="dot" style="background:{t['state']['open']['dot']}"></span>
              <span style="color:{t['fg']}">34</span>
              <span style="color:{t['fg-subtle']}">presentes</span></span>
            <span class="row" style="gap:6px">
              <span class="dot" style="background:{t['state']['pending']['dot']}"></span>
              <span style="color:{t['fg']}">1</span>
              <span style="color:{t['fg-subtle']}">justificada</span></span>
            <span class="row" style="gap:6px">
              <span class="dot" style="background:{t['state']['canceled']['dot']}"></span>
              <span style="color:{t['fg']}">1</span>
              <span style="color:{t['fg-subtle']}">ausente</span></span>
          </div>
          <div style="font-size:12px;color:{t['fg-subtle']};margin-top:5px">
            sobre 36 sesiones registradas &middot; 3 mesas terminadas</div>
        </div>
      </div>
      <div style="height:1px;background:{t['border']};margin:24px 0 20px"></div>
      {section(t, "Su respuesta a los requisitos",
        f'<div style="font-size:14px;line-height:22px;color:{t["fg-muted"]}">'
        '&laquo;Juego hace seis anos, sobre todo 5e. Me interesa la cripta porque quiero probar un personaje '
        'de soporte y esta campana pinta para eso. Puedo los martes sin problema.&raquo;</div>')}
      {section(t, "Archivos que adjunto",
        row(t, [cell(t, "ficha-thalia-n3.pdf"), cell(t, "820 KB", w="80px", muted=True, size="12px", mono=True),
                btn(t, "Abrir", "secondary", small=True)], last=True))}
    </div>
    <div style="width:280px;background:{t['surface']};border:1px solid {t['border-strong']};
         border-radius:10px;padding:16px">
      <div class="lbl" style="color:{t['fg-subtle']}">Decision</div>
      <div style="display:flex;flex-direction:column;gap:8px;margin-top:12px">
        {btn(t, "Aceptar en la mesa", "primary")}
        {btn(t, "Rechazar", "secondary")}
      </div>
      <div style="font-size:12px;color:{t['fg-subtle']};margin-top:12px;line-height:19px">
        Rechazar pide justificacion. Si la aceptas y con eso se llena el cupo, el resto de la cola se
        rechaza automaticamente y te lo avisamos antes.</div>
      <div style="border-top:1px solid {t['border']};margin-top:18px;padding-top:16px">
        <div class="lbl" style="color:{t['fg-subtle']}">Sus comentarios recibidos</div>
        <div style="display:flex;flex-direction:column;gap:12px;margin-top:10px">
          <div><div class="row" style="gap:6px;margin-bottom:3px">
            <span class="dot" style="background:{t['state']['open']['dot']}"></span>
            <span style="font-size:11px;color:{t['fg-subtle']}">master a jugador &middot; hace 3 meses</span></div>
            <div style="font-size:13px;color:{t['fg-muted']};line-height:19px">
              &laquo;Puntual y con la ficha lista.&raquo;</div></div>
          <div><div class="row" style="gap:6px;margin-bottom:3px">
            <span class="dot" style="background:{t['state']['open']['dot']}"></span>
            <span style="font-size:11px;color:{t['fg-subtle']}">jugador a jugador &middot; hace 7 meses</span></div>
            <div style="font-size:13px;color:{t['fg-muted']};line-height:19px">
              &laquo;Muy buena para integrar al grupo.&raquo;</div></div>
          <div><div class="row" style="gap:6px;margin-bottom:3px">
            <span class="dot" style="background:{t['state']['canceled']['dot']}"></span>
            <span style="font-size:11px;color:{t['fg-subtle']}">master a jugador &middot; hace 1 ano</span></div>
            <div style="font-size:13px;color:{t['fg-muted']};line-height:19px">
              &laquo;Aviso tarde dos veces que no podia venir.&raquo;</div></div>
        </div>
        <div style="font-size:12px;color:{acc_text(t)};margin-top:10px">Ver los 9 &rarr;</div>
        <div style="font-size:11px;color:{t['fg-subtle']};margin-top:8px;line-height:17px">
          Sin autor, nunca, para nadie. Se muestran los comentarios, no un conteo: leerlos dice
          mas que contarlos.</div>
      </div>
    </div>
  </div>""")

def sc_master_tasks(t):
    def task(title, due, delivered, total):
        return row(t, [
            cell(t, title),
            cell(t, due, w="140px", muted=True, size="13px"),
            cell(t, f"{delivered} de {total} entregaron", w="150px", muted=True, size="13px"),
            (badge(t, "open", "Completa") if delivered == total else badge(t, "pending", "Abierta")),
            btn(t, "Ver entregas", "secondary", small=True)])
    return shell(t, "Master", f"""
  <div class="row" style="gap:10px;margin-bottom:12px">
    <span style="font-family:Spectral,serif;font-size:20px;font-weight:600">Hijos del Vacio</span>
    {badge(t,'active','InProgress')}
    <span style="font-size:13px;color:{t['fg-subtle']}">sesion 8 de 12</span></div>
  {section(t, "Peticiones a los jugadores",
     task("Ficha de personaje nivel 5", "vence en 3 dias", 3, 5)
     + task("Trasfondo del personaje", "vencio hace 2 semanas", 5, 5)
     + task("Objetivo personal para el arco 2", "sin fecha", 1, 5),
     action=btn(t, "Publicar peticion", "primary", small=True))}
  {dialog(t, "Publicar una peticion",
     "Se puede publicar en cualquier momento mientras la mesa este <strong>InProgress</strong>. "
     "Los jugadores reciben notificacion.",
     field(t, "Que pedis", "Objetivo personal para el arco 2")
     + field(t, "Fecha limite", "Sin fecha limite", kind="select",
             hint="Vencer no bloquea: las entregas tardias siguen entrando y se acumulan."),
     btn(t, "Cancelar", "ghost") + btn(t, "Publicar"), width=420)}""")

def sc_master_actions(t):
    pause = dialog(t, "Pedir pausa de la mesa",
        "La pausa <strong>no es inmediata</strong>: la solicitud va a un admin y la mesa queda en "
        "<strong>PauseRequested</strong> mientras tanto.",
        field(t, "Por que", "El grupo se toma tres semanas por examenes.",
              hint="Obligatorio. Queda en el historial de la mesa y lo lee el admin que resuelve."),
        btn(t, "Cancelar", "ghost") + btn(t, "Enviar solicitud"), width=420)
    comment = dialog(t, "Comentar sobre Carla",
        "Se guarda como <strong>borrador</strong> y solo se confirma cuando la mesa cierre. "
        "Al confirmarlo se vuelve <strong>anonimo para siempre</strong> y no se puede editar ni borrar.",
        f"""<div class="row" style="gap:8px">
          {badge(t,'open','Positivo')}{badge(t,'canceled','Negativo')}
        </div>
        <div><span class="lbl" style="color:{t['fg-subtle']}">Tu comentario</span>
        <div style="margin-top:6px;padding:11px 12px;border:1px solid {t['border-strong']};border-radius:8px;
             min-height:72px;font-size:14px;color:{t['fg-subtle']};background:{t['canvas']}">
          Como fue jugar con esta persona&hellip;</div></div>""",
        btn(t, "Cancelar", "ghost") + btn(t, "Guardar borrador"), width=430)
    return (f'<div class="grid" style="grid-template-columns:1fr 1fr">'
            f'<div>{pause}</div><div>{comment}</div></div>')

def sc_master_wizard(t):
    steps = ["Lo basico", "Catalogos", "Agenda", "Requisitos", "Revision"]
    sh = "".join(
        f'<span style="display:inline-flex;align-items:center;gap:7px;font-size:13px;'
        f'color:{t["fg"] if i == 2 else t["fg-subtle"]}">'
        f'<span style="width:20px;height:20px;border-radius:9999px;display:inline-flex;'
        f'align-items:center;justify-content:center;font-size:11px;font-weight:600;'
        + (f'background:{acc_solid(t)};color:{on_acc(t)}' if i == 2
           else (f'background:{t["state"]["open"]["bg"]};color:{t["state"]["open"]["fg"]}' if i < 2
                 else f'border:1px solid {t["border-strong"]};color:{t["fg-subtle"]}'))
        + f'">{"&#10003;" if i < 2 else i+1}</span>{s}</span>'
        + ('<span style="color:' + t['border-strong'] + '">&mdash;</span>' if i < 4 else '')
        for i, s in enumerate(steps))
    return shell(t, "Master", f"""
  <div class="row" style="gap:10px;margin-bottom:18px">{sh}</div>
  <div style="max-width:520px;display:flex;flex-direction:column;gap:16px">
    {field(t, "Dia de la semana", "Martes", kind="select")}
    {field(t, "Hora de inicio", "20:00", hint="Se guarda en UTC y cada jugador la ve en su hora local.")}
    {field(t, "Duracion de la sesion", "3 horas", kind="select")}
    {field(t, "Cantidad de sesiones", "12",
           hint="Al abrir la mesa se generan las 12 sesiones a partir de la fecha de inicio y esta agenda.")}
    {field(t, "Fecha de la primera sesion", "9 de septiembre de 2026", kind="select")}
  </div>
  <div style="display:flex;justify-content:space-between;margin-top:22px;max-width:520px">
    {btn(t, "Atras", "secondary")}{btn(t, "Siguiente")}
  </div>
  <div style="font-size:12px;color:{t['fg-subtle']};margin-top:14px;max-width:520px">
    Al terminar, la mesa nace en <strong>Preparation</strong> y hay que enviarla a revision.
    Solo las mesas que crea un admin saltan la revision y nacen en Unassigned.</div>""")

screen("screen-master-dashboard.html", "Dashboard del master — /master", "Screens", sc_master_dashboard,
       "Bandeja de trabajo, no resumen con numeros (#136). Un master con tres mesas no necesita saber "
       "cuantos candidatos tiene, necesita saber a quien le debe una respuesta. Sin reserva: el trabajo "
       "de una mesa tiene un solo dueno.")
screen("screen-master-tables.html", "Mis mesas — /master/tables", "Screens", sc_master_tables,
       "Todos los estados en una lista. El boton de crear aparece solo con el rol Master: quien dirige "
       "una mesa por asignacion de un admin (#135) la ve aca, pero no crea mesas.")
screen("screen-master-states.html", "La mesa segun su estado", "Screens", sc_master_states,
       "Lo que el master puede hacer sale del estado, no del rol. Cinco variantes de la misma pantalla, "
       "con las acciones que cada estado habilita de verdad.")
screen("screen-master-candidate.html", "Ficha de un candidato", "Screens", sc_master_candidate,
       "El perfil se abre para el master desde que recibe la solicitud, no antes (#41). La asistencia va con los tres numeros y no como razon: mezclar una falta avisada con un planton la vuelve inexplicable, y las sesiones sin registrar quedan fuera del denominador (#137). Los comentarios se muestran, no se cuentan (#99).")
screen("screen-master-tasks.html", "Peticiones durante la mesa", "Screens", sc_master_tasks,
       "Publicar requisitos nuevos mientras la mesa corre. Vencer no bloquea: las entregas tardias "
       "siguen entrando y se acumulan.")
screen("screen-master-actions.html", "Pausa y comentario — dialogos", "Components", sc_master_actions,
       "Las dos acciones del master que no son inmediatas. La pausa pasa por un admin. El comentario "
       "nace borrador y solo se vuelve anonimo e irreversible cuando la mesa cierra.")
screen("screen-master-wizard.html", "Crear mesa — /master/tables/new", "Screens", sc_master_wizard,
       "Cinco pasos; aca el de agenda, que es el que define como se materializan las sesiones al abrir "
       "la mesa. La hora se guarda en UTC y se muestra en la local de cada quien.")


# ============ 10. responsive + the four mandatory states ============
BREAKPOINTS = [("Movil", 375, "base"), ("Tablet", 768, "md"), ("Escritorio", 1200, "lg")]

def frame(t, label, width, inner, note=None):
    """A fixed-width viewport so the three sizes can be compared side by side."""
    return f"""<div style="flex:none">
  <div class="lbl" style="color:{t['fg-subtle']};margin-bottom:6px">{label} &middot; {width}px</div>
  <div style="width:{width}px;background:{t['canvas']};border:1px solid {t['border-strong']};
       border-radius:10px;overflow:hidden">{inner}</div>
  {f'<div style="font-size:11px;color:{t["fg-subtle"]};margin-top:6px;max-width:{width}px">{note}</div>' if note else ''}
</div>"""

def rack(t, frames):
    return (f'<div style="display:flex;gap:20px;align-items:flex-start;overflow-x:auto;'
            f'padding-bottom:8px">{"".join(frames)}</div>')

def mini_header(t, compact):
    if compact:
        return f"""<div style="display:flex;align-items:center;gap:10px;padding:10px 12px;
             background:{t['surface']};border-bottom:1px solid {t['border']}">
          <span style="font-size:16px;color:{t['fg-muted']}">&#9776;</span>
          <span style="font-family:Spectral,serif;font-weight:700;font-size:14px">
            Central<span style="color:{acc_text(t)}">Dungeon</span></span>
          <span style="flex:1"></span>
          <span style="font-size:13px;color:{t['fg-muted']}">&#9733;</span>
          {avatar(t, "AV", 24)}</div>"""
    return f"""<div style="display:flex;align-items:center;gap:12px;padding:10px 16px;
         background:{t['surface']};border-bottom:1px solid {t['border']}">
      <span style="font-family:Spectral,serif;font-weight:700;font-size:15px">
        Central<span style="color:{acc_text(t)}">Dungeon</span></span>
      {chip(t, "Jugador")}<span style="flex:1"></span>
      <span style="font-size:14px;color:{t['fg-muted']}">&#9733;</span>{avatar(t, "AV", 26)}</div>"""

def mini_card(t):
    return f"""<div style="background:{t['surface']};border:1px solid {t['border-strong']};
         border-radius:8px;padding:10px">
      <div style="font-family:Spectral,serif;font-weight:600;font-size:13px">La Cripta de Ondrak</div>
      <div style="margin:6px 0">{badge(t,'open','Opened')}</div>
      <div style="font-size:11px;color:{t['fg-muted']}">D&amp;D &middot; Roll20 &middot; 3/5</div></div>"""

def sc_responsive(t):
    def grid(cols, pad=12):
        return (f'<div style="padding:{pad}px;display:grid;gap:10px;'
                f'grid-template-columns:repeat({cols},1fr)">'
                + "".join(mini_card(t) for _ in range(cols * 2)) + "</div>")
    explorer = rack(t, [
        frame(t, "Movil", 375, mini_header(t, True)
              + f'<div style="padding:12px 12px 0"><div style="padding:8px 10px;border:1px solid '
                f'{t["border-strong"]};border-radius:8px;font-size:12px;color:{t["fg-subtle"]}">'
                f'Buscar&hellip; &nbsp;&nbsp; <span style="float:right">Filtros &#9662;</span></div></div>'
              + grid(1), "Una columna. Los cuatro filtros se van a un <strong>sheet</strong> detras "
                         "de un solo boton: cuatro selects apilados comen la pantalla entera."),
        frame(t, "Tablet", 768, mini_header(t, False) + grid(2),
              "Dos columnas y los filtros vuelven a la barra."),
        frame(t, "Escritorio", 1200, mini_header(t, False) + grid(3),
              "Tres columnas. El limite lo pone la legibilidad de la ficha, no el ancho disponible."),
    ])

    def wide_table(mode):
        cols = ["Nombre", "Grupo", "Mesas", "Estado", "Acciones"]
        if mode == "full":
            head = "".join(f'<th style="color:{t["fg-subtle"]};border-color:{t["border-strong"]};'
                           f'padding:6px 8px;font-size:10px">{c}</th>' for c in cols)
            rows = "".join(
                f'<tr><td style="border-color:{t["border"]};padding:7px 8px;font-size:12px">D&amp;D 5e</td>'
                f'<td style="border-color:{t["border"]};padding:7px 8px;font-size:12px;'
                f'color:{t["fg-muted"]}">D&amp;D</td>'
                f'<td style="border-color:{t["border"]};padding:7px 8px;font-size:12px">14</td>'
                f'<td style="border-color:{t["border"]};padding:7px 8px">{badge(t,"open","Aprobado")}</td>'
                f'<td style="border-color:{t["border"]};padding:7px 8px;font-size:12px;'
                f'color:{acc_text(t)}">Fusionar</td></tr>' for _ in range(3))
            return (f'<div style="padding:12px"><table><thead><tr>{head}</tr></thead>'
                    f'<tbody>{rows}</tbody></table></div>')
        # stacked: the row becomes a definition list, ordered by priority
        item = f"""<div style="background:{t['surface']};border:1px solid {t['border-strong']};
             border-radius:8px;padding:10px;margin-bottom:8px">
          <div style="display:flex;justify-content:space-between;align-items:center;gap:8px">
            <span style="font-size:13px;font-weight:600">D&amp;D 5e</span>{badge(t,'open','Aprobado')}</div>
          <div style="font-size:11px;color:{t['fg-muted']};margin-top:5px">
            Grupo <strong>D&amp;D</strong> &middot; 14 mesas</div>
          <div style="font-size:11px;color:{acc_text(t)};margin-top:6px">Fusionar</div></div>"""
        return f'<div style="padding:12px">{item * 3}</div>'

    tables = rack(t, [
        frame(t, "Movil", 375, mini_header(t, True) + wide_table("stack"),
              "<strong>La tabla ancha deja de ser tabla.</strong> Cada fila se vuelve una ficha: "
              "identidad y estado arriba, el resto como texto, la accion al pie. Nada de scroll horizontal."),
        frame(t, "Escritorio", 1200, mini_header(t, False) + wide_table("full"),
              "Las cinco columnas caben y se leen alineadas, que es de lo que sirve una tabla."),
    ])

    dialogs = rack(t, [
        frame(t, "Movil", 375, mini_header(t, True)
              + f"""<div style="padding:0"><div style="background:{t['surface']};
                border-top:1px solid {t['border-strong']};padding:16px;margin-top:60px;
                border-radius:12px 12px 0 0">
                <div style="width:36px;height:4px;border-radius:9999px;background:{t['border-strong']};
                  margin:0 auto 12px"></div>
                <div style="font-family:Spectral,serif;font-size:15px;font-weight:600">Pedir pausa</div>
                <div style="font-size:12px;color:{t['fg-muted']};margin-top:4px;line-height:18px">
                  La pausa no es inmediata: la resuelve un admin.</div>
                <div style="margin-top:12px;padding:10px;border:1px solid {t['border-strong']};
                  border-radius:8px;min-height:54px;font-size:12px;color:{t['fg-subtle']}">Por que&hellip;</div>
                <div style="margin-top:12px">{btn(t,'Enviar solicitud')}</div></div></div>""",
              "El modal se vuelve <strong>sheet desde abajo</strong>, con el boton a lo ancho: "
              "un dialogo centrado en 375px queda pegado a los bordes."),
        frame(t, "Escritorio", 1200, mini_header(t, False)
              + f'<div style="padding:20px">{dialog(t, "Pedir pausa", "La pausa no es inmediata: la resuelve un admin.", field(t, "Por que", "El grupo se toma tres semanas."), btn(t, "Cancelar", "ghost") + btn(t, "Enviar solicitud"), width=400)}</div>',
              "Modal centrado sobre el fondo atenuado."),
    ])

    bp = "".join(
        f'<div style="display:flex;align-items:center;gap:12px;padding:7px 0;'
        f'border-bottom:1px solid {t["border"]}">'
        f'<span class="mono" style="width:60px;color:{acc_text(t)}">{cls}</span>'
        f'<span style="width:80px;font-size:13px">{name}</span>'
        f'<span class="mono" style="color:{t["fg-subtle"]}">&ge; {w}px</span></div>'
        for name, w, cls in BREAKPOINTS)

    return f"""
  <div style="margin-bottom:8px">
    <div style="font-family:Spectral,serif;font-size:19px;font-weight:600">Puntos de corte</div>
    <div style="font-size:13px;color:{t['fg-muted']};margin-top:4px;max-width:70ch">
      Los de Tailwind por defecto, sin inventar ninguno. Se disena de <strong>menor a mayor</strong>:
      las clases sin prefijo son las del telefono.</div>
    <div style="max-width:340px;margin-top:10px">{bp}</div>
  </div>
  <div style="height:1px;background:{t['border']};margin:22px 0"></div>
  <div style="font-family:Spectral,serif;font-size:17px;font-weight:600;margin-bottom:12px">
    Grilla de fichas &mdash; el explorador</div>{explorer}
  <div style="height:1px;background:{t['border']};margin:22px 0"></div>
  <div style="font-family:Spectral,serif;font-size:17px;font-weight:600;margin-bottom:4px">
    Tabla ancha &mdash; el caso dificil</div>
  <div style="font-size:13px;color:{t['fg-muted']};margin-bottom:12px;max-width:70ch">
    Es lo que hace caro el responsive completo: catalogos, usuarios y auditoria tienen cinco o mas
    columnas. La regla es una sola y vale para las tres.</div>{tables}
  <div style="height:1px;background:{t['border']};margin:22px 0"></div>
  <div style="font-family:Spectral,serif;font-size:17px;font-weight:600;margin-bottom:12px">
    Dialogos</div>{dialogs}"""

def sc_states(t):
    def bar(w, h=12, mt=8):
        return (f'<div style="height:{h}px;width:{w};border-radius:4px;margin-top:{mt}px;'
                f'background:{t["raised"]}"></div>')

    def wrap(title, inner):
        return f"""<div>
          <div class="lbl" style="color:{t['fg-subtle']};margin-bottom:8px">{title}</div>
          <div style="background:{t['surface']};border:1px solid {t['border-strong']};
               border-radius:10px;padding:16px;min-height:190px">{inner}</div></div>"""

    def empty(icon, title, desc, action=None):
        return f"""<div style="text-align:center;padding:22px 10px">
          <div style="font-size:26px;color:{t['border-strong']}">{icon}</div>
          <div style="font-size:14px;font-weight:600;margin-top:10px">{title}</div>
          <div style="font-size:12px;color:{t['fg-muted']};margin-top:5px;line-height:18px">{desc}</div>
          {f'<div style="margin-top:14px">{action}</div>' if action else ''}</div>"""

    def error(msg):
        return f"""<div style="text-align:center;padding:22px 10px">
          <div style="font-size:26px;color:{t['state']['canceled']['dot']}">&#9888;</div>
          <div style="font-size:14px;font-weight:600;margin-top:10px">No se pudo cargar</div>
          <div style="font-size:12px;color:{t['fg-muted']};margin-top:5px;line-height:18px">
            {msg}</div>
          <div style="margin-top:14px">{btn(t, "Reintentar", "secondary", small=True)}</div></div>"""

    def forbidden():
        return f"""<div style="text-align:center;padding:22px 10px">
          <div style="font-size:26px;color:{t['border-strong']}">&#128274;</div>
          <div style="font-size:14px;font-weight:600;margin-top:10px">No tenes acceso a esto</div>
          <div style="font-size:12px;color:{t['fg-muted']};margin-top:5px;line-height:18px">
            Esta pantalla es del contexto <strong>Admin</strong>. Si creias que si,
            avisale a alguien del equipo.</div>
          <div style="margin-top:14px">{btn(t, "Volver al inicio", "secondary", small=True)}</div></div>"""

    arch = {
        "Listado": {
            "cargando": "".join(
                f'<div style="display:flex;align-items:center;gap:10px;padding:9px 0;'
                f'border-bottom:1px solid {t["border"]}">{bar("40%",13,0)}'
                f'<span style="flex:1"></span>{bar("70px",16,0)}{bar("54px",11,0)}</div>'
                for _ in range(4)),
            "vacio": empty("&#9723;", "Todavia no jugas en ninguna mesa",
                           "Cuando un master te acepte, la mesa aparece aca.",
                           btn(t, "Explorar mesas", "primary", small=True)),
            "error": error("El servidor no respondio a tiempo."),
            "403": forbidden()},
        "Detalle": {
            "cargando": bar("55%", 20, 0) + bar("80px", 18) + bar("100%") + bar("92%") + bar("60%")
                        + f'<div style="height:1px;background:{t["border"]};margin:14px 0"></div>'
                        + bar("40%") + bar("70%"),
            "vacio": empty("&#9723;", "Esta mesa no existe",
                           "O fue eliminada, o nunca existio con ese enlace."),
            "error": error("No pudimos traer los datos de la mesa."),
            "403": forbidden()},
        "Formulario": {
            "cargando": bar("30%", 11, 0) + bar("100%", 34, 6) + bar("30%", 11, 14)
                        + bar("100%", 34, 6) + bar("30%", 11, 14) + bar("100%", 34, 6),
            "vacio": empty("&#9998;", "No hay nada que completar",
                           "Este paso no aplica para esta mesa."),
            "error": error("No pudimos guardar. Tus cambios siguen en el formulario."),
            "403": forbidden()},
        "Dashboard": {
            "cargando": "".join(
                f'<div style="display:flex;align-items:center;gap:10px;padding:9px 0;'
                f'border-bottom:1px solid {t["border"]}">{bar("22%",13,0)}{bar("30%",13,0)}'
                f'<span style="flex:1"></span>{bar("62px",22,0)}</div>' for _ in range(3)),
            "vacio": empty("&#10003;", "Nada espera tu respuesta",
                           "Todas tus mesas estan al dia. <strong>Esto es una buena noticia</strong>, "
                           "no una pantalla rota."),
            "error": error("No pudimos armar tu bandeja."),
            "403": forbidden()},
    }
    order = [("cargando", "Cargando &mdash; skeleton"), ("vacio", "Vacio"),
             ("error", "Error"), ("403", "Sin permiso &mdash; 403")]
    out = []
    for name, states in arch.items():
        cells = "".join(wrap(lbl, states[k]) for k, lbl in order)
        out.append(f"""<div style="margin-bottom:26px">
          <div style="font-family:Spectral,serif;font-size:17px;font-weight:600;margin-bottom:10px">
            {name}</div>
          <div class="grid" style="grid-template-columns:repeat(4,1fr)">{cells}</div></div>""")
    return "".join(out)

COPY = [
    ("/", "No hay mesas que coincidan", "Proba con menos filtros: la busqueda resuelve sinonimos, "
     "asi que DANDD tambien trae D&amp;D.", "Limpiar filtros"),
    ("/my/applications", "Todavia no te postulaste", "Cuando te postules a una mesa, vas a poder "
     "seguir el estado aca.", "Explorar mesas"),
    ("/my/tables", "Todavia no jugas en ninguna mesa", "Cuando un master te acepte, la mesa "
     "aparece aca.", "Explorar mesas"),
    ("/my/history", "Todavia no terminaste ninguna mesa", "Aca van a quedar las mesas que "
     "terminaron o se cancelaron.", None),
    ("/my/files", "No subiste ningun archivo", "Los archivos que subas a una mesa quedan aca "
     "para reusarlos en otras.", "Subir archivo"),
    ("/notifications", "No tenes notificaciones", "Te avisamos cuando algo pase en tus mesas.", None),
    ("/profile", "Todavia no recibiste comentarios", "Aparecen cuando termina una mesa en la que "
     "jugaste y alguien deja el suyo.", None),
    ("/master", "Nada espera tu respuesta", "Todas tus mesas estan al dia.", None),
    ("/master/tables", "Todavia no creaste ninguna mesa", "Una mesa nace en Preparation y se "
     "envia a revision cuando esta lista.", "Crear mesa"),
    ("/master/tables/:id &middot; candidatos", "Nadie se postulo todavia",
     "La mesa esta abierta: las postulaciones van a aparecer en orden de llegada.", None),
    ("/master/tables/:id &middot; peticiones", "No publicaste ninguna peticion",
     "Podes pedir fichas, trasfondos o lo que necesites mientras la mesa corre.", "Publicar peticion"),
    ("/admin/queue", "La bandeja esta vacia", "No hay nada esperando revision.", None),
]

def sc_states_copy(t):
    rows = "".join(
        f'<tr><td style="border-color:{t["border"]};padding:9px 10px;font-size:12px;'
        f'font-family:JetBrains Mono,monospace;color:{acc_text(t)};white-space:nowrap">{r}</td>'
        f'<td style="border-color:{t["border"]};padding:9px 10px;font-size:13px;font-weight:600">{ti}</td>'
        f'<td style="border-color:{t["border"]};padding:9px 10px;font-size:12px;'
        f'color:{t["fg-muted"]};line-height:18px">{d}</td>'
        f'<td style="border-color:{t["border"]};padding:9px 10px">'
        + (btn(t, a, "secondary", small=True) if a else
           f'<span style="font-size:11px;color:{t["fg-subtle"]}">sin accion</span>')
        + "</td></tr>" for r, ti, d, a in COPY)
    head = "".join(f'<th style="color:{t["fg-subtle"]};border-color:{t["border-strong"]};'
                   f'padding:8px 10px">{c}</th>' for c in ["Pantalla", "Titulo", "Explicacion", "Accion"])
    return (f'<table style="color:{t["fg"]}"><thead><tr>{head}</tr></thead><tbody>{rows}</tbody></table>'
            f'<div style="font-size:12px;color:{t["fg-subtle"]};margin-top:12px;line-height:19px">'
            f'El skeleton, el error y el 403 son identicos por arquetipo. <strong>Lo unico que cambia '
            f'entre pantallas es esta tabla</strong>: que dice el vacio y que ofrece hacer. '
            f'Dos de ellos &mdash;<code>/master</code> y <code>/admin/queue</code>&mdash; son '
            f'<strong>buenas noticias</strong> y no deben leerse como una pantalla rota.</div>')

screen("responsive.html", "Responsive — puntos de corte y arquetipos", "Foundations", sc_responsive,
       "Responsive completo en las 27 rutas (#138). Los puntos de corte son los de Tailwind y se disena "
       "de menor a mayor. Lo caro son las tablas anchas de Admin y Owner, y por eso tienen regla propia.")
screen("ui-states.html", "Los cuatro estados obligatorios", "Foundations", sc_states,
       "Cuatro arquetipos por cuatro estados (#139). Toda pantalla que lea datos hereda los de su "
       "arquetipo; lo unico que define por su cuenta es el texto del vacio.")
screen("ui-states-copy.html", "Estado vacio — texto por pantalla", "Foundations", sc_states_copy,
       "El vacio es el unico de los cuatro que cambia entre pantallas, porque cambia que ofrece hacer. "
       "Dos son buenas noticias y no deben verse como error.")


# ============ 11. the component inventory that was still undrawn ============
def menu(t, items, width=210, header=None):
    body = ""
    if header:
        body += (f'<div style="padding:8px 12px;border-bottom:1px solid {t["border"]};'
                 f'font-size:11px;color:{t["fg-subtle"]};letter-spacing:.04em;'
                 f'text-transform:uppercase">{header}</div>')
    for it in items:
        if it is None:
            body += f'<div style="height:1px;background:{t["border"]};margin:4px 0"></div>'
            continue
        icon, label, extra, active = it
        body += (f'<div style="display:flex;align-items:center;gap:10px;padding:8px 12px;font-size:13px;'
                 f'background:{t["raised"] if active else "transparent"};'
                 f'color:{t["fg"] if active else t["fg-muted"]}">'
                 f'<span style="width:15px;text-align:center;color:{acc_text(t) if active else t["fg-subtle"]}">'
                 f'{icon}</span><span style="flex:1">{label}</span>'
                 + (f'<span style="font-size:11px;color:{t["fg-subtle"]}">{extra}</span>' if extra else '')
                 + "</div>")
    return (f'<div style="width:{width}px;background:{t["surface"]};border:1px solid {t["border-strong"]};'
            f'border-radius:10px;padding:5px 0;box-shadow:0 14px 32px rgba(0,0,0,.4)">{body}</div>')

def demo(t, title, note, inner):
    return f"""<div style="margin-bottom:26px">
  <div style="font-family:Spectral,serif;font-size:16px;font-weight:600">{title}</div>
  <div style="font-size:12px;color:{t['fg-muted']};margin:4px 0 12px;max-width:76ch;line-height:19px">{note}</div>
  <div style="background:{t['canvas']};border:1px solid {t['border']};border-radius:10px;padding:18px">{inner}</div>
</div>"""

def confirm(t, title, consequence, confirm_label, danger=True, extra=""):
    tone = t["state"]["canceled"] if danger else t["state"]["pending"]
    return f"""<div style="width:410px;background:{t['surface']};border:1px solid {t['border-strong']};
     border-radius:12px;padding:20px;box-shadow:0 18px 40px rgba(0,0,0,.45)">
  <div style="font-family:Spectral,serif;font-size:17px;font-weight:600">{title}</div>
  <div style="margin-top:10px;padding:11px 13px;border-radius:8px;background:{tone['bg']};
       color:{tone['fg']};font-size:13px;line-height:19px">{consequence}</div>
  {extra}
  <div style="display:flex;justify-content:flex-end;gap:9px;margin-top:16px">
    {btn(t, "Cancelar", "ghost")}
    <button class="btn" style="background:{tone['dot']};color:{NEUTRAL['950']};padding:10px 18px;
      font-size:14px">{confirm_label}</button>
  </div></div>"""

def sc_comp_dialogs(t):
    veto = confirm(t, "Vetar a Diego de esta mesa",
        "El veto es <strong>solo para esta mesa</strong> y no se puede deshacer. Diego deja de ver "
        "la mesa por completo: no le aparece en el explorador y el enlace directo le responde "
        "<strong>que no existe</strong>, no que no tiene permiso.", "Vetar")
    cancel = confirm(t, "Cancelar La Cripta de Ondrak",
        "La mesa pasa a <strong>Canceled</strong> y no vuelve. Los 3 jugadores reciben notificacion "
        "y las 12 sesiones quedan sin jugar.", "Cancelar la mesa",
        extra=f'<div style="margin-top:12px">{field(t, "Justificacion", "El grupo no logro sostener el horario.", hint="Obligatoria. Queda en el historial de la mesa.")}</div>')
    comment = confirm(t, "Confirmar tu comentario sobre Carla",
        "Al confirmarlo se vuelve <strong>anonimo para siempre</strong>: nadie —ni vos, ni un admin, "
        "ni el owner— va a poder saber que lo escribiste. <strong>No se puede editar ni borrar</strong>, "
        "y es el unico que vas a poder dejar sobre Carla en esta mesa.", "Confirmar", danger=False)
    dirty = f"""<div style="width:400px;background:{t['surface']};border:1px solid {t['border-strong']};
     border-radius:12px;padding:20px;box-shadow:0 18px 40px rgba(0,0,0,.45)">
  <div style="font-family:Spectral,serif;font-size:16px;font-weight:600">Tenes cambios sin guardar</div>
  <div style="font-size:13px;color:{t['fg-muted']};margin-top:8px;line-height:19px">
    Si cerras ahora, lo que escribiste en el formulario se pierde.</div>
  <div style="display:flex;justify-content:flex-end;gap:9px;margin-top:16px">
    {btn(t, "Descartar", "ghost")}{btn(t, "Seguir editando")}</div></div>"""
    return (demo(t, "ConfirmDialog — las tres acciones irreversibles",
              "Principio 3: ninguna pasa por un &laquo;&iquest;Estas seguro?&raquo; generico. Cada una "
              "<strong>nombra su consecuencia concreta</strong>, que es distinta en las tres.",
              f'<div style="display:flex;gap:16px;flex-wrap:wrap">{veto}{cancel}{comment}</div>')
          + demo(t, "FormDialog — confirmar al cerrar con cambios sin guardar",
              "Lo unico que se rescata de <code>ModalBase</code> del legacy, como la prop "
              "<code>confirmOnDirtyClose</code> (#110). El dialogo es dueno de la mutacion; "
              "el formulario solo valida y llama <code>onSubmit</code>.", dirty))

def sc_comp_data(t):
    def dt(selected):
        head = ("".join(
            f'<th style="color:{t["fg-subtle"]};border-color:{t["border-strong"]};padding:8px 10px">'
            + (f'{c} <span style="color:{acc_text(t)}">&#9650;</span>' if c == "Postulacion" else c)
            + "</th>" for c in ["", "Jugador", "Karma", "Postulacion", "Estado", ""]))
        rows = ""
        for i, (name, ini, karma, when, st, lb) in enumerate([
                ("Carla Medina", "CM", "8 400", "hace 2 dias", "pending", "Candidate"),
                ("Diego Ruiz", "DR", "6 100", "hace 1 dia", "pending", "Candidate"),
                ("Eva Lorca", "EL", "8 000", "hace 4 horas", "pending", "Candidate"),
                ("Bruno Paz", "BP", "7 250", "hace 2 horas", "canceled", "Rejected")]):
            on = selected and i in (1, 2)
            box = (f'<span style="width:14px;height:14px;border-radius:4px;display:inline-block;'
                   f'background:{acc_solid(t) if on else "transparent"};'
                   f'border:1px solid {acc_solid(t) if on else t["border-strong"]};'
                   f'color:{on_acc(t)};font-size:10px;text-align:center;line-height:14px">'
                   f'{"&#10003;" if on else ""}</span>')
            rows += (f'<tr style="background:{t["raised"] if on else "transparent"}">'
                     f'<td style="border-color:{t["border"]};padding:8px 10px">{box}</td>'
                     f'<td style="border-color:{t["border"]};padding:8px 10px">'
                     f'<span class="row" style="gap:8px">{avatar(t, ini, 24)}'
                     f'<span style="font-size:13px">{name}</span></span></td>'
                     f'<td style="border-color:{t["border"]};padding:8px 10px;font-size:13px">{karma}</td>'
                     f'<td style="border-color:{t["border"]};padding:8px 10px;font-size:12px;'
                     f'color:{t["fg-muted"]}">{when}</td>'
                     f'<td style="border-color:{t["border"]};padding:8px 10px">{badge(t, st, lb)}</td>'
                     f'<td style="border-color:{t["border"]};padding:8px 10px;font-size:13px;'
                     f'color:{t["fg-subtle"]}">&#8942;</td></tr>')
        bar = (f'<div style="display:flex;align-items:center;gap:12px;padding:10px 12px;'
               f'background:{t["raised"]};border-radius:8px;margin-bottom:10px">'
               f'<span style="font-size:13px">2 seleccionados</span>'
               f'{btn(t, "Rechazar los 2", "secondary", small=True)}'
               f'<span style="font-size:11px;color:{t["fg-subtle"]}">Shift para rango</span></div>'
               if selected else "")
        pag = (f'<div style="display:flex;justify-content:space-between;align-items:center;'
               f'padding-top:12px;font-size:12px;color:{t["fg-subtle"]}">'
               f'<span>4 de 37</span><span class="row" style="gap:6px">'
               f'{btn(t, "Anterior", "secondary", small=True)}'
               f'<span style="color:{t["fg"]}">1</span><span>2</span><span>3</span>'
               f'{btn(t, "Siguiente", "secondary", small=True)}</span></div>')
        return (f'<div>{bar}<table><thead><tr>{head}</tr></thead><tbody>{rows}</tbody></table>{pag}</div>')

    coll = f"""<div style="border:1px solid {t['border-strong']};border-radius:10px;overflow:hidden">
      <div style="display:flex;align-items:center;gap:10px;padding:11px 14px;background:{t['surface']}">
        <span style="color:{t['fg-subtle']};font-size:11px">&#9660;</span>
        <span style="font-size:14px;font-weight:600;flex:1">Archivos de la mesa</span>
        <span style="font-size:12px;color:{t['fg-subtle']}">4</span>
        {btn(t, "Subir", "secondary", small=True)}</div>
      <div style="padding:12px 14px;border-top:1px solid {t['border']};font-size:13px;
           color:{t['fg-muted']}">mapa-cripta.png &middot; reglas-casa.pdf &middot; &hellip;</div></div>
      <div style="border:1px solid {t['border-strong']};border-radius:10px;margin-top:10px">
      <div style="display:flex;align-items:center;gap:10px;padding:11px 14px">
        <span style="color:{t['fg-subtle']};font-size:11px">&#9654;</span>
        <span style="font-size:14px;font-weight:600;flex:1">Historial de estados</span>
        <span style="font-size:12px;color:{t['fg-subtle']}">7</span></div></div>"""

    icons = f"""<div class="row" style="gap:8px;align-items:flex-start">
      {"".join(f'<span style="width:32px;height:32px;border-radius:8px;border:1px solid {t["border-strong"]};display:inline-flex;align-items:center;justify-content:center;font-size:13px;color:{t["fg-muted"]}">{i}</span>' for i in ["&#9998;", "&#128465;", "&#8681;", "&#8942;"])}
      <div style="margin-left:10px;background:{t['raised']};border:1px solid {t['border-strong']};
           border-radius:6px;padding:5px 9px;font-size:12px">Editar la mesa</div></div>"""

    return (demo(t, "DataTable — orden, seleccion con Shift y paginacion",
              "Sobre <code>PageResponse&lt;T&gt;</code>. La seleccion multiple con Shift se monta como "
              "Context <strong>alrededor de esta tabla</strong>, no global (#105). La tabla "
              "<strong>emite la accion</strong>; la mutacion es de quien la monta — el borrado que "
              "<code>TableComponent</code> hacia adentro llamando a <code>deleter</code> no vuelve.",
              f'<div style="display:flex;flex-direction:column;gap:20px">{dt(False)}'
              f'<div style="height:1px;background:{t["border"]}"></div>{dt(True)}</div>')
          + demo(t, "CollapsibleSection", "Bloque plegable con titulo y acciones en la cabecera: "
                 "el patron que el legacy repetia en <code>CardComponent</code> y <code>ListComponent</code>.",
                 coll)
          + demo(t, "IconAction", "Boton de icono con tooltip para las acciones de una fila o una ficha. "
                 "El tooltip no es decorativo: sin el, cuatro iconos seguidos son una adivinanza.", icons))

def sc_comp_inputs(t):
    picker = f"""<div style="width:430px;background:{t['surface']};border:1px solid {t['border-strong']};
     border-radius:12px;padding:18px">
  <div style="font-family:Spectral,serif;font-size:16px;font-weight:600">Adjuntar tu ficha</div>
  <div style="display:flex;gap:2px;border-bottom:1px solid {t['border']};margin:12px 0">
    <span style="padding:7px 12px;font-size:13px;font-weight:600;
      border-bottom:2px solid {acc_solid(t)}">Reusar</span>
    <span style="padding:7px 12px;font-size:13px;color:{t['fg-subtle']};
      border-bottom:2px solid transparent">Subir nuevo</span></div>
  {"".join(f'''<div style="display:flex;align-items:center;gap:10px;padding:8px 0;
       border-bottom:1px solid {t['border']}">
     <span style="width:13px;height:13px;border-radius:9999px;border:1px solid
       {acc_solid(t) if i == 0 else t['border-strong']};background:{acc_solid(t) if i == 0 else 'transparent'}"></span>
     <span style="font-size:13px;flex:1">{n}</span>
     <span class="mono" style="color:{t['fg-subtle']}">{s}</span>
     <span style="font-size:11px;color:{t['fg-subtle']};width:80px">{u}</span></div>'''
    for i, (n, s, u) in enumerate([("ficha-thalia-n5.pdf", "820 KB", "hace 3 dias"),
                                   ("ficha-korvin-n3.pdf", "760 KB", "hace 8 meses"),
                                   ("retrato-thalia.png", "1.2 MB", "hace 2 meses")]))}
  <div style="font-size:11px;color:{t['fg-subtle']};margin-top:10px">
    Reusar <strong>no duplica</strong> el archivo: se vincula el mismo. Maximo 10 MB por archivo.</div>
  <div style="display:flex;justify-content:flex-end;gap:9px;margin-top:14px">
    {btn(t, "Cancelar", "ghost")}{btn(t, "Adjuntar")}</div></div>"""

    editor = f"""<div style="width:430px">
  <div style="border:1px solid {t['border-strong']};border-radius:8px;overflow:hidden">
    <div style="display:flex;gap:2px;padding:6px 8px;background:{t['surface']};
         border-bottom:1px solid {t['border']}">
      {"".join(f'<span style="width:26px;height:26px;display:inline-flex;align-items:center;justify-content:center;font-size:12px;color:{t["fg-muted"]};border-radius:5px">{i}</span>' for i in ["<b>B</b>", "<i>I</i>", "&#8226;", "1.", "&#128279;"])}
    </div>
    <div style="padding:12px;font-size:13px;line-height:20px;min-height:96px">
      <strong>Una cripta sellada</strong> hace siglos vuelve a abrirse.<br><br>
      El grupo entra buscando un artefacto y encuentra algo que <i>preferiria no haber despertado</i>.</div>
  </div>
  <div style="font-size:11px;color:{t['fg-subtle']};margin-top:7px">
    Se sanitiza al enviar <strong>y</strong> al mostrar. TinyMCE no vuelve: necesita API key alojada.</div></div>"""

    sched = f"""<div style="width:430px;background:{t['surface']};border:1px solid {t['border-strong']};
     border-radius:12px;padding:16px">
  <div class="row" style="gap:8px;align-items:flex-end">
    <div style="flex:1">{field(t, "Dia", "Martes", kind="select")}</div>
    <div style="width:110px">{field(t, "Hora", "20:00")}</div>
    <div style="width:110px">{field(t, "Duracion", "3 h", kind="select")}</div>
  </div>
  <div style="margin-top:12px;padding:10px 12px;border-radius:8px;background:{t['state']['active']['bg']};
       color:{t['state']['active']['fg']};font-size:12px;line-height:18px">
    En tu hora local: <strong>martes 20:00 a 23:00</strong>.
    Se guarda en UTC y cada jugador la ve en la suya.</div>
  <div style="font-size:11px;color:{t['fg-subtle']};margin-top:10px">
    Los dias salen del tipo del dominio, no de un arreglo a mano — el del legacy tenia el orden mal.</div></div>"""

    return (demo(t, "FilePicker — subir <em>o</em> reusar",
              "El usuario accede a todo lo que subio en cualquier momento de su historia y puede volver "
              "a adjuntarlo sin duplicarlo (#65). Reemplaza a <code>ListFilesTable</code> y "
              "<code>UploadButton</code>.", picker)
          + demo(t, "RichTextEditor y RichTextView",
              "Texto enriquecido para la descripcion de la mesa (#62).", editor)
          + demo(t, "ScheduleEditor",
              "Dia de semana mas hora, siempre con la traduccion a hora local a la vista: es el bug que "
              "hace que alguien se pierda su propia sesion.", sched))

def sc_comp_shell(t):
    bell = menu(t, [
        ("&#9679;", "Te aceptaron en Hijos del Vacio", "2 h", True),
        ("&#9679;", "Beto publico una peticion", "1 d", True),
        ("&#9675;", "Tumbas de Sal paso a pausa", "3 d", False),
        None,
        ("", "<span style='color:%s'>Ver todas</span>" % acc_text(t), None, False),
    ], width=280, header="Notificaciones &middot; 2 sin leer")
    ctx = menu(t, [
        ("&#9823;", "Jugador", None, True),
        ("&#9819;", "Master", "1 mesa", False),
        ("&#9733;", "Admin", None, False),
    ], width=200, header="Cambiar de contexto")
    user = menu(t, [
        ("&#9679;", "Ana Valdez", None, False),
        None,
        ("&#9788;", "Tema claro", None, False),
        ("&#9990;", "Comentar el sistema", None, False),
        None,
        ("&#8629;", "Cerrar sesion", None, False),
    ], width=210)
    return (demo(t, "NotificationBell — panel abierto",
              "Contador y panel, alimentado por WebSocket. El mensaje es una <strong>senal de "
              "invalidacion</strong>, no el contenido: llega el tipo y el id, y el cliente refresca "
              "esa rama de la cache.",
              f'<div class="row" style="gap:30px;align-items:flex-start">'
              f'<span style="position:relative;font-size:16px;color:{t["fg-muted"]}">&#9733;'
              f'<span style="position:absolute;top:-6px;right:-9px;background:{acc_solid(t)};'
              f'color:{on_acc(t)};font-size:10px;font-weight:700;border-radius:9999px;'
              f'padding:1px 5px">2</span></span>{bell}</div>')
          + demo(t, "ContextSwitcher — desplegado",
              "Solo aparecen los contextos que la persona tiene. <strong>Master aparece con el rol "
              "o con al menos una mesa asignada</strong> (#135) — de ahi el &laquo;1 mesa&raquo;. "
              "Quien tiene un solo contexto no ve el selector.",
              f'<div class="row" style="gap:30px;align-items:flex-start">{chip(t,"Jugador")}{ctx}</div>')
          + demo(t, "UserMenu",
              "Avatar, tema y cerrar sesion, sobre <code>dropdown-menu</code>. El click-fuera escrito "
              "a mano con <code>window.addEventListener</code> desaparece: lo resuelve Radix. "
              "Aca cuelga tambien la accion global de feedback (#133).",
              f'<div class="row" style="gap:30px;align-items:flex-start">{avatar(t,"AV",30)}{user}</div>'))

screen("components-dialogs.html", "Diálogos — ConfirmDialog y FormDialog", "Components", sc_comp_dialogs,
       "Las tres acciones irreversibles del sistema, cada una nombrando su consecuencia concreta, "
       "y la confirmacion al cerrar un formulario a medio llenar.")
screen("components-data.html", "Datos — DataTable, CollapsibleSection, IconAction", "Components", sc_comp_data,
       "El listado paginado con seleccion por rango, el bloque plegable y el boton de icono con tooltip.")
screen("components-inputs.html", "Entradas — FilePicker, RichText, ScheduleEditor", "Components", sc_comp_inputs,
       "Los tres controles con logica propia: reusar archivos sin duplicarlos, texto enriquecido "
       "sanitizado, y horario con su traduccion a hora local siempre visible.")
screen("components-shell.html", "Shell — NotificationBell, ContextSwitcher, UserMenu", "Components", sc_comp_shell,
       "Las tres piezas globales del layout, en su estado abierto — que es el que faltaba.")


# ============ 12. admin context ============
def sc_admin_users(t):
    def u(name, ini, roles, karma, status, lb, imp=True):
        return row(t, [
            cell(t, f'<span class="row" style="gap:8px">{avatar(t, ini, 26)}'
                    f'<span style="font-size:13px">{name}</span></span>', w="210px"),
            cell(t, roles, w="150px", muted=True, size="12px"),
            cell(t, karma, w="70px", size="13px"),
            badge(t, status, lb),
            (f'<span style="font-size:12px;color:{acc_text(t)};width:80px">Ver como</span>' if imp
             else f'<span style="font-size:11px;color:{t["fg-subtle"]};width:80px">&mdash;</span>'),
            btn(t, "Abrir", "secondary", small=True)])
    return shell(t, "Admin", section(t, "Usuarios",
        u("Ana Valdez", "AV", "Jugador &middot; Master", "8 240", "open", "Allowed")
        + u("Carla Medina", "CM", "Jugador", "8 400", "open", "Allowed")
        + u("Diego Ruiz", "DR", "Jugador", "6 100", "blocked", "Blocked")
        + u("Beto Nunez", "BN", "Jugador &middot; Admin", "7 900", "open", "Allowed", imp=False)
        + u("Sonia Paz", "SP", "Owner", "8 000", "open", "Allowed", imp=False))
        + f'<div style="font-size:12px;color:{t["fg-subtle"]};line-height:19px">'
          f'<strong>Ver como</strong> no aparece sobre un <strong>Admin</strong> ni sobre el '
          f'<strong>Owner</strong>: seria escalada de privilegios (#140).</div>')

def sc_admin_impersonation(t):
    start = confirm(t, "Ver como Ana Valdez",
        "Vas a ver la aplicacion como la ve Ana y <strong>podes actuar por ella</strong>. "
        "<strong>Ana recibe una notificacion</strong> ahora mismo, con tu motivo. "
        "La sesion se cierra sola a los 30 minutos.", "Entrar como Ana", danger=False,
        extra=f"""<div style="margin-top:12px">
          {field(t, "Motivo", "No le aparece el boton para publicar una peticion.",
                 hint="Obligatorio. Ana lo va a leer.")}</div>
        <div style="margin-top:12px;font-size:12px;color:{t['fg-subtle']};line-height:18px">
          <strong>Queda bloqueado:</strong> vetar, cancelar mesa, confirmar comentario, y todo lo
          que toque comentarios &mdash; incluidos los borradores de Ana.</div>""")

    banner = f"""<div style="background:{t['state']['warning']['bg']};color:{t['state']['warning']['fg']};
       padding:10px 16px;display:flex;align-items:center;gap:12px;font-size:13px">
      <span class="dot" style="background:{t['state']['warning']['dot']}"></span>
      <span style="flex:1">Estas viendo como <strong>Ana Valdez</strong> &middot;
        quedan 24 min &middot; Ana fue notificada</span>
      {btn(t, "Salir", "secondary", small=True)}</div>"""
    inner = f"""<div style="padding:18px">
      <div class="row" style="gap:10px;margin-bottom:12px">
        <span style="font-family:Spectral,serif;font-size:17px;font-weight:600">Hijos del Vacio</span>
        {badge(t,'active','InProgress')}</div>
      <div class="row" style="gap:8px">
        {btn(t, "Publicar peticion", "primary", small=True)}
        {btn(t, "Registrar asistencia", "secondary", small=True)}
        <span style="display:inline-flex;align-items:center;gap:7px;padding:5px 12px;border-radius:8px;
          border:1px dashed {t['border-strong']};font-size:12px;color:{t['fg-subtle']}">
          &#128274; Cancelar la mesa</span>
        <span style="display:inline-flex;align-items:center;gap:7px;padding:5px 12px;border-radius:8px;
          border:1px dashed {t['border-strong']};font-size:12px;color:{t['fg-subtle']}">
          &#128274; Vetar jugador</span>
      </div>
      <div style="font-size:12px;color:{t['fg-subtle']};margin-top:12px">
        Lo bloqueado no se oculta: se muestra con candado y su motivo al pasar el mouse. Un boton
        que desaparece se lee como un bug; uno con candado se lee como una regla.</div>
    </div>"""
    active = (f'<div style="border:1px solid {t["state"]["warning"]["dot"]};border-radius:12px;'
              f'overflow:hidden">{banner}{mini_header(t, False)}{inner}</div>')

    notif = f"""<div style="width:430px;background:{t['surface']};border:1px solid {t['border-strong']};
       border-radius:10px;padding:14px">
      <div class="row" style="gap:8px;margin-bottom:6px">
        <span class="dot" style="background:{acc_solid(t)}"></span>
        <span style="font-size:12px;color:{t['fg-subtle']}">hace 2 minutos</span></div>
      <div style="font-size:14px;line-height:21px">
        <strong>Un administrador accedio a tu cuenta.</strong></div>
      <div style="font-size:13px;color:{t['fg-muted']};margin-top:6px;line-height:20px">
        Motivo: &laquo;No le aparece el boton para publicar una peticion.&raquo;</div>
      <div style="font-size:11px;color:{t['fg-subtle']};margin-top:10px;line-height:17px">
        No se muestra que administrador fue. El registro completo existe y solo lo ve el Owner.</div></div>"""

    audit = f"""<div style="width:100%">
      {row(t, [cell(t, "game_tables", w="130px", mono=True, size="12px"),
               cell(t, "Update", w="70px", size="12px"),
               cell(t, "Ana Valdez", w="120px", size="13px"),
               badge(t, "warning", "via admin"),
               cell(t, "hace 2 min", w="80px", muted=True, size="12px")])}
      {row(t, [cell(t, "table_tasks", w="130px", mono=True, size="12px"),
               cell(t, "Create", w="70px", size="12px"),
               cell(t, "Ana Valdez", w="120px", size="13px"),
               badge(t, "warning", "via admin"),
               cell(t, "hace 3 min", w="80px", muted=True, size="12px")])}
      {row(t, [cell(t, "game_tables", w="130px", mono=True, size="12px"),
               cell(t, "Update", w="70px", size="12px"),
               cell(t, "Ana Valdez", w="120px", size="13px"),
               cell(t, "", w="90px"),
               cell(t, "hace 1 dia", w="80px", muted=True, size="12px")], last=True)}
      <div style="font-size:12px;color:{t['fg-subtle']};margin-top:10px;line-height:19px">
        Asi lo ve <strong>Ana</strong> y cualquier admin: la marca dice que fue un administrador,
        no cual. En el panel del <strong>Owner</strong> la misma fila muestra el nombre.</div></div>"""

    return (demo(t, "1. Iniciar &mdash; el motivo es obligatorio",
              "El dialogo dice las tres cosas que importan antes de entrar: que podes actuar, que la "
              "persona se entera ahora, y que hay cosas que no vas a poder hacer.", start)
          + demo(t, "2. Dentro &mdash; la sesion nunca se disimula",
              "Banda permanente arriba, con el tiempo que queda y la salida siempre a mano. "
              "<strong>Lo bloqueado se muestra con candado, no se esconde.</strong>", active)
          + demo(t, "3. Lo que recibe la persona",
              "Notificacion inmediata con el motivo. Sin nombre del admin (#140).", notif)
          + demo(t, "4. Como queda en la auditoria",
              "<code>updated_by</code> sigue siendo Ana, asi que &laquo;que cambio Ana&raquo; se "
              "responde igual que siempre; el rastro del admin cuelga de <code>impersonation_id</code>.",
              audit))

def sc_admin_settings(t):
    def setting(key, label, value, note, warn=None):
        return f"""<div style="display:flex;gap:16px;align-items:flex-start;padding:13px 0;
             border-bottom:1px solid {t['border']}">
          <div style="flex:1">
            <div style="font-size:14px;font-weight:600">{label}</div>
            <div class="mono" style="color:{t['fg-subtle']};margin-top:2px">{key}</div>
            <div style="font-size:12px;color:{t['fg-muted']};margin-top:5px;line-height:18px">{note}</div>
            {f'''<div style="margin-top:8px;padding:8px 11px;border-radius:7px;
                 background:{t['state']['warning']['bg']};color:{t['state']['warning']['fg']};
                 font-size:12px;line-height:18px">&#9888; {warn}</div>''' if warn else ''}
          </div>
          <div style="width:170px;flex:none">
            <div style="padding:8px 11px;border:1px solid {t['border-strong']};border-radius:8px;
                 font-size:13px;background:{t['canvas']}">{value}</div></div>
        </div>"""
    tabs = "".join(
        f'<span style="padding:8px 14px;font-size:13px;'
        + (f'font-weight:600;border-bottom:2px solid {acc_solid(t)}' if i == 0
           else f'color:{t["fg-subtle"]};border-bottom:2px solid transparent')
        + f'">{x}</span>' for i, x in enumerate(["Negocio", "Limites y cuotas", "Textos"]))
    return shell(t, "Admin", f"""
  <div style="font-family:Spectral,serif;font-size:20px;font-weight:600">Configuracion</div>
  <div style="font-size:13px;color:{t['fg-muted']};margin-top:4px">
    Cada cambio queda auditado como cualquier otra entidad. <strong>Ningun secreto vive aca</strong>:
    las claves y el HMAC siguen en el entorno.</div>
  <div style="display:flex;gap:2px;border-bottom:1px solid {t['border']};margin:14px 0 4px">{tabs}</div>
  {setting("karma.default", "Karma inicial", "8000",
           "El que recibe una cuenta nueva. Escala 0 a 10000.")}
  {setting("karma.decay_window_months", "Ventana de decaimiento del karma", "12 meses",
           "Cuanto tarda una valoracion en dejar de pesar.",
           warn="Cambiarlo altera el calculo <strong>retroactivamente</strong> en el proximo recalculo. "
                "No es un ajuste cosmetico.")}
  {setting("visibility.expiry_days", "Caducidad de visibilidad de perfiles", "14 dias",
           "Desde que la mesa entra en Finished o Canceled. En Pause el reloj no corre.",
           warn="Cambia <strong>quien puede ver a quien</strong> de inmediato.")}
  {setting("admin_queue.claim_timeout_minutes", "Timeout de reserva de la bandeja", "15 min",
           "Una reserva sin resolver se libera sola y el item reaparece para todos.")}""")

def sc_admin_moderation(t):
    def cm(kind, direction, text, table, when):
        s = t["state"][kind]
        return f"""<div style="padding:14px 0;border-bottom:1px solid {t['border']}">
          <div class="row" style="gap:8px;margin-bottom:7px">
            <span class="dot" style="background:{s['dot']}"></span>
            <span style="font-size:12px;color:{s['fg']}">
              {'Positivo' if kind == 'open' else 'Negativo'}</span>
            <span style="font-size:12px;color:{t['fg-subtle']}">
              &middot; {direction} &middot; {table} &middot; {when}</span></div>
          <div style="font-size:14px;line-height:21px;color:{t['fg-muted']}">&laquo;{text}&raquo;</div>
          <div class="row" style="gap:8px;margin-top:10px">
            {btn(t, "Aprobar", "primary", small=True)}
            {btn(t, "Rechazar", "secondary", small=True)}</div></div>"""
    return shell(t, "Admin", section(t, "Comentarios por moderar",
        cm("canceled", "jugador a master", "Cancelo tres sesiones seguidas sin avisar con tiempo.",
           "El Rio Negro", "hace 2 h")
        + cm("open", "master a jugador", "Siempre puntual y con la ficha lista.",
             "La Cripta de Ondrak", "hace 5 h")
        + cm("canceled", "jugador a jugador", "Interrumpia bastante durante las sesiones.",
             "Hijos del Vacio", "hace 1 d"))
        + f'<div style="font-size:12px;color:{t["fg-subtle"]};line-height:19px">'
          f'Se ve el <strong>contenido</strong> y a quien va dirigido. <strong>Nunca el autor</strong> '
          f'&mdash; ni para vos, ni para el Owner, ni para quien desarrolla el sistema (#43, #45). '
          f'Aprobar dispara el recalculo de karma de esa persona.</div>')

def sc_admin_catalogs(t):
    def c(name, group, tables, status, lb):
        return row(t, [
            cell(t, name, w="170px"),
            cell(t, group, w="130px", muted=True, size="13px"),
            cell(t, tables, w="80px", muted=True, size="13px"),
            badge(t, status, lb),
            cell(t, f'<span style="color:{acc_text(t)};font-size:12px">Fusionar</span>', w="90px")])
    return shell(t, "Admin", section(t, "Sistemas",
        c("D&amp;D 5e", "canonico", "14 mesas", "open", "Aprobado")
        + c("DND5", "&rarr; D&amp;D 5e", "3 mesas", "draft", "Sinonimo")
        + c("DANDD", "&rarr; D&amp;D 5e", "1 mesa", "draft", "Sinonimo")
        + c("Pathfinder 2e", "canonico", "6 mesas", "open", "Aprobado")
        + c("Vampiro V5", "sin grupo", "1 mesa", "pending", "Propuesto"),
        action=btn(t, "Nuevo sistema", "primary", small=True))
        + f'<div style="font-size:12px;color:{t["fg-subtle"]};line-height:19px">'
          f'La equivalencia es <strong>pura y simetrica</strong>: buscar cualquier miembro del grupo '
          f'trae todas las mesas del grupo. Los alias apuntan <strong>siempre al canonico</strong>, '
          f'nunca a otro alias. Un tag propuesto y sin aprobar no es visible para los jugadores, '
          f'pero la mesa se publica igual y lo conserva.</div>')

screen("screen-admin-users.html", "Usuarios — /admin/users", "Screens", sc_admin_users,
       "El listado desde donde arranca «ver como». La accion no aparece sobre un Admin ni sobre el "
       "Owner: seria escalada de privilegios (#140).")
screen("screen-admin-impersonation.html", "«Ver como» — el flujo completo", "Screens", sc_admin_impersonation,
       "Las cuatro pantallas de la funcion mas delicada del sistema: iniciar con motivo obligatorio, "
       "la sesion siempre visible, lo que recibe la persona, y como queda el rastro. Todo lo que toca "
       "comentarios esta fuera, porque #43 no es negociable.")
screen("screen-admin-settings.html", "Configuración — /admin/settings", "Screens", sc_admin_settings,
       "Los ajustes que hoy viven en el codigo. Dos de ellos cambian comportamiento retroactivo o "
       "visibilidad entre personas, y la pantalla lo dice en vez de dejarlos parecer cosmeticos (#141).")
screen("screen-admin-moderation.html", "Moderación — /admin/moderation", "Screens", sc_admin_moderation,
       "Se modera el contenido sin saber quien lo escribio: es la unica excepcion a la visibilidad "
       "total del admin (#45), y esta por encima de ese permiso.")
screen("screen-admin-catalogs.html", "Catálogos — /admin/catalogs", "Screens", sc_admin_catalogs,
       "Grupos de sinonimos planos: cada alias apunta al canonico, nunca a otro alias. Fusionar y "
       "separar son las dos operaciones que justifican la pantalla.")


# ============ 13. public entry + remaining admin + owner ============
def sc_login(t):
    card = f"""<div style="width:400px;background:{t['surface']};border:1px solid {t['border-strong']};
     border-radius:14px;padding:30px;text-align:center">
  <div style="font-family:Spectral,serif;font-size:26px;font-weight:700;letter-spacing:.01em">
    Central<span style="color:{acc_text(t)}">Dungeon</span></div>
  <div style="font-size:13px;color:{t['fg-muted']};margin-top:8px;line-height:20px">
    Las mesas de la comunidad, en un solo lugar.</div>
  <div style="margin-top:22px">
    <button class="btn" style="background:{acc_solid(t)};color:{on_acc(t)};width:100%;padding:12px">
      Entrar con Discord</button></div>
  <div style="font-size:12px;color:{t['fg-subtle']};margin-top:14px;line-height:18px">
    Es la unica forma de entrar: no hay registro propio ni contrasena.
    Necesitas ser miembro del servidor.</div>
</div>"""
    return (f'<div style="background:linear-gradient(-45deg, {", ".join(BRAND_SOURCE["gradient"])});'
            f'padding:44px;border-radius:12px;display:flex;justify-content:center">{card}</div>'
            f'<div style="font-size:12px;color:{t["fg-subtle"]};margin-top:10px;line-height:18px">'
            f'El fondo es el gradiente propio de la comunidad, el mismo de '
            f'<code>links.centraldungeon.org</code>. Es el unico lugar donde aparece completo: '
            f'adentro de la aplicacion los catorce colores de estado necesitan un fondo que no compita.</div>')

def sc_callback(t):
    def step(title, body, actions, tone=None):
        head = ""
        if tone:
            s = t["state"][tone]
            head = (f'<div style="padding:9px 12px;border-radius:8px;background:{s["bg"]};'
                    f'color:{s["fg"]};font-size:12px;margin-bottom:14px">{title}</div>')
            title = ""
        return f"""<div style="width:370px;background:{t['surface']};border:1px solid {t['border-strong']};
             border-radius:12px;padding:22px">{head}
          {f'<div style="font-family:Spectral,serif;font-size:17px;font-weight:600">{title}</div>' if title else ''}
          <div style="font-size:13px;color:{t['fg-muted']};margin-top:8px;line-height:20px">{body}</div>
          <div style="display:flex;gap:9px;margin-top:18px">{actions}</div></div>"""
    a = step("Verificando tu cuenta", "Estamos comprobando que seas miembro del servidor de "
             "CentralDungeon en Discord.",
             f'<div style="height:34px;width:100%;border-radius:8px;background:{t["raised"]}"></div>')
    b = step("No estas en el servidor",
             "CentralDungeon es la aplicacion de una comunidad de Discord, asi que primero hay que "
             "entrar al servidor. Cuando aceptes, volves aca y seguimos.",
             btn(t, "Unirme al servidor") + btn(t, "Ahora no", "ghost"), tone="pending")
    c = step("Listo, ya sos miembro",
             "Te reconocimos. Falta un paso corto para terminar de crear tu cuenta.",
             btn(t, "Continuar"), tone="open")
    d = step("No se pudo entrar",
             "Declinaste la invitacion al servidor. Sin membresia no hay cuenta: podes volver a "
             "intentarlo cuando quieras.",
             btn(t, "Volver a intentar", "secondary"), tone="canceled")
    return (f'<div style="display:flex;gap:16px;flex-wrap:wrap">{a}{b}{c}{d}</div>'
            f'<div style="font-size:12px;color:{t["fg-subtle"]};margin-top:14px;line-height:19px">'
            f'Los cuatro estados del retorno de OAuth. <strong>No se rechaza con un error seco</strong>: '
            f'se ofrece la invitacion y recien si la persona la declina se corta (#38). '
            f'El tercero encadena con <code>/onboarding</code> (#134).</div>')

def sc_admin_tables(t):
    def tb(name, master, state, lb, when, why=None):
        base = row(t, [
            cell(t, f'<span style="font-family:Spectral,serif;font-weight:600">{name}</span>', w="200px"),
            cell(t, master, w="110px", muted=True, size="13px"),
            badge(t, state, lb),
            cell(t, when, w="90px", muted=True, size="12px"),
            btn(t, "Revisar", "primary" if state == "pending" else "secondary", small=True)])
        if why:
            base += (f'<div style="font-size:12px;color:{t["fg-muted"]};padding:0 0 11px;margin-top:-6px;'
                     f'border-bottom:1px solid {t["border"]}">Le pediste: &laquo;{why}&raquo;</div>')
        return base
    return shell(t, "Admin", section(t, "Mesas esperando revision",
        tb("Hijos del Vacio", "Beto", "pending", "Preparation", "hace 10 min")
        + tb("Tumbas de Sal", "Cira", "pending", "Preparation", "hace 3 h")
        + tb("El Jardin de Hierro", "Ana", "warning", "ChangesRequested", "hace 2 d",
             why="Falta el sistema y la descripcion no dice el tono de la campana.")
        + tb("Ecos del Sur", "&mdash;", "draft", "Unassigned", "hace 1 d"))
        + f'<div style="font-size:12px;color:{t["fg-subtle"]};line-height:19px">'
          f'Pedir correcciones exige justificacion: es lo que el master ve al volver. '
          f'Una mesa en <strong>Unassigned</strong> la creo un admin y le falta master; al asignarselo '
          f'pasa directo a <strong>Opened</strong>, sin revision.</div>')

def sc_admin_requests(t):
    def rq(who, ini, kind, detail, when):
        return row(t, [
            cell(t, f'<span class="row" style="gap:8px">{avatar(t, ini, 24)}'
                    f'<span style="font-size:13px">{who}</span></span>', w="180px"),
            cell(t, kind, w="180px", size="13px"),
            cell(t, detail, muted=True, size="13px"),
            cell(t, when, w="80px", muted=True, size="12px"),
            btn(t, "Resolver", "secondary", small=True)])
    return shell(t, "Admin", section(t, "Solicitudes",
        rq("Carla Medina", "CM", "Rol de master", "Dirigio 2 mesas asignadas, las dos terminadas", "hace 1 d")
        + rq("Diego Ruiz", "DR", "Que se abra una mesa", "Pathfinder 2e, jueves, sin master", "hace 2 d")
        + rq("Ana Valdez", "AV", "Pausa de mesa", "La Cripta de Ondrak &middot; 3 semanas", "hace 2 h")
        + rq("Beto Nunez", "BN", "Veto de jugador", "Hijos del Vacio &middot; pedido por un Secondary", "hace 5 h")
        + rq("Eva Lorca", "EL", "Peticion general", "Propone un canal de busqueda de suplentes", "hace 3 d"))
        + f'<div style="font-size:12px;color:{t["fg-subtle"]};line-height:19px">'
          f'Los cinco flujos viven en <strong>una sola tabla</strong> con referencia polimorfica: '
          f'agregar un sexto es un valor de enum, no una columna. Resolver siempre pide justificacion, '
          f'apruebe o rechace.</div>')

def sc_admin_feedback(t):
    def fb(text, when, status, lb):
        return f"""<div style="padding:13px 0;border-bottom:1px solid {t['border']}">
          <div class="row" style="gap:8px;margin-bottom:6px">
            {badge(t, status, lb)}
            <span style="font-size:12px;color:{t['fg-subtle']}">{when}</span></div>
          <div style="font-size:14px;line-height:21px;color:{t['fg-muted']}">&laquo;{text}&raquo;</div>
          <div class="row" style="gap:8px;margin-top:9px">
            {btn(t, "Marcar revisado", "secondary", small=True)}</div></div>"""
    return shell(t, "Admin", section(t, "Feedback del sistema",
        fb("Cuesta encontrar las mesas de sistemas que no son D&amp;D. El filtro esta pero no se ve.",
           "hace 4 h", "pending", "Nuevo")
        + fb("Estaria bueno saber cuanto falta para que cierren las postulaciones de una mesa.",
             "hace 1 d", "pending", "Nuevo")
        + fb("El horario me aparecia mal hasta que cambie la zona de mi compu.",
             "hace 3 d", "done", "Revisado"))
        + f'<div style="font-size:12px;color:{t["fg-subtle"]};line-height:19px">'
          f'<strong>Anonimo sin excepcion</strong>: la fila nace sin autor, no hay circuito de borrador '
          f'que anonimizar. <strong>No pasa por moderacion</strong> &mdash; el estado sirve para marcar '
          f'lo ya revisado, no para aprobar o rechazar. Uno cada 24 horas por persona, con token opaco.</div>')

def sc_owner_audit(t):
    def al(entity, action, who, when, via=False, diff=None):
        base = row(t, [
            cell(t, entity, w="150px", mono=True, size="12px"),
            cell(t, action, w="70px", size="12px"),
            cell(t, who, w="130px", size="13px"),
            (badge(t, "warning", "via Beto Nunez") if via else cell(t, "", w="130px")),
            cell(t, when, w="90px", muted=True, size="12px"),
            btn(t, "Ver diff", "secondary", small=True)])
        if diff:
            base += f"""<div style="padding:0 0 12px;margin-top:-4px;border-bottom:1px solid {t['border']}">
              <div class="mono" style="line-height:19px">
                <div style="color:{t['state']['canceled']['fg']}">- max_players: 5</div>
                <div style="color:{t['state']['open']['fg']}">+ max_players: 6</div></div></div>"""
        return base
    return shell(t, "Owner", section(t, "Auditoria",
        al("game_tables", "Update", "Ana Valdez", "hace 2 min", via=True, diff=True)
        + al("table_tasks", "Create", "Ana Valdez", "hace 3 min", via=True)
        + al("users_roles", "Create", "Beto Nunez", "hace 1 h")
        + al("system_settings", "Update", "Beto Nunez", "hace 2 h")
        + al("files", "Delete", "Sonia Paz", "hace 1 d"),
        action=chip(t, "Filtrar"))
        + f'<div style="font-size:12px;color:{t["fg-subtle"]};line-height:19px">'
          f'Se guarda <strong>solo el diff</strong>, no la fila entera: mantiene la tabla manejable y '
          f'hace legible cada linea. <strong>Este es el unico lugar donde se ve que admin actuo detras '
          f'de un &laquo;ver como&raquo;</strong> (#140). '
          f'<strong>Los comentarios no se auditan</strong> &mdash; auditarlos guardaria autor y '
          f'contenido juntos, que es exactamente lo que #43 prohibe.</div>')

def sc_owner_storage(t):
    def f(name, size, deleted, refs):
        return row(t, [
            f'<span style="width:14px;flex:none"><span style="width:13px;height:13px;border-radius:4px;'
            f'display:inline-block;border:1px solid {t["border-strong"]}"></span></span>',
            cell(t, name),
            cell(t, size, w="80px", muted=True, size="12px", mono=True),
            cell(t, deleted, w="130px", muted=True, size="12px"),
            (cell(t, f'<span style="color:{t["state"]["canceled"]["fg"]}">{refs}</span>', w="120px", size="12px")
             if refs != "sin referencias" else cell(t, refs, w="120px", muted=True, size="12px"))])
    return shell(t, "Owner", f"""
  <div style="font-family:Spectral,serif;font-size:20px;font-weight:600">Borrado fisico</div>
  <div style="font-size:13px;color:{t['fg-muted']};margin-top:4px;line-height:20px;max-width:74ch">
    El borrado logico de la aplicacion <strong>nunca toca los bytes</strong>. Esto si, y tambien en el
    almacenamiento en la nube. Es mantenimiento deliberado, no el efecto secundario de que alguien
    apriete &laquo;eliminar&raquo;.</div>
  <div style="margin-top:16px">
  {section(t, "Archivos en borrado logico",
     f("mapa-cripta-v1.png", "2.4 MB", "hace 8 meses", "sin referencias")
     + f("ficha-korvin-n1.pdf", "740 KB", "hace 11 meses", "sin referencias")
     + f("sesion-03-notas.pdf", "1.1 MB", "hace 6 meses", "1 entrega activa")
     + f("retrato-viejo.png", "3.2 MB", "hace 14 meses", "sin referencias"))}
  </div>
  <div class="row" style="gap:10px">
    <button class="btn" style="background:{t['state']['canceled']['dot']};color:{NEUTRAL['950']};
      padding:9px 18px;font-size:13px">Borrar definitivamente</button>
    <span style="font-size:12px;color:{t['fg-subtle']}">3 seleccionables &middot; 6.4 MB</span></div>
  <div style="font-size:12px;color:{t['fg-subtle']};margin-top:12px;line-height:19px">
    Los que tienen referencias activas <strong>no se pueden seleccionar</strong>. Es la clase de
    problema que el proyecto ya sufrio: <code>table_files</code> apuntando a archivos borrados.</div>""")

def sc_owner_migrate(t):
    return shell(t, "Owner", f"""
  <div style="font-family:Spectral,serif;font-size:20px;font-weight:600">Migrar cuenta</div>
  <div style="font-size:13px;color:{t['fg-muted']};margin-top:4px;line-height:20px;max-width:74ch">
    Para cuando alguien pierde su cuenta de Discord. <strong>No hay recuperacion automatica ni
    self-service</strong>: no existe contrasena propia, ni email, ni ningun otro dato con el que
    reconocer a una persona. Esto cubre el caso real &mdash; alguien conocido de la comunidad que
    vuelve con otra cuenta.</div>
  <div style="display:flex;gap:16px;align-items:center;margin-top:20px;flex-wrap:wrap">
    <div style="width:250px;background:{t['surface']};border:1px solid {t['border-strong']};
         border-radius:10px;padding:14px">
      <div class="lbl" style="color:{t['fg-subtle']}">Cuenta vieja</div>
      <div class="row" style="gap:9px;margin-top:9px">{avatar(t, "AV", 32)}
        <div><div style="font-size:13px;font-weight:600">Ana Valdez</div>
        <div style="font-size:11px;color:{t['fg-subtle']}">anavaldez</div></div></div>
      <div style="font-size:12px;color:{t['fg-muted']};margin-top:10px;line-height:18px">
        karma 8 240 &middot; 3 mesas &middot; 9 comentarios &middot; 12 archivos</div>
    </div>
    <span style="font-size:20px;color:{acc_text(t)}">&rarr;</span>
    <div style="width:250px;background:{t['surface']};border:1px solid {t['border-strong']};
         border-radius:10px;padding:14px">
      <div class="lbl" style="color:{t['fg-subtle']}">Cuenta nueva</div>
      <div class="row" style="gap:9px;margin-top:9px">{avatar(t, "AV", 32)}
        <div><div style="font-size:13px;font-weight:600">Ana V.</div>
        <div style="font-size:11px;color:{t['fg-subtle']}">ana_valdez2</div></div></div>
      <div style="font-size:12px;color:{t['fg-muted']};margin-top:10px;line-height:18px">
        cuenta nueva &middot; sin historial</div>
    </div>
  </div>
  <div style="margin-top:18px;max-width:530px">
    {field(t, "Motivo", "Perdio el acceso a su Discord. Confirmado por dos masters.",
           hint="Obligatorio. Queda en la auditoria como cualquier accion del owner.")}
  </div>
  <div style="margin-top:16px;padding:12px 14px;border-radius:8px;
       background:{t['state']['canceled']['bg']};color:{t['state']['canceled']['fg']};
       font-size:13px;line-height:19px;max-width:530px">
    Se trasladan karma, comentarios recibidos, historial de mesas y archivos.
    <strong>La cuenta vieja queda bloqueada y no vuelve.</strong> No se puede deshacer.</div>
  <div class="row" style="gap:10px;margin-top:16px">
    <button class="btn" style="background:{t['state']['canceled']['dot']};color:{NEUTRAL['950']};
      padding:10px 20px;font-size:14px">Migrar</button>{btn(t, "Cancelar", "ghost")}</div>""")

screen("screen-login.html", "Entrar — /login", "Screens", sc_login,
       "El unico lugar donde el gradiente de la comunidad aparece completo. Adentro de la aplicacion "
       "los catorce colores de estado necesitan un fondo que no compita.")
screen("screen-auth-callback.html", "Retorno del OAuth — /auth/callback", "Screens", sc_callback,
       "Los cuatro estados del retorno. No se rechaza con un error seco: se ofrece la invitacion al "
       "servidor y recien si la persona la declina se corta (#38).")
screen("screen-admin-tables.html", "Mesas por revisar — /admin/tables", "Screens", sc_admin_tables,
       "Pedir correcciones exige justificacion: es lo que el master ve al volver. Una mesa en "
       "Unassigned la creo un admin y le falta master.")
screen("screen-admin-requests.html", "Solicitudes — /admin/requests", "Screens", sc_admin_requests,
       "Los cinco flujos en una sola tabla con referencia polimorfica: agregar un sexto es un valor "
       "de enum, no una columna.")
screen("screen-admin-feedback.html", "Feedback — /admin/feedback", "Screens", sc_admin_feedback,
       "Anonimo sin excepcion y sin moderacion: el estado marca lo revisado, no aprueba ni rechaza.")
screen("screen-owner-audit.html", "Auditoría — /owner/audit", "Screens", sc_owner_audit,
       "Solo el diff, no la fila entera. Es el unico lugar donde se ve que admin actuo detras de un "
       "«ver como» (#140), y los comentarios siguen fuera de la auditoria (#43).")
screen("screen-owner-storage.html", "Borrado físico — /owner/storage", "Screens", sc_owner_storage,
       "El borrado logico nunca toca los bytes. Los archivos con referencias activas no se pueden "
       "seleccionar: es la clase de problema que el proyecto ya sufrio.")
screen("screen-owner-migrate.html", "Migrar cuenta — /owner/users/:id/migrate", "Screens", sc_owner_migrate,
       "Sin recuperacion automatica ni self-service: no hay contrasena, email ni dato con el que "
       "reconocer a alguien. Cubre el caso real y queda auditado.")
