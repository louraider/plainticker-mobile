# PlainTicker Mobile design canvas, direction A "Instrument" (chosen 2026-09-11).
# Cold near-black canvas, one blue accent, sharp corners, Outfit + JetBrains Mono, mono numerals,
# a tracking gauge as the signature element, blueprint grids for facts, one live signature per screen.
# Rules applied from taste-skill v2 / redesign-skill: no cream palette, no section-number eyebrows,
# no em dashes, one accent, shape lock (radius 0), motion only for live state, labels above inputs,
# max one middle dot per line, no decorative dots, no filled progress tracks.
# Run: python design/canvas/instrument.py -> writes eight *.dc.html artboards + canvas.json next to this file.
import io, json, os

OUT = os.path.dirname(os.path.abspath(__file__))
# Frame is the real Seeker viewport, measured on the device 2026-09-12:
# 1200x2670 physical at 480dpi -> sw400dp w400dp h890dp. It was 412x915 (the generic
# medium-phone frame) until then, which drew every screen 12dp wider than the phone.
W, H = 400, 890

# ----- tokens -----
BG, ELEV, INK, INK2, MUTED = "#0B0F14", "#121820", "#E8ECF1", "#B4BCC8", "#7F8A99"
LINE, LINE_STRONG, ACCENT, CAUTION = "rgba(232,236,241,0.10)", "rgba(232,236,241,0.22)", "#5AA9E6", "#D9A441"
UI = "'Outfit', system-ui, sans-serif"
MONO = "'JetBrains Mono', ui-monospace, Consolas, monospace"
FONTS = "https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600&amp;family=JetBrains+Mono:wght@400;500&amp;display=swap"

# ----- sample data (illustrative, consistent across screens) -----
D = dict(
    ticker="TSLAx", company="Tesla, Inc.", price="$366.17", ref="$365.84", premium="+0.09%",
    slot="445,912,118", ago="2 s ago",
    por="100.7%", por_sub="26,101 shares held for 25,924 tokens",
    receive="0.01364", paid="5.00", balance="10.00", sig="4xQm…9tHe", rslot="445,912,340", secs="3.1 s",
    wallet="3kF9…Qm2v",
)

def t(size, weight=400, color=INK, extra=""):
    return f"font-family: {UI}; font-size: {size}px; font-weight: {weight}; color: {color}; {extra}"

def m(size, weight=400, color=INK, extra=""):
    return f"font-family: {MONO}; font-size: {size}px; font-weight: {weight}; color: {color}; font-variant-numeric: tabular-nums; {extra}"

def frame(body, height=H, clip=True):
    hstyle = f"height: {height}px; overflow: hidden;" if clip else f"min-height: {height}px;"
    return f"""<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
  <link rel="stylesheet" href="{FONTS}">
  <style>
    body {{ margin: 0; background: {BG}; -webkit-font-smoothing: antialiased; text-rendering: optimizeLegibility; }}
    a {{ color: {ACCENT}; }} a:hover {{ color: {INK}; }}
    @keyframes breathe {{ 0%, 100% {{ opacity: 1; }} 50% {{ opacity: .45; }} }}
    @media (prefers-reduced-motion: reduce) {{ * {{ animation: none !important; }} }}
  </style>
</helmet>
<div style="width: {W}px; {hstyle} background: {BG}; position: relative; display: flex; flex-direction: column; box-sizing: border-box;">
{body}
</div>
</x-dc>
</body>
</html>
"""

# ----- components -----
def header(right=""):
    r = right or "<span></span>"
    return f"""<div style="height: 24px; flex: none;"></div>
<div style="display: flex; flex-direction: row; align-items: center; justify-content: space-between; height: 56px; padding: 0 20px; flex: none;">
  <div style="{t(15, 600, INK, 'letter-spacing: -0.01em;')}">PlainTicker</div>
  {r}
</div>"""

def text_action(label, color=ACCENT, pad="14px 0 14px 16px"):
    return f'<div style="{t(14, 600, color)} padding: {pad};">{label}</div>'

def tabs(active):
    items = []
    for name in ("List", "Portfolio", "Watchlist"):
        on = name == active
        items.append(f'<div style="{t(14, 600 if on else 500, INK if on else MUTED)} padding: 12px 0 14px 0; border-bottom: 2px solid {ACCENT if on else "transparent"};">{name}</div>')
    return f"""<div style="display: flex; flex-direction: row; gap: 24px; padding: 0 20px; border-bottom: 1px solid {LINE}; flex: none;">{''.join(items)}</div>"""

