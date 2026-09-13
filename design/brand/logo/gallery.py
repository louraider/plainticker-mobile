"""
One file the founder opens: the logo, the seven marks, the drawer, and both critics unedited.

Everything is a data URI so the page is one file that works from a phone, an email attachment or a
USB stick with no server and no network. The renders come from render_logo.py and icon_measure.py;
nothing is drawn here.

Run:   PYTHONIOENCODING=utf-8 python design/brand/logo/gallery.py
Reads: design/brand/logo/render/*.png, critique/*.txt, edge-contrast.json
Writes: design/brand/logo/gallery.html
"""
import base64
import io
import json
import sys
from pathlib import Path

from fontTools.subset import Options, Subsetter
from fontTools.ttLib import TTFont
from PIL import Image

HERE = Path(__file__).resolve().parent
BRAND = HERE.parent
sys.path.insert(0, str(BRAND / "attempt-three"))
sys.path.insert(0, str(HERE))

import composite as C3          # noqa: E402  data_uri and escape, already in the repository
import marks_logo as M          # noqa: E402
import wordmark as W            # noqa: E402

RENDER = HERE / "render"
CRITIQUE = HERE / "critique"
GALLERY = HERE / "gallery.html"
NL = chr(10)

EDGES = json.loads((HERE / "edge-contrast.json").read_text(encoding="utf-8"))


def webfont(filename, text):
    """
    One of the app's own faces, subset to the characters this page uses, as a woff2 data URI.

    The page has to be one file and it has to be set in the product's own type. Naming Outfit and
    JetBrains Mono in a font stack and shipping neither means the page falls back to the system
    face, which is the one typographic decision this design system refuses to make. Subsetting
    turns 425 KB of TTF into a few tens of KB, so correctness costs almost nothing.
    """
    font = TTFont(W.FONT_DIR / filename)
    options = Options()
    options.flavor = "woff2"
    options.layout_features = ["kern", "liga", "calt", "tnum"]
    options.desubroutinize = True
    subsetter = Subsetter(options=options)
    subsetter.populate(text="".join(sorted(set(text))))
    subsetter.subset(font)
    buffer = io.BytesIO()
    font.save(buffer)
    return "data:font/woff2;base64," + base64.b64encode(buffer.getvalue()).decode("ascii")


def faces(page_text):
    """The @font-face block, built from the characters the finished page actually contains."""
    alphabet = page_text + "".join(chr(c) for c in range(32, 127)) + "‘’“”"
    entries = []
    for filename, family, weight in (("outfit_regular.ttf", "Outfit", 400),
                                     ("outfit_semibold.ttf", "Outfit", 600),
                                     ("jetbrains_mono_regular.ttf", "JetBrains Mono", 400)):
        entries.append(
            "@font-face {{ font-family: \"{family}\"; font-weight: {weight}; font-style: normal; "
            "font-display: swap; src: url({uri}) format(\"woff2\"); }}".format(
                family=family, weight=weight, uri=webfont(filename, alphabet)))
    return NL.join(entries)


def uri(name, fmt="PNG", **kw):
    return C3.data_uri(Image.open(RENDER / name), fmt, **kw)


def paragraphs(text):
    out = []
    for block in text.replace(chr(13), "").split(NL + NL):
        body = C3.escape(block.strip())
        while "**" in body:
            body = body.replace("**", "<strong>", 1).replace("**", "</strong>", 1)
        while "`" in body:
            body = body.replace("`", "<code>", 1).replace("`", "</code>", 1)
        out.append("<p>" + body.replace(NL, "<br>") + "</p>")
    return "".join(out)


def edge(key):
    return EDGES["candidates"].get(key)


def fmt_edge(key):
    value = edge(key)
    return "-" if value is None else "{:.2f} to 1".format(value)


# -- sections ------------------------------------------------------------------------------------

