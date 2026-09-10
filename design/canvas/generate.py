# Generates the /design canvas artboards for PlainTicker Mobile from the decided spec
# (docs/plan-2026-09-10.md §13 + DESIGN.md "MOBILE DELTAS"). Static mockups, sample data.
# Run: python design/canvas/generate.py  -> writes *.dc.html + canvas.json next to this file.
import io, json, os

OUT = os.path.dirname(os.path.abspath(__file__))

# ---- tokens (DESIGN.md v3 light + mobile deltas) ----
PAPER, ELEV, INK, INK2, MUTED, RULE, RULE_STRONG = "#F5F2EA", "#EDE8DA", "#18181B", "#3F3F46", "#5F5F66", "#D4D0C5", "#A8A29E"
POS, CAUTION, DANGER = "oklch(0.45 0.15 155)", "oklch(0.48 0.16 70)", "oklch(0.45 0.18 25)"
SANS = "'Geist', system-ui, -apple-system, 'Segoe UI', sans-serif"
MONO = "'Geist Mono', ui-monospace, 'SF Mono', Consolas, monospace"
W, H = 412, 915  # Seeker portrait frame in dp

HELMET = f"""<helmet>
  <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Geist:wght@400;500&amp;family=Geist+Mono:wght@400;500&amp;display=swap">
  <style>
    body {{ margin: 0; background: {PAPER}; color: {INK}; font-family: {SANS}; -webkit-font-smoothing: antialiased; text-rendering: optimizeLegibility; }}
    a {{ color: {INK}; }} a:hover {{ color: {INK2}; }}
    @keyframes pt-pulse {{ 0%, 100% {{ opacity: 1; }} 50% {{ opacity: .35; }} }}
    @media (prefers-reduced-motion: reduce) {{ * {{ animation: none !important; }} }}
  </style>
</helmet>"""

def mono(size, weight=400, color=INK, extra=""):
    return f"font-family: {MONO}; font-size: {size}px; font-weight: {weight}; color: {color}; font-variant-numeric: tabular-nums; {extra}"

def sans(size, weight=400, color=INK, extra=""):
    return f"font-family: {SANS}; font-size: {size}px; font-weight: {weight}; color: {color}; {extra}"

def meta(text, color=MUTED, extra=""):
    return f'<div style="{mono(11, 400, color, "text-transform: uppercase; letter-spacing: 0.08em; line-height: 16px;")} {extra}">{text}</div>'

def dot(color, pulse=False):
    anim = " animation: pt-pulse 2s ease-in-out infinite;" if pulse else ""
    return f'<span style="display: inline-block; width: 6px; height: 6px; border-radius: 3px; background: {color}; flex: none;{anim}"></span>'

def rule():
    return f'<div style="height: 1px; background: {RULE}; flex: none;"></div>'

def frame(inner, height=H, clip=True, tall=False):
    ov = "hidden" if clip else "visible"
    hstyle = f"height: {height}px;" if not tall else f"min-height: {height}px;"
    return f"""<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <script src="./support.js"></script>
</head>
<body>
<x-dc>
{HELMET}
<div style="width: {W}px; {hstyle} overflow: {ov}; background: {PAPER}; position: relative; display: flex; flex-direction: column; box-sizing: border-box;">
{inner}
</div>
</x-dc>
</body>
</html>
"""

def col(inner, extra=""):
    return f'<div style="display: flex; flex-direction: column; {extra}">{inner}</div>'

def row(inner, extra=""):
    return f'<div style="display: flex; flex-direction: row; align-items: center; justify-content: space-between; gap: 12px; {extra}">{inner}</div>'

def header(right=""):
    """56dp header after a 24dp status inset: wordmark left, optional meta right. Scrolls away; never sticky."""
    r = right or "<span></span>"
    return f"""<div style="height: 24px; flex: none;"></div>
<div style="display: flex; flex-direction: row; align-items: center; justify-content: space-between; height: 56px; padding: 0 16px; flex: none;">
  {meta("PLAINTICKER", INK2)}
  {r}
</div>"""