def heading(text, top=32, right=""):
    r = f'<div style="{m(13, 500, MUTED, "line-height: 18px;")}">{right}</div>' if right else ""
    return f"""<div style="display: flex; flex-direction: row; align-items: baseline; justify-content: space-between; gap: 12px; padding: {top}px 20px 14px 20px; flex: none;">
  <div style="{t(20, 600, INK, 'line-height: 26px; letter-spacing: -0.01em;')}">{text}</div>
  {r}
</div>"""

def live(label, meta_text, pulse=True):
    anim = " animation: breathe 2.4s ease-in-out infinite;" if pulse else ""
    return f"""<div style="display: flex; flex-direction: row; align-items: stretch; gap: 14px; padding: 0 20px; flex: none;">
  <div style="width: 2px; background: {ACCENT};{anim} flex: none;"></div>
  <div style="display: flex; flex-direction: column; gap: 2px;">
    <div style="{t(14, 600, ACCENT, 'line-height: 20px;')}">{label}</div>
    <div style="{m(12, 400, MUTED, 'line-height: 18px;')}">{meta_text}</div>
  </div>
</div>"""

def cell(label, value, sub="", span=1, value_size=24, tone=None, min_h=96, sub_mono=False):
    if sub:
        style = m(12, 400, INK2, "line-height: 18px;") if sub_mono else t(13, 400, INK2, "line-height: 18px;")
        subhtml = f'<div style="{style}">{sub}</div>'
    else:
        subhtml = ""
    return f"""<div style="grid-column: span {span}; background: {BG}; padding: 16px 16px 18px 16px; display: flex; flex-direction: column; gap: 8px; min-height: {min_h}px; box-sizing: border-box;">
  <div style="{t(13, 500, MUTED, 'line-height: 18px;')}">{label}</div>
  <div style="{m(value_size, 500, tone or INK, 'line-height: 1.1; letter-spacing: -0.01em;')}">{value}</div>
  {subhtml}
</div>"""

def grid(cells):
    return f"""<div style="padding: 0 20px; flex: none;">
  <div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1px; background: {LINE}; border: 1px solid {LINE};">
    {''.join(cells)}
  </div>
</div>"""

def track(label, value, state, pos_pct):
    return f"""<div style="display: flex; flex-direction: column; gap: 10px; padding: 14px 20px 6px 20px; flex: none;">
  <div style="display: flex; flex-direction: row; align-items: baseline; justify-content: space-between; gap: 12px;">
    <div style="{t(15, 500, INK2, 'line-height: 20px;')}">{label}</div>
    <div style="display: flex; flex-direction: row; align-items: baseline; gap: 10px;">
      <div style="{m(20, 500, INK, 'line-height: 24px;')}">{value}</div>
      <div style="{t(13, 400, MUTED, 'line-height: 18px;')}">{state}</div>
    </div>
  </div>
  <div style="position: relative; height: 12px;">
    <div style="position: absolute; left: 0; right: 0; top: 5px; height: 1px; background: {LINE_STRONG};"></div>
    <div style="position: absolute; left: {pos_pct}%; top: 0; width: 2px; height: 12px; background: {INK};"></div>
  </div>
</div>"""

def signal(name, ok=True):
    return f"""<div style="display: flex; flex-direction: row; align-items: center; justify-content: space-between; gap: 12px; min-height: 44px; padding: 10px 20px; box-sizing: border-box;">
  <div style="{t(15, 400, INK2 if ok else MUTED, 'line-height: 20px;')}">{name}</div>
  <div style="{m(14, 500, INK if ok else MUTED, 'line-height: 20px;')}">{'yes' if ok else 'no'}</div>
</div>"""

def button(label, kind="primary"):
    if kind == "primary":
        style = f"background: {ACCENT}; color: {BG}; border: 1px solid {ACCENT};"; color = BG
    elif kind == "secondary":
        style = f"background: transparent; color: {INK}; border: 1px solid {LINE_STRONG};"; color = INK
    else:  # disabled
        style = f"background: transparent; color: {MUTED}; border: 1px solid {LINE};"; color = MUTED
    return f'<div style="display: flex; align-items: center; justify-content: center; height: 56px; {style} {t(16, 600, color)} box-sizing: border-box;">{label}</div>'

def field(label, value, unit="", action="", placeholder=False, value_style=None):
    vs = value_style or m(36, 500, MUTED if placeholder else INK, "line-height: 40px; letter-spacing: -0.02em;")
    u = f'<div style="{m(14, 400, MUTED)}">{unit}</div>' if unit else ""
    a = f'<div style="{t(14, 600, ACCENT)} padding: 8px 0 8px 12px;">{action}</div>' if action else ""
    return f"""<div style="display: flex; flex-direction: column; gap: 8px; padding: 0 20px; flex: none;">
  <div style="{t(13, 500, MUTED, 'line-height: 18px;')}">{label}</div>
  <div style="display: flex; flex-direction: row; align-items: center; justify-content: space-between; gap: 12px; border-bottom: 1px solid {LINE_STRONG}; padding-bottom: 10px;">
    <div style="display: flex; flex-direction: row; align-items: baseline; gap: 10px; min-width: 0;">
      <div style="{vs}">{value}</div>
      {u}
    </div>
    {a}
  </div>
</div>"""