def wordmark_section():
    cards = []
    verdicts = {
        "split": ("Rejected by both critics.",
                  "Antigravity: “It is two fonts in a logo wearing a justification.” "
                  "Codex found the defect in the argument rather than in the drawing: "
                  "“‘Ticker’ is a word, not a ticker symbol.” The rule the "
                  "setting claimed to apply is about ticker symbols and numerals, and the second "
                  "half of the name is neither."),
        "outfit": ("Ships.",
                   "One face, one name. It carries no idea, and after the split setting was "
                   "killed that is the correct division of labour: the wordmark is the name and "
                   "the mark is the idea."),
        "mono": ("Not shipped.",
                 "The name treated as a ticker throughout. It reads as data rather than as a "
                 "product, and it puts the plain half in the numeral face, which is DESIGN.md "
                 "section 3 broken in reverse."),
    }
    for setting in W.SETTINGS:
        state, why = verdicts[setting.key]
        x0, _, x1, _ = setting.box()
        cards.append(WORD_CARD.format(
            key=C3.escape(setting.key), title=C3.escape(setting.title),
            state=C3.escape(state), why=why,
            argument=C3.escape(setting.argument),
            ratio="{:.2f}".format((x1 - x0) / W.CAP),
            headline=uri("wordmark-{}-headline-dark.png".format(setting.key)),
            light=uri("wordmark-{}-headline-light.png".format(setting.key)),
            one=uri("wordmark-{}-headline-onecolour.png".format(setting.key)),
            inline=uri("wordmark-{}-inline-dark.png".format(setting.key)),
            inline_light=uri("wordmark-{}-inline-light.png".format(setting.key)),
            shipping=" shipping" if setting.key == W.CHOSEN_SETTING else ""))
    return "".join(cards)


CRITIC_READS = {
    "overhang": ("an off-centre capital T or a top-alignment control", "fifth of six"),
    "overhang-flush": ("a thick upper-left corner or crop bracket", "sixth of six"),
    "floor": ("a top rule with a detached insertion cursor beneath it", "fourth of six"),
    "kept-place": ("a pause button resting on a tray, before the registers were separated",
                   "second of six"),
    "short-measure": ("a bar-chart or equalizer control", "first of six"),
    "withheld-print": ("a selected text cursor large, an outlined zero small", "third of six"),
    "short-measure-agy": ("not seen: this drawing exists because Antigravity asked for it", "-"),
    "gauge-inverted": ("a chart-axis fragment with an intersecting cross", "not ranked"),
}


def marks_section():
    cards = []
    for mark in M.ALL:
        codex_read, agy_rank = CRITIC_READS.get(mark.key, ("-", "-"))
        dp, px = mark.on_phone()
        gaps = ", ".join("{} {:g}".format(k, v) for k, v in mark.gaps.items()) or "-"
        treatments = []
        for suffix, label in (("", "Canvas ground"), ("-inverted", "Accent field"),
                              ("-paper", "paper field")):
            key = mark.key + suffix
            if edge(key) is not None:
                treatments.append('<li><b>{}</b> {}</li>'.format(
                    C3.escape(label), fmt_edge(key)))
        cards.append(MARK_CARD.format(
            key=C3.escape(mark.key), title=C3.escape(mark.title),
            says=C3.escape(mark.says), glyph=C3.escape(mark.system_glyph),
            apart=C3.escape(mark.apart), refusal=C3.escape(mark.refusal),
            breaks=C3.escape(mark.breaks), risk=C3.escape(mark.risk),
            waiver=('<p class="waiver">Waives {}</p>'.format(C3.escape(mark.waiver))
                    if mark.waiver else ""),
            codex=C3.escape(codex_read), agy=C3.escape(agy_rank),
            thin="{:g} units, {:.1f}dp, {:.0f} device pixels".format(mark.thinnest(), dp, px),
            ink="{:.1f}".format(mark.ink_share() * 100), gaps=C3.escape(gaps),
            corner="{:.2f}".format(mark.radius()),
            treatments="".join(treatments),
            dark=uri("mark-{}-dark.png".format(mark.key)),
            light=uri("mark-{}-light.png".format(mark.key)),
            one=uri("mark-{}-onecolour.png".format(mark.key)),
            sizes=uri("mark-{}-sizes-dark.png".format(mark.key)),
            sizes_light=uri("mark-{}-sizes-light.png".format(mark.key)),
            sizes_one=uri("mark-{}-sizes-onecolour.png".format(mark.key)),
            drawer=uri(best_drawer(mark.key) + "-crop.png", "JPEG", quality=88, subsampling=0),
            drawer_key=C3.escape(best_drawer(mark.key)),
            drawer_edge=fmt_edge(best_drawer(mark.key)),
            chosen=" chosen" if mark.key == M.CHOSEN else ""))
    return "".join(cards)


