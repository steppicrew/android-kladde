#!/usr/bin/env python3
"""Builds the public privacy page from the app's privacy strings.

The strings in app/src/main/res/values*/privacy.xml are the only source of the policy: this
script turns them into one self-contained HTML page per language for GitHub Pages (the page
Play links to) and into PRIVACY.md, so the copies cannot drift apart.

    ./scripts/privacy-page.py _site [YYYY-MM-DD]     # HTML pages, dated today if no date
    ./scripts/privacy-page.py --markdown PRIVACY.md  # the English policy as Markdown

English is index.html; every other language is index.<tag>.html. Each page has a language
selector that switches to the page of another language. The English page also switches once
to the reader's browser language when a page for it exists.
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

# Android resource folder suffix → (BCP 47 tag, the language's own name). The order is the
# selector's order; English first, as the default.
LANGUAGES: dict[str, tuple[str, str]] = {
    "": ("en", "English"),
    "de": ("de", "Deutsch"),
}

# Reading order of the page.
SECTIONS = [
    "no_network",
    "storage",
    "other_apps",
    "sharing",
    "control",
    "changes",
    "contact",
]

REQUIRED = ["privacy_title", "privacy_summary", "privacy_updated", "privacy_language"] + [
    f"privacy_{section}_{part}" for section in SECTIONS for part in ("title", "body")
]

# GitHub Pages can't pick a page by Accept-Language, so the English page does it once in the
# browser. Links to English carry ?lang=en, so choosing English isn't undone.
DETECT = """<script>
(function () {
  if (location.search.indexOf("lang=") >= 0) return;
  var pages = %s;
  var wanted = navigator.languages || [navigator.language || ""];
  for (var i = 0; i < wanted.length; i++) {
    var base = wanted[i].toLowerCase().split("-")[0];
    if (base in pages) { if (pages[base]) location.replace(pages[base]); return; }
  }
})();
</script>"""

SELECT = """<label>%s
      <select onchange="location.href = this.value">
%s
      </select>
    </label>
    <noscript>%s</noscript>"""


def page_name(tag: str) -> str:
    return "index.html" if tag == "en" else f"index.{tag}.html"


def page_link(tag: str) -> str:
    return page_name(tag) + ("?lang=en" if tag == "en" else "")


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
    for suffix, (tag, _) in LANGUAGES.items():
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
    names = {t: name for t, name in LANGUAGES.values()}
    sections = "\n".join(
        f"  <section>\n"
        f"    <h2>{html.escape(values[f'privacy_{s}_title'])}</h2>\n"
        f"    <p>{html.escape(values[f'privacy_{s}_body'])}</p>\n"
        f"  </section>"
        for s in SECTIONS
    )
    options = "\n".join(
        f'        <option value="{page_link(t)}" lang="{t}"{" selected" if t == tag else ""}>'
        f"{html.escape(names[t])}</option>"
        for t in tags
    )
    links = " · ".join(
        f'<a href="{page_link(t)}" hreflang="{t}" lang="{t}">{html.escape(names[t])}</a>' for t in tags
    )
    selector = SELECT % (html.escape(values["privacy_language"]), options, links)
    alternates = "\n".join(f'<link rel="alternate" hreflang="{t}" href="{page_name(t)}">' for t in tags)
    detect = DETECT % json.dumps({t: "" if t == "en" else page_name(t) for t in tags}) if tag == "en" else ""
    title = f"{APP_NAME} — {values['privacy_title']}"
    return f"""<!DOCTYPE html>
<html lang="{tag}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)}</title>
{alternates}
{detect}
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
  header {{ display: flex; flex-wrap: wrap; gap: .75rem 1.5rem; justify-content: space-between; align-items: baseline; }}
  h1 {{ font-size: 1.9rem; line-height: 1.2; margin: 0; }}
  h2 {{ font-size: 1.15rem; margin: 2.25rem 0 .4rem; color: var(--accent); }}
  p {{ margin: 0 0 1rem; color: var(--muted); }}
  .lede {{ font-size: 1.1rem; color: var(--fg); margin: 1.5rem 0 2rem; }}
  label {{ font-size: .9rem; color: var(--muted); }}
  select {{ font: inherit; margin-left: .4rem; padding: .2rem .4rem; color: var(--fg); background: var(--bg);
            border: 1px solid var(--rule); border-radius: .3rem; }}
  footer {{ margin-top: 3rem; padding-top: 1.25rem; border-top: 1px solid var(--rule); font-size: .9rem; color: var(--muted); }}
  a {{ color: var(--accent); }}
</style>
</head>
<body>
<header>
  <h1>{html.escape(APP_NAME)} · {html.escape(values["privacy_title"])}</h1>
  <nav aria-label="{html.escape(values["privacy_language"])}">
    {selector}
  </nav>
</header>
<p class="lede">{html.escape(values["privacy_summary"])}</p>
{sections}
<footer>
  <p>{html.escape(updated_text(values, updated))} · <a href="{SOURCE}">{SOURCE.removeprefix("https://")}</a></p>
</footer>
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