def list_row(ticker, company, right_main="", right_sub="", sub="", muted=False, trailing="", last=False):
    tc = MUTED if muted else INK
    cc = MUTED if muted else INK2
    rm = f'<div style="{m(18 if not muted else 15, 500 if not muted else 400, tc, "line-height: 22px; white-space: nowrap;")}">{right_main}</div>' if right_main else ""
    rs = f'<div style="{t(13, 400, MUTED, "line-height: 18px;")}">{right_sub}</div>' if right_sub else ""
    s = f'<div style="{m(12, 400, MUTED, "line-height: 18px;")}">{sub}</div>' if sub else ""
    tr = f'<div style="{t(14, 600, ACCENT)} padding: 10px 0 10px 16px; flex: none;">{trailing}</div>' if trailing else ""
    border = "" if last else f"border-bottom: 1px solid {LINE};"
    return f"""<div style="display: flex; flex-direction: row; align-items: center; justify-content: space-between; gap: 12px; min-height: 64px; padding: 12px 20px; box-sizing: border-box; {border}">
  <div style="display: flex; flex-direction: column; gap: 3px; min-width: 0;">
    <div style="display: flex; flex-direction: row; align-items: baseline; gap: 10px;">
      <div style="{m(18, 500, tc, 'line-height: 22px;')}">{ticker}</div>
      <div style="{t(13, 400, cc, 'line-height: 18px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;')}">{company}</div>
    </div>
    {s}
  </div>
  <div style="display: flex; flex-direction: row; align-items: center; flex: none;">
    <div style="display: flex; flex-direction: row; align-items: baseline; gap: 8px;">
      {rm}
      {rs}
    </div>
    {tr}
  </div>
</div>"""

def strip(text):
    return f'<div style="padding: 12px 20px; border-bottom: 1px solid {LINE}; {t(13, 500, INK2, "line-height: 18px;")} flex: none;">{text}</div>'

def gauge(left_text, premium_value, pos_pct, off_scale=None):
    # The scale is +-2.5%, the spread the tracked set actually produced (DESIGN.md 1.1), so
    # 0.09% is 51.8% along and -0.95% is 31%. The track stops 8px short at each end, which is a
    # 6px gap plus the tick's own 2px, and carries an end stop at each end: a premium past the
    # scale (off_scale="left" or "right") stands its tick in that gutter, clear of the track,
    # instead of resting on the end where it would read as the end of the scale.
    if off_scale == "left":
        tick = f'<div style="position: absolute; left: 0; top: 0; width: 2px; height: 14px; background: {ACCENT};"></div>'
    elif off_scale == "right":
        tick = f'<div style="position: absolute; right: 0; top: 0; width: 2px; height: 14px; background: {ACCENT};"></div>'
    else:
        tick = f'<div style="position: absolute; left: calc(8px + (100% - 18px) * {pos_pct / 100:.4f}); top: 0; width: 2px; height: 14px; background: {ACCENT};"></div>'
    return f"""<div style="display: flex; flex-direction: column; gap: 10px; padding: 18px 20px 0 20px; flex: none;">
  <div style="position: relative; height: 14px;">
    <div style="position: absolute; left: 8px; right: 8px; top: 6px; height: 1px; background: {LINE_STRONG};"></div>
    <div style="position: absolute; left: 8px; top: 4px; width: 1px; height: 6px; background: {LINE_STRONG};"></div>
    <div style="position: absolute; right: 8px; top: 4px; width: 1px; height: 6px; background: {LINE_STRONG};"></div>
    <div style="position: absolute; left: calc(50% - 0.5px); top: 2px; width: 1px; height: 10px; background: {MUTED};"></div>
    {tick}
  </div>
  <div style="display: flex; flex-direction: row; justify-content: space-between; gap: 12px;">
    <div style="{t(13, 400, INK2, 'line-height: 18px;')}">{left_text}</div>
    <div style="{m(13, 500, ACCENT, 'line-height: 18px;')}">{premium_value}</div>
  </div>
</div>"""