def tabs(active):
    items = []
    for name in ("LIST", "PORTFOLIO", "WATCHLIST"):
        on = name == active
        color = INK if on else MUTED
        border = f"border-bottom: 2px solid {INK};" if on else "border-bottom: 2px solid transparent;"
        items.append(f'<div style="{mono(11, 500 if on else 400, color, "text-transform: uppercase; letter-spacing: 0.08em;")} padding: 14px 0 12px 0; {border}">{name}</div>')
    return f"""<div style="display: flex; flex-direction: row; gap: 24px; padding: 0 16px; flex: none;">{''.join(items)}</div>
{rule()}"""

def section_head(num, label, title=None, top=32):
    t = f'<div style="{sans(22, 500, INK, "line-height: 28px; margin-top: 6px;")}">{title}</div>' if title else ""
    return f"""<div style="display: flex; flex-direction: column; padding: {top}px 16px 12px 16px; flex: none;">
  {meta(f"{num} — {label}")}
  {t}
</div>"""

def metric_row(label, value, sub=None, tone=None, value_size=22, height=56):
    d = dot(tone) if tone else ""
    subhtml = f'<div style="{sans(14, 400, MUTED, "line-height: 20px; margin-top: 2px;")}">{sub}</div>' if sub else ""
    return f"""<div style="display: flex; flex-direction: row; align-items: center; justify-content: space-between; gap: 12px; min-height: {height}px; padding: 12px 16px; box-sizing: border-box;">
  <div style="display: flex; flex-direction: column; min-width: 0;">
    <div style="{sans(16, 400, INK2, "line-height: 22px;")}">{label}</div>
    {subhtml}
  </div>
  <div style="display: flex; flex-direction: row; align-items: center; gap: 8px; flex: none;">
    {d}
    <div style="{mono(value_size, 400, INK, "line-height: 28px; white-space: nowrap;")}">{value}</div>
  </div>
</div>
{rule()}"""

def strata_row(label, value, state, pos_pct):
    return f"""<div style="display: flex; flex-direction: column; gap: 10px; padding: 14px 16px; box-sizing: border-box;">
  <div style="display: flex; flex-direction: row; align-items: baseline; justify-content: space-between; gap: 12px;">
    <div style="{sans(16, 400, INK2, "line-height: 22px;")}">{label}</div>
    <div style="display: flex; flex-direction: row; align-items: baseline; gap: 8px;">
      <div style="{mono(18, 400, INK, "line-height: 22px;")}">{value}</div>
      <div style="{sans(14, 400, MUTED, "line-height: 20px;")}">{state}</div>
    </div>
  </div>
  <div style="position: relative; height: 12px;">
    <div style="position: absolute; left: 0; right: 0; top: 5px; height: 2px; background: {RULE_STRONG};"></div>
    <div style="position: absolute; left: {pos_pct}%; top: 0; width: 2px; height: 12px; background: {INK};"></div>
  </div>
</div>
{rule()}"""

def signal_row(name, ok=True):
    mark = "✓" if ok else "—"
    color = INK if ok else MUTED
    return f"""<div style="display: flex; flex-direction: row; align-items: center; justify-content: space-between; min-height: 44px; padding: 10px 16px; box-sizing: border-box;">
  <div style="{sans(16, 400, INK2 if ok else MUTED, "line-height: 22px;")}">{name}</div>
  <div style="{mono(18, 400, color, "line-height: 22px;")}">{mark}</div>
</div>
{rule()}"""

def live_signature(text):
    return f"""<div style="display: flex; flex-direction: row; align-items: center; gap: 10px; padding: 0 16px 12px 16px;">
  {dot(POS, pulse=True)}
  {meta(text)}
</div>"""

def ink_button(text, outline=False):
    if outline:
        style = f"background: transparent; color: {INK}; border: 1px solid {INK};"
    else:
        style = f"background: {INK}; color: {PAPER}; border: 1px solid {INK};"
    return f'<div style="display: flex; align-items: center; justify-content: center; height: 56px; border-radius: 4px; {style} {sans(16, 500, PAPER if not outline else INK)} box-sizing: border-box;">{text}</div>'