def best_drawer(key):
    for suffix in ("-paper", "-inverted", ""):
        if edge(key + suffix) is not None:
            return key + suffix
    return key


def table_rows():
    rows = []
    for name, value in EDGES["references"].items():
        rows.append('<tr class="ref"><td colspan="2">{}</td><td class="n">{:.2f}</td></tr>'.format(
            C3.escape(name), value))
    for key, value in sorted(EDGES["candidates"].items(), key=lambda kv: -kv[1]):
        ground = ("paper" if key.endswith("-paper") else
                  "Accent field" if key.endswith("-inverted") or key == "withheld-print"
                  or key == "gauge-inverted" else "Canvas")
        rows.append('<tr class="{cls}"><td>{key}</td><td>{ground}</td>'
                    '<td class="n">{value:.2f}</td></tr>'.format(
                        cls="win" if key == EDGES["chosen"] else "",
                        key=C3.escape(key), ground=ground, value=value))
    return "".join(rows)


# -- templates -----------------------------------------------------------------------------------

WORD_CARD = """
<article class="card{shipping}" id="wordmark-{key}">
  <header><h3>{title}</h3><span class="state">{state}</span></header>
  <div class="shots">
    <figure><img src="{headline}" alt=""><figcaption>headline, on Canvas</figcaption></figure>
    <figure class="pale"><img src="{light}" alt=""><figcaption>on paper</figcaption></figure>
    <figure><img src="{one}" alt=""><figcaption>one colour</figcaption></figure>
  </div>
  <div class="shots">
    <figure><img src="{inline}" alt=""><figcaption>in a row of text, 17px</figcaption></figure>
    <figure class="pale"><img src="{inline_light}" alt=""><figcaption>the same on paper</figcaption></figure>
  </div>
  <p>{argument}</p>
  <p class="verdict">{why}</p>
  <dl><dt>width</dt><dd>{ratio} cap heights</dd></dl>
</article>
"""

MARK_CARD = """
<article class="card{chosen}" id="mark-{key}">
  <header><h3>{title}</h3><code>{key}</code></header>
  <p class="says">{says}</p>
  <div class="shots">
    <figure><img src="{dark}" alt=""><figcaption>full colour</figcaption></figure>
    <figure class="pale"><img src="{light}" alt=""><figcaption>on paper</figcaption></figure>
    <figure><img src="{one}" alt=""><figcaption>one colour</figcaption></figure>
  </div>
  <div class="shots wide">
    <figure><img src="{sizes}" alt=""><figcaption>16, 24, 48 and 60.7dp at 1:1</figcaption></figure>
  </div>
  <div class="shots wide">
    <figure class="pale"><img src="{sizes_light}" alt=""><figcaption>the same on paper</figcaption></figure>
    <figure><img src="{sizes_one}" alt=""><figcaption>the same in one colour</figcaption></figure>
  </div>
  <div class="shots wide">
    <figure><img src="{drawer}" alt=""><figcaption>{drawer_key} in the real Seeker drawer, edge {drawer_edge}</figcaption></figure>
  </div>
  <dl>
    <dt>nearest system glyph</dt><dd>{glyph}</dd>
    <dt>not that glyph because</dt><dd>{apart}</dd>
    <dt>what Codex saw in the pixels</dt><dd>{codex}</dd>
    <dt>where Antigravity ranked it</dt><dd>{agy}</dd>
    <dt>the refusal</dt><dd>{refusal}</dd>
    <dt>breaks</dt><dd>{breaks}</dd>
    <dt>its own worst risk</dt><dd>{risk}</dd>
    <dt>thinnest shape</dt><dd>{thin}</dd>
    <dt>gaps (units)</dt><dd>{gaps}</dd>
    <dt>ink share of the visible 72</dt><dd>{ink}%</dd>
    <dt>furthest corner</dt><dd>{corner} of the 33 circle guarantee</dd>
  </dl>
  <ul class="edges">{treatments}</ul>
  {waiver}
</article>
"""

