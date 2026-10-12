#!/usr/bin/env python3
"""Checks the GitHub Pages site in site/ before it deploys.

- Every local link and asset (href, src) points at a file that exists.
- Nothing is loaded from another origin: no fonts, scripts, styles or
  images from a CDN. Plain links out (<a href>) are fine.
- The privacy policy page exists, since Play and the app link to it.

Usage: check-site.py [site-dir]   (default: site)
"""
import sys
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlsplit, unquote

# The 404 page is served for any path, so its URLs are absolute to the project site.
BASE = "/PokerSI/"


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

    for error in errors:
        print(f"::error::{error}")
    print(f"Checked {len(pages)} pages: {'OK' if not errors else f'{len(errors)} problem(s)'}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
