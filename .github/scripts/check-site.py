#!/usr/bin/env python3
"""Checks the GitHub Pages site in site/ before it deploys.

- Every local link and asset (HTML href and src, CSS url() and @import)
  points at a file that exists.
- Nothing is loaded from another origin: no fonts, scripts, styles or
  images from a CDN, in the HTML or the CSS. Plain links out (<a href>)
  are fine.
- The privacy policy page exists, since Play and the app link to it.

Usage: check-site.py [site-dir]   (default: site)
"""
import re
import sys
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlsplit, unquote

# The 404 page is served for any path, so its URLs are absolute to the project site.
BASE = "/PokerSI/"

# url(...) in any form, and @import "..." without url().
CSS_REF = re.compile(r"""url\(\s*(['"]?)(.*?)\1\s*\)|@import\s+(['"])(.*?)\3""", re.I)


class Refs(HTMLParser):
    def __init__(self):
        super().__init__()
        self.refs = []  # (tag, attr, value, rel)

    def handle_starttag(self, tag, attrs):
        rel = dict(attrs).get("rel") or ""
        for name, value in attrs:
            if name in ("href", "src") and value is not None:
                self.refs.append((tag, name, value, rel))


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else "site").resolve()
    errors = []
    if not (root / "privacy" / "index.html").is_file():
        errors.append("privacy/index.html is missing")

    pages = sorted(root.rglob("*.html"))
    for page in pages:
        parser = Refs()
        parser.feed(page.read_text(encoding="utf-8"))
        for tag, attr, value, rel in parser.refs:
            where = f"{page.relative_to(root)}: <{tag} {attr}={value!r}>"
            url = urlsplit(value)
            if url.scheme or value.startswith("//"):
                # Only links may leave the site; everything the page loads is local.
                if tag != "a" and rel != "canonical":
                    errors.append(f"{where} loads from another origin")
                continue
            if not url.path:
                continue  # "#anchor"
            path = unquote(url.path)
            if path.startswith("/"):
                if not path.startswith(BASE):
                    errors.append(f"{where} is absolute outside {BASE}")
                    continue
                target = root / path[len(BASE):]
            else:
                target = page.parent / path
            if target.is_dir() or path.endswith("/"):
                target = target / "index.html"
            if not target.resolve().is_file():
                errors.append(f"{where} points at a missing file")

    # Stylesheets, plus <style> blocks and style="" attributes in the pages.
    sheets = sorted(root.rglob("*.css"))
    for source in sheets + pages:
        errors += css_errors(root, source)

    for error in errors:
        print(f"::error::{error}")
    print(f"Checked {len(pages)} pages and {len(sheets)} stylesheets: {'OK' if not errors else f'{len(errors)} problem(s)'}")
    return 1 if errors else 0


def css_errors(root: Path, source: Path) -> list:
    errors = []
    text = re.sub(r"/\*.*?\*/", "", source.read_text(encoding="utf-8"), flags=re.S)
    for match in CSS_REF.finditer(text):
        value = match.group(2) or match.group(4) or ""
        where = f"{source.relative_to(root)}: {match.group(0)}"
        if value.startswith("data:") or value.startswith("#"):
            continue
        if urlsplit(value).scheme or value.startswith("//"):
            errors.append(f"{where} loads from another origin")
            continue
        path = unquote(urlsplit(value).path)
        if path.startswith(BASE):
            target = root / path[len(BASE):]
        else:
            target = source.parent / path
        if path and not target.resolve().is_file():
            errors.append(f"{where} points at a missing file")
    return errors


if __name__ == "__main__":
    sys.exit(main())