def hairline_field(label, value, unit=None, action=None, placeholder=False, value_style=None):
    vs = value_style or mono(28, 400, MUTED if placeholder else INK, "line-height: 34px;")
    unit_html = f'<div style="{mono(14, 400, MUTED)}">{unit}</div>' if unit else ""
    act = f'<div style="{mono(11, 500, INK, "text-transform: uppercase; letter-spacing: 0.08em;")} padding: 12px 0 12px 12px;">{action}</div>' if action else ""
    return f"""<div style="display: flex; flex-direction: column; gap: 6px; padding: 0 16px;">
  {meta(label)}
  <div style="display: flex; flex-direction: row; align-items: center; justify-content: space-between; gap: 12px; border-bottom: 1px solid {RULE}; padding-bottom: 8px;">
    <div style="display: flex; flex-direction: row; align-items: baseline; gap: 8px; min-width: 0;">
      <div style="{vs}">{value}</div>
      {unit_html}
    </div>
    {act}
  </div>
</div>"""

def list_row(ticker, company, right_main, right_sub, meta_line, muted=False, trailing=None):
    tcolor = MUTED if muted else INK
    ccolor = MUTED if muted else INK2
    rs = f'<div style="{sans(14, 400, MUTED, "line-height: 20px;")}">{right_sub}</div>' if right_sub else ""
    tr = f'<div style="{mono(11, 500, INK, "text-transform: uppercase; letter-spacing: 0.08em;")} padding: 12px 0 12px 12px; flex: none;">{trailing}</div>' if trailing else ""
    return f"""<div style="display: flex; flex-direction: row; align-items: center; justify-content: space-between; gap: 12px; min-height: 56px; padding: 12px 16px; box-sizing: border-box;">
  <div style="display: flex; flex-direction: column; min-width: 0; gap: 2px;">
    <div style="display: flex; flex-direction: row; align-items: baseline; gap: 10px;">
      <div style="{mono(16, 500, tcolor, "line-height: 22px;")}">{ticker}</div>
      <div style="{sans(14, 400, ccolor, "line-height: 20px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;")}">{company}</div>
    </div>
    {meta(meta_line) if meta_line else ""}
  </div>
  <div style="display: flex; flex-direction: row; align-items: center; gap: 8px; flex: none;">
    <div style="display: flex; flex-direction: row; align-items: baseline; gap: 8px;">
      <div style="{mono(20 if not muted else 16, 400, tcolor, "line-height: 26px; white-space: nowrap;")}">{right_main}</div>
      {rs}
    </div>
    {tr}
  </div>
</div>
{rule()}"""

def dateline(text):
    return f"""{rule()}
<div style="padding: 10px 16px; flex: none;">{meta(text)}</div>
{rule()}"""

def banner(text):
    return f'<div style="background: {ELEV}; padding: 12px 16px; {sans(14, 400, INK2, "line-height: 20px;")} flex: none;">{text}</div>'

# ---------------- Detail (shared parts) ----------------
def detail_top():
    watch = f'<div style="{mono(11, 500, INK, "text-transform: uppercase; letter-spacing: 0.08em;")} padding: 16px 0 16px 16px;">WATCH</div>'
    return f"""{header()}
<div style="display: flex; flex-direction: column; padding: 8px 16px 0 16px; flex: none;">
  <div style="display: flex; flex-direction: row; align-items: baseline; justify-content: space-between; gap: 12px;">
    <div style="{mono(56, 500, INK, "line-height: 60px; letter-spacing: -0.02em;")}">TSLAx</div>
    <div style="{mono(28, 400, INK, "line-height: 34px;")}">$366.17</div>
  </div>
  <div style="display: flex; flex-direction: row; align-items: center; justify-content: space-between; gap: 12px; margin-top: 2px;">
    <div style="{sans(16, 400, INK2, "line-height: 22px;")}">Tesla, Inc.</div>
    {watch}
  </div>
  <div style="display: flex; flex-direction: row; align-items: center; justify-content: space-between; gap: 12px; margin-top: 4px;">
    {meta("DATA UPDATED 10 SEP 2026 14:55 UTC")}
  </div>
  <div style="{sans(14, 400, MUTED, "line-height: 20px; margin-top: 10px;")}">tracking within 0.09% of TSLA · NYSE open</div>
</div>
{section_head("01", "TRUST")}
{live_signature("READ FROM MINT · SLOT 445 912 118 · 2 S AGO")}
{rule()}
{metric_row("Proof of reserves", "100.7%", "26,101 shares held · 25,924 in circulation")}
{metric_row("Permanent delegate", "yes", "issuer can move tokens", tone=CAUTION)}
{metric_row("Transfers pausable", "yes", "not paused", tone=CAUTION)}
{metric_row("Split multiplier", "1.00", "no pending change")}
{metric_row("Transfer hook", "none")}
"""