PAGE = """<title>PlainTicker, the logo</title>
<style>
  {faces}
  :root {{
    color-scheme: dark;
    --canvas: #0B0F14; --elevated: #121820; --ink: #E8ECF1; --ink2: #B4BCC8;
    --muted: #7F8A99; --line: rgba(232,236,241,0.10); --strong: rgba(232,236,241,0.22);
    --accent: #5AA9E6; --paper: #F2F5F8;
  }}
  * {{ box-sizing: border-box; }}
  html {{ scrollbar-color: var(--strong) var(--canvas); }}
  body {{ margin: 0; background: var(--canvas); color: var(--ink);
    font: 16px/1.55 "Outfit", ui-sans-serif, system-ui, sans-serif;
    -webkit-font-smoothing: antialiased; }}
  ::selection {{ background: var(--accent); color: var(--canvas); }}
  code, .n, td.n {{ font-family: "JetBrains Mono", ui-monospace, SFMono-Regular, monospace; }}
  .wrap {{ max-width: 1060px; margin: 0 auto; padding: 56px 20px 96px; }}
  h1, h2, h3 {{ text-wrap: balance; }}
  h1 {{ font-size: 36px; font-weight: 600; letter-spacing: -0.01em; margin: 0 0 10px; }}
  h2 {{ font-size: 22px; font-weight: 600; letter-spacing: -0.01em;
    margin: 72px 0 6px; }}
  h3 {{ font-size: 18px; font-weight: 600; margin: 0; }}
  p {{ color: var(--ink2); margin: 0 0 12px; max-width: 74ch; }}
  .lede {{ color: var(--ink2); font-size: 16px; }}
  .sub {{ color: var(--muted); margin-bottom: 22px; }}
  .hero {{ border: 1px solid var(--line); background: var(--canvas); padding: 34px 30px;
    margin: 26px 0 10px; }}
  .hero img {{ display: block; max-width: 100%; }}
  .hero.pale {{ background: var(--paper); }}
  .heroes {{ display: grid; gap: 14px; grid-template-columns: repeat(auto-fit, minmax(290px, 1fr)); }}
  .card {{ border: 1px solid var(--line); padding: 22px 20px; margin: 14px 0;
    background: var(--elevated); }}
  .card.chosen, .card.shipping {{ border-color: var(--accent); }}
  .card header {{ display: flex; align-items: baseline; gap: 12px; justify-content: space-between;
    border-bottom: 1px solid var(--line); padding-bottom: 10px; margin-bottom: 14px; }}
  .card header code {{ color: var(--muted); font-size: 12px; }}
  .state {{ color: var(--accent); font-size: 13px; font-weight: 600; }}
  .says {{ color: var(--ink); }}
  .verdict {{ color: var(--ink2); border-left: 2px solid var(--strong); padding-left: 12px; }}
  .waiver {{ color: #D9A441; font-size: 13px; }}
  .shots {{ display: flex; gap: 12px; flex-wrap: wrap; margin: 0 0 12px; }}
  .shots.wide figure {{ flex: 1 1 100%; }}
  figure {{ margin: 0; background: var(--canvas); border: 1px solid var(--line);
    padding: 10px; flex: 1 1 200px; min-width: 0; }}
  figure.pale {{ background: var(--paper); }}
  figure img {{ display: block; max-width: 100%; height: auto; }}
  figcaption {{ color: var(--muted); font-size: 12px; margin-top: 8px;
    font-family: "JetBrains Mono", ui-monospace, monospace; }}
  figure.pale figcaption {{ color: #5c6774; }}
  dl {{ display: grid; grid-template-columns: minmax(150px, 210px) 1fr; gap: 3px 16px;
    margin: 14px 0 0; font-size: 14px; }}
  dt {{ color: var(--muted); }}
  dd {{ margin: 0; color: var(--ink2); }}
  ul.edges {{ list-style: none; padding: 0; margin: 14px 0 0; display: flex; gap: 18px;
    flex-wrap: wrap; font-size: 13px; color: var(--ink2);
    font-family: "JetBrains Mono", ui-monospace, monospace; }}
  ul.edges b {{ color: var(--muted); font-weight: 400; }}
  .scroll {{ overflow-x: auto; margin-top: 14px; }}
  table {{ border-collapse: collapse; width: 100%; font-size: 14px; min-width: 460px; }}
  th, td {{ text-align: left; padding: 7px 10px; border-bottom: 1px solid var(--line); }}
  td.n {{ text-align: right; font-variant-numeric: tabular-nums; }}
  tr.ref td {{ color: var(--muted); }}
  tr.win td {{ color: var(--accent); }}
  .critic {{ border: 1px solid var(--line); padding: 22px 20px; margin: 14px 0;
    background: var(--elevated); }}
  .critic h3 {{ margin-bottom: 4px; }}
  .critic .run {{ color: var(--muted); font-size: 12px; margin-bottom: 14px;
    font-family: "JetBrains Mono", ui-monospace, monospace; }}
  .critic p {{ color: var(--ink); }}
  .split {{ display: grid; gap: 14px; grid-template-columns: repeat(auto-fit, minmax(340px, 1fr)); }}
  .sheet img {{ width: 100%; display: block; }}
  hr {{ border: 0; border-top: 1px solid var(--line); margin: 56px 0 0; }}
  @media (max-width: 620px) {{ dl {{ grid-template-columns: 1fr; }} dt {{ margin-top: 8px; }} }}
</style>
<div class="wrap">
<h1>PlainTicker, the logo</h1>
<p class="lede">There was no logo, so three launcher icons were designed without one and all three
were rejected. This round designs the logo first: a wordmark, a mark, two lockups, and a launcher
icon that is the same drawing rather than a different one. Two critics were run against it,
Antigravity and Codex, and they are quoted in full at the bottom, unedited, including where they
contradict each other and where they contradict me.</p>

<h2>The logo</h2>
<p class="sub">The mark, the wordmark, and the horizontal lockup. This is the thing.</p>
<div class="hero"><img src="{lockup_dark}" alt=""></div>
<div class="heroes">
  <div class="hero pale"><img src="{lockup_light}" alt=""></div>
  <div class="hero"><img src="{lockup_one}" alt=""></div>
  <div class="hero"><img src="{stack_dark}" alt=""></div>
  <div class="hero pale"><img src="{stack_light}" alt=""></div>
</div>
<p>The mark is three registered places on a row: two hold a figure and the third is kept, drawn and
left empty. It is the app's one distinctive act, the refusal to print a figure it cannot stand
behind, drawn as a place that stays in the row. Its figure blocks stand at the wordmark's cap
height and its registers hang below the wordmark's baseline, the way a descender does, so the mark
meets the word by a typographic rule rather than by a ratio picked by eye.</p>

<h2>The wordmark</h2>
<p class="sub">Three settings were drawn. Both critics killed the interesting one and they were
right.</p>
{wordmarks}

<h2>The seven marks</h2>
<p class="sub">Six candidates, one drawing made because a critic asked for it, and one control. The
control is the mark on the phone now, so the claim that the brand mark is the tracking gauge is
tested against a picture rather than argued about.</p>
{marks}

<h2>The launcher icon, measured in the real drawer</h2>
<p class="sub">Sampled off the finished composite: a three-pixel ring inside the launcher's own
measured mask against a three-pixel ring outside it, as a WCAG contrast ratio. Near 1 means the
tile does not exist as an object.</p>
<div class="scroll"><table>
  <thead><tr><th>drawing</th><th>ground</th><th class="n">edge contrast</th></tr></thead>
  <tbody>{rows}</tbody>
</table></div>
<p style="margin-top:18px">The third row of that table is the round's own finding. Attempts three,
four and five only ever asked one ground question, Canvas or an Accent field, and every round took
the Accent field and paid for it by knocking the mark out, which flattens a two-colour mark into a
single silhouette. A cool off-white field reads 16.89 against Photos's 18.06, and it is the only
treatment that keeps the mark's two-colour structure.</p>
<div class="hero sheet"><img src="{sheet}" alt=""></div>

<h2>The two critics, in their own words</h2>
<p class="sub">Neither block below has been edited, tightened, reordered or paraphrased. My
adjudication is underneath and nothing above it is mine.</p>
<div class="split">
  <div class="critic">
    <h3>Google Antigravity</h3>
    <div class="run">gemini-3.1-pro-high, effort high, plan mode, sandbox, 48.1 s, 23,401 tokens,
    one turn</div>
    {antigravity}
  </div>
  <div class="critic">
    <h3>Codex</h3>
    <div class="run">codex-cli 0.154.0, non-interactive, read-only, with the three rendered sheets
    attached as images</div>
    {codex}
  </div>
</div>

<h2>Adjudication</h2>
{verdict}
<hr>
<p class="sub" style="margin-top:20px">Generated by design/brand/logo/gallery.py. The geometry is in
marks_logo.py, the type in wordmark.py, the renders in render_logo.py, and the drawer measurement
in icon_measure.py, which imports attempt five's edge_contrast rather than rewriting it.</p>
</div>
"""


