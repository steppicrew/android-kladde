#!/usr/bin/env python3
"""Builds the public privacy page from the app's privacy strings.

The strings in app/src/main/res/values*/privacy.xml are the only source of the policy: this
script turns them into one self-contained HTML page per language for GitHub Pages (the page
Play links to) and into PRIVACY.md, so the copies cannot drift apart.

    ./scripts/privacy-page.py _site [YYYY-MM-DD]     # HTML pages, dated today if no date
    ./scripts/privacy-page.py --markdown PRIVACY.md  # the English policy as Markdown

English is index.html; every other language is index.<tag>.html. Each page links every
language at the top and, when the reader's browser prefers another language that has a page,
offers it in a hint. It never redirects: Play rejects a policy URL that doesn't land on the
policy text itself.
"""

import datetime
import html
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
SOURCE = "https://github.com/steppicrew/android-kladde"
APP_NAME = "Kladde"

# Android resource folder suffix → (BCP 47 tag, the language's own name, the hint that offers
# it in its own words). The order is the link order; English first, as the default.
LANGUAGES: dict[str, tuple[str, str, str]] = {
    "": ("en", "English", "This page is also available in English."),
    "de": ("de", "Deutsch", "Diese Seite gibt es auch auf Deutsch."),
}

# Reading order of the page.
SECTIONS = [
    "no_network",
    "storage",
    "other_apps",
    "reminders",
    "sharing",
    "control",
    "changes",
    "contact",
]

REQUIRED = ["privacy_title", "privacy_summary", "privacy_updated", "privacy_language"] + [
    f"privacy_{section}_{part}" for section in SECTIONS for part in ("title", "body")
]

# The script only *offers* the reader's language; it never navigates. Play wants the linked URL
# to land on the policy text (health-connect-view was rejected as "indirect" on 2026-11-25 for a
# page that switched by itself). A reader arriving from our own language links chose already.
OFFER = """<script>
(function () {
  if (document.referrer.indexOf(location.origin) === 0) return;
  var pages = %s, here = %s;
  var wanted = navigator.languages || [navigator.language || ""];
  for (var i = 0; i < wanted.length; i++) {
    var base = wanted[i].toLowerCase().split("-")[0];
    if (!(base in pages)) continue;
    if (base === here) return;
    var a = document.createElement("a"), hint = document.getElementById("hint");
    a.href = pages[base][0]; a.textContent = pages[base][1]; a.lang = base; a.hreflang = base;
    hint.appendChild(a); hint.hidden = false;
    return;
  }
})();
</script>"""


def page_name(tag: str) -> str:
    return "index.html" if tag == "en" else f"index.{tag}.html"


def read_strings(folder: Path) -> dict[str, str]:
    path = folder / "privacy.xml"
    if not path.exists():
        return {}
    out: dict[str, str] = {}
    for node in ET.parse(path).getroot().iter("string"):
        name = node.get("name")
        if name is not None:
            # Android escapes quotes for its own parser; the page wants them plain.
            out[name] = "".join(node.itertext()).replace("\\'", "'").replace('\\"', '"')
    return out


def load() -> dict[str, dict[str, str]]:
    """Strings per language tag. A translation must be complete: a policy half in English is
    not a translation."""
    found: dict[str, dict[str, str]] = {}
    for suffix, (tag, _, _) in LANGUAGES.items():
        values = read_strings(RES / ("values-" + suffix if suffix else "values"))
        missing = [key for key in REQUIRED if not values.get(key)]
        if missing:
            raise SystemExit(f"error: {tag} privacy strings lack {', '.join(missing)}")
        found[tag] = values
    suffixes = (path.parent.name.removeprefix("values-") for path in RES.glob("values-*/privacy.xml"))
    unknown = sorted(suffix for suffix in suffixes if suffix not in LANGUAGES)
    if unknown:
        raise SystemExit(f"error: add {', '.join(unknown)} to LANGUAGES")
    return found


def updated_text(values: dict[str, str], updated: str) -> str:
    return values["privacy_updated"].replace("%1$s", updated)