def detail_rest():
    return f"""{section_head("02", "QUALITY")}
{strata_row("Quality", "8/9", "strong", 86)}
{metric_row("Return on invested capital", "9.8%", "sector median 6.1%")}
{metric_row("Gross margin", "17.9%", "sector median 21.4%")}
{section_head("03", "VALUATION")}
{strata_row("Valuation", "51", "fair", 51)}
{metric_row("Price / earnings", "92.4×", "sector median 27.0×")}
{metric_row("Enterprise value / sales", "11.2×", "sector median 2.4×")}
{section_head("04", "MOMENTUM")}
{strata_row("Momentum", "0.79", "high", 79)}
{metric_row("52-week position", "0.79", "of the low–high range")}
{metric_row("vs sector, 6 months", "+14.2 pt", "relative price change")}
{section_head("05", "F-SCORE")}
<div style="display: flex; flex-direction: row; align-items: baseline; gap: 12px; padding: 0 16px 8px 16px;">
  <div style="{mono(56, 500, INK, "line-height: 60px; letter-spacing: -0.02em;")}">8</div>
  <div style="{sans(16, 400, MUTED, "line-height: 22px;")}">of 9</div>
</div>
{rule()}
{signal_row("Return on assets positive")}
{signal_row("Operating cash flow positive")}
{signal_row("Return on assets improving")}
{signal_row("Cash flow exceeds earnings")}
{signal_row("Leverage falling")}
{signal_row("Liquidity improving", ok=False)}
{signal_row("No new shares issued")}
{signal_row("Gross margin improving")}
{signal_row("Asset turnover improving")}
{section_head("06", "METHOD")}
<div style="display: flex; flex-direction: column; gap: 12px; padding: 0 16px;">
  <div style="{sans(16, 400, INK2, "line-height: 24px;")}">Rule-based classification of fundamentals against the sector. Not a price forecast and not investment advice.</div>
  {meta("FILINGS SEC EDGAR XBRL · PRICES JUPITER PRICE V3 · REFERENCE NYSE CLOSE")}
</div>
<div style="display: flex; flex-direction: column; gap: 10px; padding: 32px 16px 48px 16px;">
  {ink_button("Swap USDC → TSLAx")}
  {meta("EST. ALL-IN COST 0.09% · LIQUIDITY $1.3M")}
</div>
"""

# ---------------- Screens ----------------
def screen_main():
    return frame(detail_top())

def screen_detail_full():
    return frame(detail_top() + detail_rest(), height=2400, clip=False, tall=True)

