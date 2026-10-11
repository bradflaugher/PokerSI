#!/usr/bin/env python3
"""Renders the Play and README screenshots on the JVM, then captions them.

    tools/screenshots/shoot.py            # render everything, then caption.py all
    tools/screenshots/shoot.py --no-render  # only rerun caption.py all

No emulator: StoreScreenshotTest (app/src/test/.../ui/screens/) renders
every ScreenshotScenes scene with Robolectric's native graphics at each
Play class (phone 1080x1920, 7-inch 1224x2176, 10-inch 1620x2880, all
exactly 9:16, at densities that give each class its real window size in
dp), plus the whole feature graphic and the launcher icon, into
build/screenshots/<class>/. It only runs with -Pstore, so a plain `test`
stays fast. caption.py then writes the captioned store images into
fastlane/metadata/android/en-US/images/, the feature graphic and icons, and
the uncaptioned README shots in docs/screenshots/.

Needs JDK 21 and the Android SDK (as for any build) and ImageMagick;
pngquant and optipng are used when installed.
"""
import shutil
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
RAW = REPO / "build/screenshots"


def render():
    if RAW.exists():
        shutil.rmtree(RAW)
    subprocess.run(
        [
            str(REPO / "gradlew"), "-PskipNative", "-Pstore",
            ":app:testDebugUnitTest", "--tests", "*StoreScreenshotTest*", "--rerun",
        ],
        cwd=REPO, check=True,
    )


def main():
    args = sys.argv[1:]
    if args not in ([], ["--no-render"]):
        sys.exit(__doc__)
    if not args:
        render()
    subprocess.run([sys.executable, str(Path(__file__).with_name("caption.py")), "all"], cwd=REPO, check=True)


if __name__ == "__main__":
    main()