# ----- Detail -----
def detail_top():
    return f"""{header(text_action('Watch'))}
<div style="display: flex; flex-direction: column; padding: 12px 20px 0 20px; flex: none;">
  <div style="{m(64, 500, INK, 'line-height: 64px; letter-spacing: -0.035em;')}">{D['ticker']}</div>
  <div style="{t(16, 400, INK2, 'line-height: 22px; margin-top: 6px;')}">{D['company']}</div>
</div>
<div style="display: flex; flex-direction: row; align-items: flex-end; justify-content: space-between; gap: 16px; padding: 28px 20px 0 20px; flex: none;">
  <div style="display: flex; flex-direction: column; gap: 4px;">
    <div style="{t(13, 500, MUTED, 'line-height: 18px;')}">Token price</div>
    <div style="{m(40, 500, INK, 'line-height: 44px; letter-spacing: -0.03em;')}">{D['price']}</div>
  </div>
  <div style="display: flex; flex-direction: column; gap: 4px; align-items: flex-end; padding-bottom: 6px;">
    <div style="{t(13, 500, MUTED, 'line-height: 18px;')}">NYSE close</div>
    <div style="{m(20, 400, INK2, 'line-height: 24px;')}">{D['ref']}</div>
  </div>
</div>
{gauge('Token vs NYSE close, scale 2.5%', D['premium'], 51.8)}
<div style="height: 28px; flex: none;"></div>
{live('Live from the mint', f"slot {D['slot']} · {D['ago']}")}
{heading('Backing and controls', top=28)}
{grid([
    cell('Proof of reserves', D['por'], D['por_sub'], span=2, value_size=32, sub_mono=True),
    cell('Permanent delegate', 'Yes', 'Issuer can move tokens', tone=CAUTION),
    cell('Transfers pausable', 'Yes', 'Not paused now, issuer can pause', tone=CAUTION),
    cell('Split multiplier', '1.00', 'No pending split'),
    cell('Transfer hook', 'None', 'No transfer hook program'),
])}
"""

# Detail below the liquidity floor. APPx exactly as the signed v0.2.0 read it on the Seeker on
# 2026-09-13: pool $34, token $611.56, NYSE close $323.00, which are +89.34% apart. The canvas
# never drew this state, which is why the screen shipped handing a reader both operands at 40sp
# and 20sp with the caveat at 13sp between them. Here the sentence is read first, both figures
# are set at 20 mono, and the token's is called what it is.
def detail_below_floor():
    return f"""{header(text_action('Watch'))}
{strip('The NYSE is closed, the reference is the last close')}
<div style="display: flex; flex-direction: column; padding: 12px 20px 0 20px; flex: none;">
  <div style="{m(64, 500, INK, 'line-height: 64px; letter-spacing: -0.035em;')}">APPx</div>
  <div style="{t(16, 400, INK2, 'line-height: 22px; margin-top: 6px;')}">AppLovin Corporation</div>
</div>
<div style="padding: 28px 20px 0 20px; flex: none; {t(15, 400, INK, 'line-height: 23px;')}">Pool holds $34, too thin to track the NYSE close</div>
<div style="display: flex; flex-direction: row; align-items: flex-end; justify-content: space-between; gap: 16px; padding: 14px 20px 0 20px; flex: none;">
  <div style="display: flex; flex-direction: column; gap: 4px;">
    <div style="{t(13, 500, MUTED, 'line-height: 18px;')}">Pool quote</div>
    <div style="{m(20, 400, INK, 'line-height: 24px;')}">$611.56</div>
  </div>
  <div style="display: flex; flex-direction: column; gap: 4px; align-items: flex-end;">
    <div style="{t(13, 500, MUTED, 'line-height: 18px;')}">NYSE close</div>
    <div style="{m(20, 400, INK2, 'line-height: 24px;')}">$323.00</div>
  </div>
</div>
<div style="height: 28px; flex: none;"></div>
{live('Live from the mint', f"slot 446,664,185 · 3 s ago")}
{heading('Backing and controls', top=28)}
{grid([
    cell('Proof of reserves', '102.9%', '2,134 shares held by Alpaca for 2,074.5 tokens', span=2, value_size=32, sub_mono=True),
    cell('Permanent delegate', 'Yes', 'Issuer can move tokens', tone=CAUTION),
    cell('Transfers pausable', 'Yes', 'Not paused now, issuer can pause', tone=CAUTION),
])}
"""