def screen_list():
    body = f"""{header()}
{tabs("LIST")}
{dateline("TODAY · 2 WATCHED · 1 PREMIUM MOVED · NEXT REPORT AAPLX IN 3 D")}
<div style="height: 20px; flex: none;"></div>
{hairline_field("SEARCH", "Ticker or company", action=None, placeholder=True, value_style=sans(16, 400, MUTED, "line-height: 24px;"))}
{section_head("01", "ANALYZED", top=28)}
{rule()}
{list_row("TSLAx", "Tesla, Inc.", "0.71", "strong", "+0.02% VS NYSE CLOSE · ANALYSIS 2 D OLD")}
{list_row("NVDAx", "NVIDIA Corp.", "0.68", "strong", "−0.04% VS NYSE CLOSE · ANALYSIS 1 D OLD")}
{list_row("AAPLx", "Apple Inc.", "0.61", "fair", "+0.01% VS NYSE CLOSE · ANALYSIS 2 D OLD")}
{list_row("MSFTx", "Microsoft Corp.", "0.58", "fair", "+0.03% VS NYSE CLOSE · ANALYSIS 6 D OLD")}
{list_row("AMZNx", "Amazon.com, Inc.", "0.55", "fair", "−0.02% VS NYSE CLOSE · ANALYSIS 2 D OLD")}
{list_row("COINx", "Coinbase Global", "0.47", "weak", "+0.08% VS NYSE CLOSE · ANALYSIS 3 D OLD")}
{section_head("02", "WITHOUT ANALYSIS", top=24)}
{rule()}
{list_row("TSMx", "Taiwan Semiconductor", "$264.10", "", "+0.05% VS NYSE CLOSE", muted=True)}
{list_row("ASMLx", "ASML Holding", "$812.40", "", "−0.01% VS NYSE CLOSE", muted=True)}
"""
    return frame(body)

def screen_onboarding():
    # the list is visible beneath a paper scrim; the promise sits on top
    behind = f"""<div style="position: absolute; inset: 0; opacity: 0.32; pointer-events: none; display: flex; flex-direction: column;">
{header()}
{tabs("LIST")}
{dateline("TODAY · 2 WATCHED · 1 PREMIUM MOVED · NEXT REPORT AAPLX IN 3 D")}
{section_head("01", "ANALYZED", top=28)}
{rule()}
{list_row("TSLAx", "Tesla, Inc.", "0.71", "strong", "+0.02% VS NYSE CLOSE · ANALYSIS 2 D OLD")}
{list_row("NVDAx", "NVIDIA Corp.", "0.68", "strong", "−0.04% VS NYSE CLOSE · ANALYSIS 1 D OLD")}
{list_row("AAPLx", "Apple Inc.", "0.61", "fair", "+0.01% VS NYSE CLOSE · ANALYSIS 2 D OLD")}
{list_row("MSFTx", "Microsoft Corp.", "0.58", "fair", "+0.03% VS NYSE CLOSE · ANALYSIS 6 D OLD")}
</div>"""
    check = f"""<div style="display: flex; flex-direction: row; align-items: flex-start; gap: 14px; min-height: 48px;">
  <div style="width: 20px; height: 20px; border: 1.5px solid {INK}; border-radius: 2px; background: {INK}; display: flex; align-items: center; justify-content: center; flex: none; margin-top: 2px;">
    <svg width="14" height="14" viewBox="0 0 16 16" fill="none" stroke="{PAPER}" stroke-width="2" stroke-linecap="square"><path d="M3 8.5 6.5 12 13 4.5"></path></svg>
  </div>
  <div style="{sans(14, 400, INK2, "line-height: 20px;")}">I am not a US person, and I understand xStocks are tokenized tracker instruments issued by a third party, not shares.</div>
</div>"""
    panel = f"""<div style="position: absolute; left: 0; right: 0; bottom: 0; background: {PAPER}; border-top: 1px solid {RULE}; display: flex; flex-direction: column; gap: 20px; padding: 28px 16px 40px 16px; box-sizing: border-box;">
  {meta("PLAINTICKER", INK2)}
  <div style="display: flex; flex-direction: column; gap: 8px;">
    <div style="{sans(28, 500, INK, "line-height: 34px;")}">PlainTicker</div>
    <div style="{sans(16, 400, INK2, "line-height: 24px;")}">Tokenized stocks, read before you swap.</div>
  </div>
  <div style="display: flex; flex-direction: column; gap: 10px; {sans(16, 400, INK2, "line-height: 24px;")}">
    <div>Every xStock page starts with what the token itself says: reserves, issuer controls, split multiplier, read live from the Solana mint.</div>
    <div>Then the company against its sector: quality, valuation, momentum, F-Score, from SEC filings.</div>
    <div style="color: {INK};">Not a price forecast. Not investment advice.</div>
  </div>
  {check}
  {ink_button("Read the list")}
</div>"""
    return frame(behind + panel)