def main():
    antigravity = (CRITIQUE / "antigravity.txt").read_text(encoding="utf-8")
    codex = (CRITIQUE / "codex.txt").read_text(encoding="utf-8")
    verdict = (HERE / "ADJUDICATION.md").read_text(encoding="utf-8")
    body = PAGE.format(
        faces="",
        lockup_dark=uri("lockup-horizontal-dark.png"),
        lockup_light=uri("lockup-horizontal-light.png"),
        lockup_one=uri("lockup-horizontal-onecolour.png"),
        stack_dark=uri("lockup-stacked-dark.png"),
        stack_light=uri("lockup-stacked-light.png"),
        wordmarks=wordmark_section(),
        marks=marks_section(),
        rows=table_rows(),
        sheet=C3.data_uri(Image.open(RENDER / "drawer-contact-sheet.png"), "JPEG", quality=82,
                          subsampling=0),
        antigravity=paragraphs(antigravity),
        codex=paragraphs(codex),
        verdict=paragraphs(verdict))
    # The faces are built last, from the finished page, so nothing is embedded that is not used.
    html = body.replace("<style>" + NL + "  " + NL, "<style>" + NL + "  " + faces(body) + NL, 1)
    GALLERY.write_text(html, encoding="utf-8", newline=NL)
    print("gallery {} ({:.1f} KB)".format(GALLERY, GALLERY.stat().st_size / 1024))


if __name__ == "__main__":
    main()