# Every gauge state the live catalogue can produce, on one artboard. The canvas drew +0.09% and
# nothing else, so a tick pinned against the end of the track was never looked at before it
# shipped; these are the premiums the device and the 2026-09-12 measurement actually read.
def gauge_states():
    rows = [
        ('TSLAx, +0.09% on $1.3M', 'Token vs NYSE close, scale 2.5%', '+0.09%', 51.8, None),
        ('NVDAx, -0.95% on $1.9M, the tick that pinned at 0.5%', 'Token vs NYSE close, scale 2.5%', '-0.95%', 31.0, None),
        ('NFLXx, -2.34% on $12.5k, the widest the tracked set went', 'Token vs NYSE close, scale 2.5%', '-2.34%', 3.2, None),
        ('Past the scale, low side', 'Token vs NYSE close, past the 2.5% scale', '-4.10%', 0, 'left'),
        ('Past the scale, high side', 'Token vs NYSE close, past the 2.5% scale', '+6.80%', 100, 'right'),
    ]
    body = "".join(
        f'<div style="padding: 22px 20px 0 20px; flex: none; {t(13, 500, MUTED, "line-height: 18px;")}">{title}</div>'
        + gauge(caption, value, pos, off)
        for title, caption, value, pos, off in rows
    )
    return f"""{header()}
<div style="padding: 20px 20px 0 20px; flex: none; {t(20, 600, INK, 'line-height: 26px; letter-spacing: -0.01em;')}">Gauge, every reading the market gave</div>
{body}
<div style="height: 28px; flex: none;"></div>
"""

def detail_rest():
    return f"""{heading('Against the sector', right='composite 0.71')}
{track('Quality', '8/9', 'strong', 89)}
{track('Valuation', '51', 'fair', 51)}
{track('Momentum', '0.79', 'high', 79)}
<div style="height: 14px; flex: none;"></div>
{grid([
    cell('Sector rank', '14 of 62', 'Technology hardware, by composite', span=2, value_size=22, min_h=88),
    cell('Return on capital', '9.8%', 'sector median 6.1%', value_size=22, min_h=88),
    cell('Gross margin', '17.9%', 'sector median 21.4%', value_size=22, min_h=88),
    cell('Price to earnings', '92.4x', 'sector median 27.0x', value_size=22, min_h=88),
    cell('EV to sales', '11.2x', 'sector median 2.4x', value_size=22, min_h=88),
    cell('52-week position', '0.83', 'of the low to high range', value_size=22, min_h=88),
    cell('Six months vs sector', '+14.2 pt', 'relative price change', value_size=22, min_h=88),
])}
{heading('F-Score')}
<div style="display: flex; flex-direction: row; align-items: baseline; gap: 12px; padding: 0 20px 6px 20px; flex: none;">
  <div style="{m(56, 500, INK, 'line-height: 60px; letter-spacing: -0.03em;')}">8</div>
  <div style="{t(16, 400, MUTED, 'line-height: 22px;')}">of 9 signals</div>
</div>
{signal('Return on assets positive')}
{signal('Operating cash flow positive')}
{signal('Return on assets improving')}
{signal('Cash flow exceeds earnings')}
{signal('Leverage falling')}
{signal('Liquidity improving', ok=False)}
{signal('No new shares issued')}
{signal('Gross margin improving')}
{signal('Asset turnover improving')}
{heading('Method')}
<div style="display: flex; flex-direction: column; gap: 12px; padding: 0 20px; flex: none;">
  <div style="{t(15, 400, INK2, 'line-height: 23px;')}">Rule-based classification of fundamentals against the sector. Not a price forecast and not investment advice.</div>
  <div style="{t(13, 400, MUTED, 'line-height: 18px;')}">Filings from SEC EDGAR XBRL. Prices from Jupiter. Reference from the NYSE close.</div>
</div>
<div style="display: flex; flex-direction: column; gap: 10px; padding: 32px 20px 48px 20px; flex: none;">
  {button('Swap USDC to TSLAx')}
  <div style="{m(12, 400, MUTED, 'line-height: 18px;')}">est. all-in cost 0.09% · liquidity $1.3M</div>
</div>
"""

def screen_main():
    tail = f"""{heading('Against the sector', right='composite 0.71')}
{track('Quality', '8/9', 'strong', 89)}
{track('Valuation', '51', 'fair', 51)}
"""
    return frame(detail_top() + tail)

def screen_detail_full():
    return frame(detail_top() + detail_rest(), height=2640, clip=False)

# ----- List -----
def list_rows():
    return "".join([
        list_row('TSLAx', 'Tesla, Inc.', '0.71', 'strong', '+0.09% vs NYSE close · 2 d old'),
        list_row('NVDAx', 'NVIDIA Corp.', '0.68', 'strong', '-0.04% vs NYSE close · 1 d old'),
        list_row('AAPLx', 'Apple Inc.', '0.61', 'fair', '+0.01% vs NYSE close · 2 d old'),
        list_row('MSFTx', 'Microsoft Corp.', '0.58', 'fair', '+0.03% vs NYSE close · 6 d old'),
        list_row('AMZNx', 'Amazon.com, Inc.', '0.55', 'fair', '-0.02% vs NYSE close · 2 d old'),
        list_row('COINx', 'Coinbase Global', '0.47', 'weak', '+0.08% vs NYSE close · 3 d old', last=True),
    ])