def sheet(inner, handle=True):
    """Bottom sheet over a dimmed Detail: 4dp corners, a 2dp rule as the drag handle."""
    behind = f'<div style="position: absolute; inset: 0; opacity: 0.22; pointer-events: none; display: flex; flex-direction: column;">{detail_top()}</div>'
    h = f'<div style="width: 24px; height: 2px; background: {RULE_STRONG}; border-radius: 1px; align-self: center; margin: 12px 0 4px 0;"></div>' if handle else ""
    return frame(behind + f"""<div style="position: absolute; left: 0; right: 0; bottom: 0; background: {PAPER}; border: 1px solid {RULE}; border-bottom: none; border-radius: 4px 4px 0 0; display: flex; flex-direction: column; padding: 0 0 40px 0; box-sizing: border-box;">
  {h}
  {inner}
</div>""")

def screen_swap():
    inner = f"""<div style="display: flex; flex-direction: row; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 16px 8px 16px;">
  <div style="{mono(22, 500, INK, "line-height: 28px;")}">USDC → TSLAx</div>
  <div style="{mono(11, 500, INK, "text-transform: uppercase; letter-spacing: 0.08em;")} padding: 12px 0 12px 12px;">⇅ SELL</div>
</div>
<div style="height: 12px;"></div>
{hairline_field("AMOUNT", "5.00", unit="USDC", action="MAX")}
<div style="padding: 8px 16px 0 16px;">{meta("BALANCE 10.00 USDC")}</div>
<div style="height: 20px;"></div>
{rule()}
{metric_row("You receive", "0.01366 TSLAx", value_size=18)}
{metric_row("Rate", "1 TSLAx = $366.17", "reference $365.84 · NYSE", value_size=18)}
{metric_row("All-in cost", "0.09%", "route Metis · slippage automatic", value_size=18)}
{metric_row("Liquidity", "$1.3M", value_size=18)}
<div style="display: flex; flex-direction: column; gap: 10px; padding: 24px 16px 0 16px;">
  {ink_button("Swap USDC → TSLAx")}
  {meta("SIGN IN SEED VAULT WALLET · NOT INVESTMENT ADVICE")}
</div>"""
    return sheet(inner)

def screen_receipt():
    inner = f"""<div style="display: flex; flex-direction: column; gap: 6px; padding: 12px 16px 8px 16px;">
  <div style="{sans(22, 500, INK, "line-height: 28px;")}">Received 0.01366 TSLAx</div>
  <div style="display: flex; flex-direction: row; align-items: center; gap: 10px;">{dot(POS)}{meta("LANDED · CONFIRMED IN 12.7 S")}</div>
</div>
<div style="height: 12px;"></div>
{rule()}
{metric_row("Paid", "5.00 USDC", value_size=18)}
{metric_row("All-in cost paid", "0.09%", "quote 0.09% · fill +0.00%", value_size=18)}
{metric_row("Signature", "4xQm…9tHe", "tap to copy", value_size=18)}
{metric_row("Slot", "445 912 340", value_size=18)}
<div style="display: flex; flex-direction: column; gap: 10px; padding: 24px 16px 0 16px;">
  {ink_button("View in Portfolio →", outline=True)}
</div>"""
    return sheet(inner, handle=True)

def screen_portfolio():
    wallet = meta("3KF9 … QM2V", INK2)
    body = f"""{header(right=wallet)}
{tabs("PORTFOLIO")}
{section_head("01", "HOLDINGS")}
{rule()}
{metric_row("Total", "$1,284.02", "3 xStocks · priced by Jupiter", value_size=22)}
{list_row("TSLAx", "Tesla, Inc.", "$732.34", "", "2.0 TSLAX · +0.02% VS NYSE CLOSE")}
{list_row("NVDAx", "NVIDIA Corp.", "$366.05", "", "2.1 NVDAX · −0.04% VS NYSE CLOSE")}
{list_row("AAPLx", "Apple Inc.", "$185.63", "", "0.8 AAPLX · +0.01% VS NYSE CLOSE")}
<div style="padding: 16px 16px 0 16px; {sans(14, 400, MUTED, "line-height: 20px;")}">Cost basis is not read from the chain.</div>
"""
    return frame(body)

