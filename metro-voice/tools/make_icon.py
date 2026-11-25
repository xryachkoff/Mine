import math, sys
out_dir, res_dir = sys.argv[1], sys.argv[2]
INK, BG, WHITE, ACCENT, LAMP = '#1C1A1A', '#D0EDF6', '#FFFFFF', '#EB4B3D', '#FFC94A'

def rrect(x, y, w, h, r):
    return (f"M{x+r},{y} H{x+w-r} A{r},{r} 0 0 1 {x+w},{y+r} V{y+h-r} A{r},{r} 0 0 1 {x+w-r},{y+h} "
            f"H{x+r} A{r},{r} 0 0 1 {x},{y+h-r} V{y+r} A{r},{r} 0 0 1 {x+r},{y} Z")
def circle(cx, cy, r):
    return f"M{cx-r},{cy} A{r},{r} 0 1 0 {cx+r},{cy} A{r},{r} 0 1 0 {cx-r},{cy} Z"
def arc(cx, cy, r, deg):
    a = math.radians(deg); dx, dy = r*math.cos(a), r*math.sin(a)
    return f"M{cx+dx:.2f},{cy-dy:.2f} A{r},{r} 0 0 1 {cx+dx:.2f},{cy+dy:.2f}"

# The mascot: a train front wearing headphones. Fits the 66dp safe zone of an adaptive icon.
body = rrect(39, 37, 30, 42, 8)
glass = rrect(44, 43, 20, 14, 4)
stripe = "M39,62 H69 V66.5 H39 Z"
lamps = [circle(46, 72.5, 2.4), circle(62, 72.5, 2.4)]
rail = "M45,84 H63"
band = "M35.5,50 A18.5,24 0 0 1 72.5,50"
cups = [rrect(31, 43, 9, 16, 3.8), rrect(68, 43, 9, 16, 3.8)]

# --- colour foreground: drawn back to front. fills (path, colour), strokes (path, colour, width, alpha)
layers = [('s', band, INK, 4.0, 1.0), ('f', body, WHITE), ('f', glass, INK), ('f', stripe, ACCENT)] + \
    [('f', l, LAMP) for l in lamps] + [('f', c, INK) for c in cups] + [('s', rail, INK, 2.4, 0.3)]
fills = [l[1:] for l in layers if l[0] == 'f']
strokes = [l[1:] for l in layers if l[0] == 's']
waves = []

def vector(fills, strokes, size=108, viewport=108):
    x = [f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="{size}dp" android:height="{size}dp"\n    android:viewportWidth="{viewport}" android:viewportHeight="{viewport}">']
    for d, c, w, a in strokes:
        x.append(f'    <path android:strokeColor="{c}" android:strokeWidth="{w}" android:strokeAlpha="{a}" android:strokeLineCap="round" android:pathData="{d}" />')
    for d, c, *rest in fills:
        ft = ' android:fillType="evenOdd"' if rest else ''
        x.append(f'    <path android:fillColor="{c}"{ft} android:pathData="{d}" />')
    x.append('</vector>\n')
    return '\n'.join(x)

def svg_inner(fills, strokes):
    s = []
    for d, c, w, a in strokes:
        s.append(f'<path d="{d}" fill="none" stroke="{c}" stroke-width="{w}" stroke-opacity="{a}" stroke-linecap="round"/>')
    for d, c, *rest in fills:
        s.append(f'<path d="{d}" fill="{c}"{" fill-rule=\"evenodd\"" if rest else ""}/>')
    return ''.join(s)

open(f'{res_dir}/drawable/ic_launcher_foreground.xml', 'w').write(vector(fills, strokes))

# --- monochrome (themed icons): one colour, windows and lamps cut out of the body
mono_body = body + ' ' + glass + ' ' + ' '.join(lamps) + ' M39,62 H69 V64 H39 Z'
mono_fills = [(mono_body, WHITE, 'evenOdd')] + [(c, WHITE) for c in cups]
mono_strokes = [(band, WHITE, 4.0, 1.0), (rail, WHITE, 2.4, 1.0)]
open(f'{res_dir}/drawable/ic_launcher_monochrome.xml', 'w').write(vector(mono_fills, mono_strokes))

# --- status-bar glyph, 24dp: just the train front
def g(v): return round(v, 2)
sb_body = rrect(6, 2.5, 12, 17, 3.2) + ' ' + rrect(8, 5, 8, 5.5, 1.4) + ' ' + circle(9, 15.8, 1.1) + ' ' + circle(15, 15.8, 1.1)
open(f'{res_dir}/drawable/ic_stat_metro.xml', 'w').write(
    vector([(sb_body, WHITE, 'evenOdd')], [("M7.5,22 H16.5", WHITE, 1.4, 1.0)], size=24, viewport=24))

# --- preview sheet
masks = {'circle': '<circle cx="54" cy="54" r="36"/>', 'squircle': '<rect x="18" y="18" width="72" height="72" rx="20"/>', 'square': '<rect x="18" y="18" width="72" height="72" rx="8"/>'}
cells = []
for i, (name, m) in enumerate(masks.items()):
    cells.append(f'<g transform="translate({20 + i*190},20) scale(2.2) translate(-18,-18)"><clipPath id="c{i}">{m}</clipPath><g clip-path="url(#c{i})"><rect width="108" height="108" fill="{BG}"/>{svg_inner(fills, strokes)}</g></g>')
# small sizes as on a home screen
for i, px in enumerate((48, 36)):
    sc = px / 72
    cells.append(f'<g transform="translate({600 + i*70},{60}) scale({sc}) translate(-18,-18)"><clipPath id="s{i}"><circle cx="54" cy="54" r="36"/></clipPath><g clip-path="url(#s{i})"><rect width="108" height="108" fill="{BG}"/>{svg_inner(fills, strokes)}</g></g>')
# themed (monochrome) on a dark tile and the status-bar glyph
cells.append(f'<g transform="translate(600,130) scale(1.0) translate(-18,-18)"><clipPath id="m"><circle cx="54" cy="54" r="36"/></clipPath><g clip-path="url(#m)"><rect width="108" height="108" fill="#2B3A42"/>{svg_inner([(mono_body, "#CFE8F2", "evenOdd")] + [(c, "#CFE8F2") for c in cups], [(band, "#CFE8F2", 4.0, 1.0), (rail, "#CFE8F2", 2.4, 1.0)])}</g></g>')
cells.append(f'<g transform="translate(700,150)"><rect width="40" height="40" rx="8" fill="#1C1A1A"/><g transform="translate(8,8)">{svg_inner([(sb_body, "#FFFFFF", "evenOdd")], [("M7.5,22 H16.5", "#FFFFFF", 1.4, 1.0)])}</g></g>')
open(f'{out_dir}/preview.svg', 'w').write(f'<svg xmlns="http://www.w3.org/2000/svg" width="780" height="220" viewBox="0 0 780 220"><rect width="780" height="220" fill="#EDEDED"/>{"".join(cells)}</svg>')