def screen_list():
    body = f"""{header()}
{tabs('List')}
{strip('Today: 3 watched, next report TSLAx on Oct 22')}
<div style="height: 22px; flex: none;"></div>
{field('Search', 'Ticker or company', placeholder=True, value_style=t(16, 400, MUTED, 'line-height: 24px;'))}
{heading('Analyzed', top=30)}
{list_rows()}
{heading('Without analysis', top=28)}
{list_row('TSMx', 'Taiwan Semiconductor', '$264.10', '', '+0.05% vs NYSE close', muted=True)}
{list_row('ASMLx', 'ASML Holding', '$812.40', '', '-0.01% vs NYSE close', muted=True, last=True)}
"""
    return frame(body)

# ----- Onboarding -----
def screen_onboarding():
    behind = f"""<div style="position: absolute; inset: 0; opacity: 0.25; pointer-events: none; display: flex; flex-direction: column;">
{header()}
{tabs('List')}
{strip('Today: 3 watched, next report TSLAx on Oct 22')}
{heading('Analyzed', top=30)}
{list_rows()}
</div>"""
    panel = f"""<div style="position: absolute; left: 0; right: 0; bottom: 0; background: {ELEV}; border-top: 1px solid {LINE_STRONG}; display: flex; flex-direction: column; gap: 20px; padding: 28px 20px 40px 20px; box-sizing: border-box;">
  <div style="{t(15, 600, INK, 'letter-spacing: -0.01em;')}">PlainTicker</div>
  <div style="{t(26, 600, INK, 'line-height: 32px; letter-spacing: -0.02em;')}">Tokenized stocks, read before you swap.</div>
  <div style="display: flex; flex-direction: column; gap: 10px; {t(15, 400, INK2, 'line-height: 23px;')}">
    <div>Every xStock page starts with what the token itself says: reserves, issuer controls and the split multiplier, read live from the Solana mint.</div>
    <div>Then the company against its sector: quality, valuation, momentum and F-Score, from SEC filings.</div>
    <div style="color: {INK};">Not a price forecast. Not investment advice.</div>
  </div>
  <div style="display: flex; flex-direction: row; align-items: flex-start; gap: 14px; min-height: 48px;">
    <div style="width: 20px; height: 20px; border: 1px solid {LINE_STRONG}; flex: none; margin-top: 1px; box-sizing: border-box;"></div>
    <div style="{t(14, 400, INK2, 'line-height: 20px;')}">I am not a US person, and I understand xStocks are tokenized tracker instruments issued by a third party, not shares.</div>
  </div>
  {button('Read the list', kind='disabled')}
</div>"""
    return frame(behind + panel)

# ----- Swap sheet and receipt -----
def sheet(inner):
    behind = f'<div style="position: absolute; inset: 0; opacity: 0.18; pointer-events: none; display: flex; flex-direction: column;">{detail_top()}</div>'
    return frame(behind + f"""<div style="position: absolute; left: 0; right: 0; bottom: 0; background: {ELEV}; border-top: 1px solid {LINE_STRONG}; display: flex; flex-direction: column; padding: 0 0 40px 0; box-sizing: border-box;">
  <div style="width: 28px; height: 2px; background: {LINE_STRONG}; align-self: center; margin: 12px 0 8px 0;"></div>
  {inner}
</div>""")

def sheet_cell(label, value, sub="", span=1, value_size=22, sub_mono=False):
    if sub:
        style = m(12, 400, INK2, "line-height: 18px;") if sub_mono else t(13, 400, INK2, "line-height: 18px;")
        subhtml = f'<div style="{style}">{sub}</div>'
    else:
        subhtml = ""
    return f"""<div style="grid-column: span {span}; background: {ELEV}; padding: 14px 16px 16px 16px; display: flex; flex-direction: column; gap: 6px; box-sizing: border-box;">
  <div style="{t(13, 500, MUTED, 'line-height: 18px;')}">{label}</div>
  <div style="{m(value_size, 500, INK, 'line-height: 1.1; letter-spacing: -0.01em;')}">{value}</div>
  {subhtml}
</div>"""

def sheet_grid(cells):
    return f"""<div style="padding: 0 20px; flex: none;">
  <div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1px; background: {LINE}; border: 1px solid {LINE};">
    {''.join(cells)}
  </div>
</div>"""