def screen_watchlist():
    body = f"""{header()}
{tabs("WATCHLIST")}
{section_head("01", "WATCHED")}
{rule()}
{list_row("AAPLx", "Apple Inc.", "reports in 3 d", "", "+0.01% VS NYSE CLOSE · ANALYSIS 2 D OLD", trailing="UNWATCH")}
{list_row("NVDAx", "NVIDIA Corp.", "reports in 41 d", "", "−0.04% VS NYSE CLOSE · ANALYSIS 1 D OLD", trailing="UNWATCH")}
{list_row("TSLAx", "Tesla, Inc.", "no date", "", "+0.02% VS NYSE CLOSE · ANALYSIS 2 D OLD", trailing="UNWATCH")}
<div style="padding: 16px 16px 0 16px;">{meta("DAILY DIGEST 08:00 · NOTIFICATIONS ON")}</div>
"""
    return frame(body)

SCREENS = {
    "Onboarding.dc.html": screen_onboarding(),
    "List.dc.html": screen_list(),
    "Main.dc.html": screen_main(),
    "SwapSheet.dc.html": screen_swap(),
    "Receipt.dc.html": screen_receipt(),
    "DetailFull.dc.html": screen_detail_full(),
    "Portfolio.dc.html": screen_portfolio(),
    "Watchlist.dc.html": screen_watchlist(),
}

for name, html in SCREENS.items():
    io.open(os.path.join(OUT, name), "w", encoding="utf-8", newline="\n").write(html)

GAP = 88
X = lambda i: i * (W + GAP)
canvas = {
    "artboards": [
        {"file": "Onboarding.dc.html", "title": "1 · Onboarding", "x": X(0), "y": 0, "w": W, "h": H},
        {"file": "List.dc.html", "title": "2 · List", "x": X(1), "y": 0, "w": W, "h": H},
        {"file": "Main.dc.html", "title": "3 · Detail — first viewport", "x": X(2), "y": 0, "w": W, "h": H},
        {"file": "SwapSheet.dc.html", "title": "4 · Swap sheet", "x": X(3), "y": 0, "w": W, "h": H},
        {"file": "Receipt.dc.html", "title": "5 · Receipt (landed)", "x": X(4), "y": 0, "w": W, "h": H},
        {"file": "DetailFull.dc.html", "title": "3b · Detail — full scroll", "x": X(0), "y": H + 160, "w": W, "h": 2400},
        {"file": "Portfolio.dc.html", "title": "6 · Portfolio", "x": X(1), "y": H + 160, "w": W, "h": H},
        {"file": "Watchlist.dc.html", "title": "7 · Watchlist", "x": X(2), "y": H + 160, "w": W, "h": H},
    ],
    "annotations": [
        {"id": "brief", "x": X(3), "y": H + 160, "w": 420,
         "text": "PlainTicker Mobile — Seeker, 412dp portrait, warm paper forced.\nBuilt from docs/plan-2026-09-10.md §13 and the MOBILE DELTAS block in DESIGN.md.\nStatic mockups with sample data: TSLAx numbers, F-Score signals, wallet, signature and slot are illustrative.\nRows and rules, never cards · Geist Sans/Mono · one pulse per screen (the live signature) · no BUY/SELL/HOLD anywhere."},
    ],
    "launch": {"view": "canvas"},
}
io.open(os.path.join(OUT, "canvas.json"), "w", encoding="utf-8", newline="\n").write(json.dumps(canvas, ensure_ascii=False, indent=2) + "\n")
print(f"wrote {len(SCREENS)} artboards + canvas.json -> {OUT}")