def render(values: dict[str, str], tag: str, tags: list[str], updated: str) -> str:
    names = {t: name for t, name, _ in LANGUAGES.values()}
    sections = "\n".join(
        f"  <section>\n"
        f"    <h2>{html.escape(values[f'privacy_{s}_title'])}</h2>\n"
        f"    <p>{html.escape(values[f'privacy_{s}_body'])}</p>\n"
        f"  </section>"
        for s in SECTIONS
    )
    hints = {t: hint for t, _, hint in LANGUAGES.values()}
    # Every language under its own name; this one as text.
    links = " · ".join(
        f'<span aria-current="page">{html.escape(names[t])}</span>'
        if t == tag
        else f'<a href="{page_name(t)}" hreflang="{t}" lang="{t}">{html.escape(names[t])}</a>'
        for t in tags
    )
    alternates = "\n".join(f'<link rel="alternate" hreflang="{t}" href="{page_name(t)}">' for t in tags)
    offer = OFFER % (json.dumps({t: [page_name(t), hints[t]] for t in tags}, ensure_ascii=False), json.dumps(tag))
    title = f"{APP_NAME} — {values['privacy_title']}"
    return f"""<!DOCTYPE html>
<html lang="{tag}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)}</title>
{alternates}
<style>
  :root {{ color-scheme: light dark; --bg: #fdfcf8; --fg: #1c1b18; --muted: #4a473f; --rule: #e3dfd3; --accent: #7a4f12; }}
  @media (prefers-color-scheme: dark) {{
    :root {{ --bg: #1c1b18; --fg: #e8e4da; --muted: #cbc6b8; --rule: #4a473f; --accent: #e6b46a; }}
  }}
  * {{ box-sizing: border-box; }}
  body {{
    margin: 0 auto; padding: 2rem 1rem 4rem; max-width: 42rem; background: var(--bg); color: var(--fg);
    font: 17px/1.65 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  }}
  h1 {{ font-size: 1.9rem; line-height: 1.2; margin: 0; }}
  h2 {{ font-size: 1.15rem; margin: 2.25rem 0 .4rem; color: var(--accent); }}
  p {{ margin: 0 0 1rem; color: var(--muted); }}
  .lede {{ font-size: 1.1rem; color: var(--fg); margin: 1.5rem 0 2rem; }}
  footer {{ margin-top: 3rem; padding-top: 1.25rem; border-top: 1px solid var(--rule); font-size: .9rem; color: var(--muted); }}
  a {{ color: var(--accent); }}
  nav {{ margin: 0 0 1.5rem; font-size: .9rem; line-height: 1.9; color: var(--muted); }}
  .hint {{ margin: 0 0 1.5rem; padding: .6rem .9rem; border: 1px solid var(--rule); border-radius: .5rem; }}
</style>
</head>
<body>
<nav aria-label="{html.escape(values["privacy_language"])}">{links}</nav>
<p class="hint" id="hint" hidden></p>
<h1>{html.escape(APP_NAME)} · {html.escape(values["privacy_title"])}</h1>
<p class="lede">{html.escape(values["privacy_summary"])}</p>
{sections}
<footer>
  <p>{html.escape(updated_text(values, updated))} · <a href="{SOURCE}">{SOURCE.removeprefix("https://")}</a></p>
</footer>
{offer}
</body>
</html>
"""


MARKDOWN_NOTE = "<!-- Generated by scripts/privacy-page.py from app/src/main/res/values/privacy.xml. Edit that, not this file. -->"
UPDATED_LINE = re.compile(r"^\*.*\d{4}-\d{2}-\d{2}\*$", re.MULTILINE)


def render_markdown(values: dict[str, str], updated: str) -> str:
    parts = [
        MARKDOWN_NOTE,
        f"# {APP_NAME} · {values['privacy_title']}",
        f"*{updated_text(values, updated)}*",
        values["privacy_summary"],
    ]
    for s in SECTIONS:
        parts += [f"## {values[f'privacy_{s}_title']}", values[f"privacy_{s}_body"]]
    return "\n\n".join(parts) + "\n"


def write_markdown(target: Path, updated: str) -> None:
    text = render_markdown(load()["en"], updated)
    # Same policy on another day: keep the old date rather than restamp the file.
    if target.exists() and UPDATED_LINE.sub("", target.read_text(encoding="utf-8")) == UPDATED_LINE.sub("", text):
        return
    target.write_text(text, encoding="utf-8")
    print(target)


def write_pages(out_dir: Path, updated: str) -> None:
    translations = load()
    tags = list(translations)
    out_dir.mkdir(parents=True, exist_ok=True)
    for tag, values in translations.items():
        target = out_dir / page_name(tag)
        target.write_text(render(values, tag, tags, updated), encoding="utf-8")
        print(target)


def main(args: list[str]) -> int:
    today = datetime.date.today().isoformat()
    if len(args) == 2 and args[0] == "--markdown":
        write_markdown(Path(args[1]), today)
    elif len(args) in (1, 2) and not args[0].startswith("-"):
        write_pages(Path(args[0]), args[1] if len(args) == 2 and args[1] else today)
    else:
        print(__doc__, file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