def screen_swap():
    inner = f"""<div style="display: flex; flex-direction: row; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 20px 8px 20px;">
  <div style="{m(22, 500, INK, 'line-height: 28px;')}">USDC to TSLAx</div>
  {text_action('TSLAx to USDC', pad='10px 0 10px 12px')}
</div>
<div style="height: 14px;"></div>
{field('Amount, USDC', D['paid'], action='Max')}
<div style="padding: 10px 20px 0 20px; {m(12, 400, MUTED, 'line-height: 18px;')}">Balance {D['balance']} USDC</div>
<div style="height: 22px;"></div>
{sheet_grid([
    sheet_cell('You receive', f"{D['receive']} TSLAx", f"1 TSLAx = {D['price']}, reference {D['ref']}", span=2, value_size=28, sub_mono=True),
    sheet_cell('All-in cost', '0.09%', 'Route Metis'),
    sheet_cell('Liquidity', '$1.3M', 'Quote refreshes at tap'),
])}
<div style="display: flex; flex-direction: column; gap: 10px; padding: 24px 20px 0 20px;">
  {button('Swap USDC to TSLAx')}
  <div style="{t(13, 400, MUTED, 'line-height: 18px;')}">Signs in Seed Vault Wallet. Not investment advice.</div>
</div>"""
    return sheet(inner)

def screen_receipt():
    inner = f"""<div style="padding: 8px 0 0 0;">{live('Landed', f"confirmed in {D['secs']}", pulse=False)}</div>
<div style="display: flex; flex-direction: column; gap: 4px; padding: 22px 20px 0 20px;">
  <div style="{t(13, 500, MUTED, 'line-height: 18px;')}">Received</div>
  <div style="{m(40, 500, INK, 'line-height: 44px; letter-spacing: -0.03em;')}">{D['receive']} TSLAx</div>
</div>
<div style="height: 22px;"></div>
{sheet_grid([
    sheet_cell('Paid', f"{D['paid']} USDC"),
    sheet_cell('All-in cost paid', '0.09%', 'quote 0.09%, fill +0.00%', sub_mono=True),
    sheet_cell('Signature', D['sig'], 'Tap to copy', span=2, value_size=18),
    sheet_cell('Slot', D['rslot'], '', span=2, value_size=18),
])}
<div style="display: flex; flex-direction: column; gap: 10px; padding: 24px 20px 0 20px;">
  {button('View in Portfolio', kind='secondary')}
</div>"""
    return sheet(inner)

# ----- Portfolio -----
def screen_portfolio():
    wallet = f'<div style="{m(12, 400, MUTED)}">{D["wallet"]}</div>'
    body = f"""{header(wallet)}
{tabs('Portfolio')}
{heading('Holdings')}
<div style="display: flex; flex-direction: column; gap: 4px; padding: 0 20px 6px 20px; flex: none;">
  <div style="{m(40, 500, INK, 'line-height: 44px; letter-spacing: -0.03em;')}">$1,289.01</div>
  <div style="{t(13, 400, MUTED, 'line-height: 18px;')}">3 xStocks, priced by Jupiter</div>
</div>
<div style="height: 18px; flex: none;"></div>
{list_row('TSLAx', 'Tesla, Inc.', '$737.33', '', '2.01364 TSLAx · +0.09% vs NYSE close')}
{list_row('NVDAx', 'NVIDIA Corp.', '$366.05', '', '2.1 NVDAx · -0.04% vs NYSE close')}
{list_row('AAPLx', 'Apple Inc.', '$185.63', '', '0.8 AAPLx · +0.01% vs NYSE close', last=True)}
<div style="padding: 16px 20px 0 20px; {t(13, 400, MUTED, 'line-height: 18px;')} flex: none;">Cost basis is not read from the chain.</div>
{heading('Recent swaps', top=30)}
{list_row('5.00 USDC', 'to 0.01364 TSLAx', '', '', f"Today 14:57 · {D['sig']}")}
{list_row('20.00 USDC', 'to 0.11464 NVDAx', '', '', 'Sep 4 · 9pLd…3kRw', last=True)}
"""
    return frame(body)

# ----- Watchlist -----
def screen_watchlist():
    body = f"""{header()}
{tabs('Watchlist')}
{heading('Watched')}
{list_row('TSLAx', 'Tesla, Inc.', sub='Reports Oct 22 · +0.09% vs NYSE close', trailing='Unwatch')}
{list_row('AAPLx', 'Apple Inc.', sub='Reports Oct 30 · +0.01% vs NYSE close', trailing='Unwatch')}
{list_row('NVDAx', 'NVIDIA Corp.', sub='Reports Nov 19 · -0.04% vs NYSE close', trailing='Unwatch', last=True)}
{heading('Daily digest', top=30)}
<div style="padding: 0 20px; flex: none;">
  <div style="background: {ELEV}; border: 1px solid {LINE}; padding: 16px; display: flex; flex-direction: column; gap: 8px;">
    <div style="{m(12, 400, MUTED, 'line-height: 18px;')}">Today 08:00</div>
    <div style="{t(15, 400, INK, 'line-height: 22px;')}">3 watched. NVDAx moved from -0.04% to -0.61% against the NYSE close. TSLAx reports in 41 days.</div>
  </div>
</div>
<div style="display: flex; flex-direction: column; gap: 4px; padding: 16px 20px 0 20px; flex: none;">
  <div style="{t(13, 400, INK2, 'line-height: 18px;')}">Notifications on, delivered at 08:00.</div>
  <div style="{t(13, 400, MUTED, 'line-height: 18px;')}">Checked 3 h ago.</div>
</div>
"""
    return frame(body)

