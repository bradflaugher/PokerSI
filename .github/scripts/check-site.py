#!/usr/bin/env python3
"""Checks the GitHub Pages site in site/ before it deploys.

- Every local link and asset (HTML href and src, CSS url() and @import)
  points at a file that exists, and every #fragment matches an id.
- Nothing is loaded from another origin: no fonts, scripts, styles or
  images from a CDN, in the HTML or the CSS. Plain links out (<a href>)
  are fine.
- The privacy policy page exists, since Play and the app link to it.
- Each stylesheet link carries the stylesheet's content hash (`style.css?v=…`),
  so a page never pairs with a stale cached copy: Pages lets browsers cache
  for ten minutes, and a new page on an old stylesheet renders broken. Run
  with --fix to write the current hashes into the pages after a CSS change.

Usage: check-site.py [site-dir] [--fix]   (default: site)
"""
import hashlib
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
        self.styles = []
        self.ids = set()

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if "id" in a and a["id"]:
            self.ids.add(a["id"])
        rel = a.get("rel") or ""
        if tag == "link" and "stylesheet" in rel.lower().split() and a.get("href"):
            self.styles.append(a["href"])
        for name, value in attrs:
            if name in ("href", "src") and value is not None:
                self.refs.append((tag, name, value, rel))


def resolve(root: Path, page: Path, path: str) -> Path | None:
    if path.startswith("/"):
        if not path.startswith(BASE):
            return None
        target = root / path[len(BASE):]
    else:
        target = page.parent / path
    if target.is_dir() or path.endswith("/"):
        target = target / "index.html"
    return target


def version(css: Path) -> str:
    # LF-normalized, so a CRLF checkout stamps the hash CI computes.
    return hashlib.sha256(css.read_bytes().replace(b"\r\n", b"\n")).hexdigest()[:10]


def fix_versions(root: Path, pages: list[Path]) -> None:
    """Rewrites each local stylesheet href to carry its file's current hash."""
    for page in pages:
        html = new = page.read_text(encoding="utf-8")
        parser = Refs()
        parser.feed(html)
        for href in parser.styles:
            path = unquote(urlsplit(href).path)
            target = resolve(root, page, path) if path else None
            if target is not None and inside(root, target) and target.resolve().is_file():
                new = re.sub(
                    r'(href=["\'])' + re.escape(href) + r'(["\'])',
                    lambda m: f"{m.group(1)}{urlsplit(href).path}?v={version(target.resolve())}{m.group(2)}",
                    new,
                )
        if new != html:
            page.write_text(new, encoding="utf-8")


def main() -> int:
    args = [a for a in sys.argv[1:] if a != "--fix"]
    root = Path(args[0] if args else "site").resolve()
    pages = sorted(root.rglob("*.html"))
    if "--fix" in sys.argv[1:]:
        fix_versions(root, pages)
    errors = []
    if not (root / "privacy" / "index.html").is_file():
        errors.append("privacy/index.html is missing")

    parsed = {}
    for page in pages:
        parser = Refs()
        parser.feed(page.read_text(encoding="utf-8"))
        parsed[page.resolve()] = parser

    for page in pages:
        parser = parsed[page.resolve()]
        for tag, attr, value, rel in parser.refs:
            where = f"{page.relative_to(root)}: <{tag} {attr}={value!r}>"
            url = urlsplit(value)
            if url.scheme or value.startswith("//"):
                # Only links may leave the site; everything the page loads is local.
                if tag != "a" and rel != "canonical":
                    errors.append(f"{where} loads from another origin")
                continue
            if not url.path:
                if url.fragment and unquote(url.fragment) not in parser.ids:
                    errors.append(f"{where} has no matching id #{url.fragment}")
                continue  # "#anchor"
            path = unquote(url.path)
            target = resolve(root, page, path)
            if target is None:
                errors.append(f"{where} is absolute outside {BASE}")
                continue
            if not inside(root, target):
                errors.append(f"{where} points outside the site")
            elif not target.resolve().is_file():
                errors.append(f"{where} points at a missing file")
            else:
                resolved = target.resolve()
                if value in parser.styles and url.query != f"v={version(resolved)}":
                    errors.append(
                        f"{where} should end ?v={version(resolved)} "
                        "(run python3 .github/scripts/check-site.py site --fix)"
                    )
                if url.fragment and resolved in parsed and unquote(url.fragment) not in parsed[resolved].ids:
                    errors.append(f"{where} has no matching id #{url.fragment}")

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
        if path and not inside(root, target):
            errors.append(f"{where} points outside the site")
        elif path and not target.resolve().is_file():
            errors.append(f"{where} points at a missing file")
    return errors


def inside(root: Path, target: Path) -> bool:
    """Only site/ is published, so a file elsewhere in the repo is a broken link."""
    return target.resolve().is_relative_to(root)


if __name__ == "__main__":
    sys.exit(main())
