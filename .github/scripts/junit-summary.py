#!/usr/bin/env python3
"""Turns JUnit XML reports into a Markdown table for $GITHUB_STEP_SUMMARY.

Usage: junit-summary.py <title> <report-dir>...
Prints the summary to stdout; lists every failing test with its message.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET


def main() -> int:
    title, dirs = sys.argv[1], sys.argv[2:]
    suites = []
    failures = []
    for d in dirs:
        for path in sorted(glob.glob(os.path.join(d, "*.xml"))):
            root = ET.parse(path).getroot()
            for suite in [root] if root.tag == "testsuite" else root.iter("testsuite"):
                name = suite.get("name", "?").rsplit(".", 1)[-1]
                tests = int(suite.get("tests", 0))
                failed = int(suite.get("failures", 0)) + int(suite.get("errors", 0))
                skipped = int(suite.get("skipped", 0))
                variant = os.path.basename(os.path.normpath(d)).replace("test", "").replace("UnitTest", "") or "?"
                suites.append((variant, name, tests, failed, skipped, float(suite.get("time", 0))))
                for case in suite.iter("testcase"):
                    for problem in list(case.findall("failure")) + list(case.findall("error")):
                        message = (problem.get("message") or "").strip().splitlines()
                        failures.append((variant, name, case.get("name"), message[0][:300] if message else ""))
    total = sum(s[2] for s in suites)
    failed = sum(s[3] for s in suites)
    icon = "✅" if failed == 0 and total > 0 else "❌"
    print(f"## {icon} {title}: {total - failed}/{total} passed\n")
    print("| Variant | Suite | Tests | Failed | Skipped | Time |")
    print("|---|---|---:|---:|---:|---:|")
    for variant, name, tests, bad, skipped, time in suites:
        print(f"| {variant} | {name} | {tests} | {bad or ''} | {skipped or ''} | {time:.2f}s |")
    if failures:
        print("\n### Failures\n")
        for variant, suite, case, message in failures:
            print(f"- **{variant} · {suite}.{case}** — `{message}`")
    if total == 0:
        print("\nNo test reports were found.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