SCREENS = {
    "Onboarding.dc.html": screen_onboarding(),
    "List.dc.html": screen_list(),
    "Main.dc.html": screen_main(),
    "SwapSheet.dc.html": screen_swap(),
    "Receipt.dc.html": screen_receipt(),
    "DetailFull.dc.html": screen_detail_full(),
    "DetailBelowFloor.dc.html": frame(detail_below_floor()),
    "GaugeStates.dc.html": frame(gauge_states(), height=680),
    "Portfolio.dc.html": screen_portfolio(),
    "Watchlist.dc.html": screen_watchlist(),
}
for name, html in SCREENS.items():
    io.open(os.path.join(OUT, name), "w", encoding="utf-8", newline="\n").write(html)

GAP = 96
X = lambda i: i * (W + GAP)
canvas = {
    "artboards": [
        {"file": "Onboarding.dc.html", "title": "1 Onboarding", "x": X(0), "y": 0, "w": W, "h": H},
        {"file": "List.dc.html", "title": "2 List", "x": X(1), "y": 0, "w": W, "h": H},
        {"file": "Main.dc.html", "title": "3 Detail, first viewport", "x": X(2), "y": 0, "w": W, "h": H},
        {"file": "SwapSheet.dc.html", "title": "4 Swap sheet", "x": X(3), "y": 0, "w": W, "h": H},
        {"file": "Receipt.dc.html", "title": "5 Receipt, landed", "x": X(4), "y": 0, "w": W, "h": H},
        {"file": "DetailFull.dc.html", "title": "3b Detail, full scroll", "x": X(0), "y": H + 200, "w": W, "h": 2640},
        {"file": "Portfolio.dc.html", "title": "6 Portfolio", "x": X(1), "y": H + 200, "w": W, "h": H},
        {"file": "Watchlist.dc.html", "title": "7 Watchlist", "x": X(2), "y": H + 200, "w": W, "h": H},
        {"file": "DetailBelowFloor.dc.html", "title": "3c Detail, below the liquidity floor", "x": X(0), "y": H + 3040, "w": W, "h": H},
        {"file": "GaugeStates.dc.html", "title": "3d Gauge, every reading the market gave", "x": X(1), "y": H + 3040, "w": W, "h": 680},
    ],
    "annotations": [
        {"id": "read", "x": X(0), "y": -220, "w": 980,
         "text": "PlainTicker Mobile, direction Instrument. Seeker, 412dp portrait, dark canvas.\nA reading tool for tokenized US stocks: the token's own facts first (reserves, issuer controls, split multiplier, live from the mint), then the company against its sector, then one Swap. Outfit for words, JetBrains Mono for every number. One blue accent for interaction and live state; amber only on issuer-control risk. Sharp corners.\nSignature elements: the tracking gauge (token tick against the NYSE close), the live bar, blueprint grids for facts.\nSample data is illustrative."},
        {"id": "floor", "x": X(2), "y": H + 3040, "w": 420,
         "text": "Below the liquidity floor the premium is withheld, so the two figures it would be computed from may not be staged as a comparison: the pool sentence is read first, both figures are set at 20 mono, and the token's is labelled Pool quote. Drawn from APPx as the Seeker read it on 2026-09-13.\nThe gauge scale is the measured spread of the tracked set, plus or minus 2.5 percent, and a premium past it stands its tick off the end of the track instead of resting on it."},
        {"id": "ia", "x": X(3), "y": H + 200, "w": 420,
         "text": "Screen order kept from the plan: trust first, fundamentals second, one Swap after Method; Watch in the header; List, Portfolio and Watchlist as top tabs.\nOne change for mobile: Quality, Valuation and Momentum share one section, Against the sector, with three marker tracks and a fact grid, instead of three sections with the same layout."},
    ],
    "launch": {"view": "canvas"},
}
io.open(os.path.join(OUT, "canvas.json"), "w", encoding="utf-8", newline="\n").write(json.dumps(canvas, ensure_ascii=False, indent=2) + "\n")
print(f"wrote {len(SCREENS)} artboards + canvas.json -> {OUT}")
