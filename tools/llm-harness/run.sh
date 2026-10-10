#!/usr/bin/env bash
# Build the harness, dump the app's real prompts and grammars from the JVM
# tests, run them through each model, then parse every reply with the app's
# LlmReply parser. See README.md.
#
# usage: tools/llm-harness/run.sh model.gguf [model2.gguf ...]
# env:   LLAMA_DIR   llama.cpp checkout (default: the app's submodule)
#        WORK_DIR    scratch dir for the build, cases and replies (default: /tmp/pokersi-harness)
#        RUNS        samples per case (default 2), THREADS (default 4)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK_DIR="${WORK_DIR:-/tmp/pokersi-harness}"
LLAMA_DIR="${LLAMA_DIR:-$ROOT/app/src/main/cpp/llama.cpp}"
RUNS="${RUNS:-2}"
THREADS="${THREADS:-4}"
[ $# -ge 1 ] || { echo "usage: $0 model.gguf [...]" >&2; exit 2; }

cmake -S "$ROOT/tools/llm-harness" -B "$WORK_DIR/build" -G Ninja -DCMAKE_BUILD_TYPE=Release \
    -DLLAMA_DIR="$LLAMA_DIR" > /dev/null
cmake --build "$WORK_DIR/build" --target pokersi-harness

rm -rf "$WORK_DIR/cases"
POKERSI_HARNESS_CASES="$WORK_DIR/cases" "$ROOT/gradlew" -p "$ROOT" -PskipNative -q \
    testDebugUnitTest --tests '*HarnessCasesTest.dumpCases' --rerun

status=0
for model in "$@"; do
    name="$(basename "$model" .gguf)"
    echo "=== $name"
    "$WORK_DIR/build/pokersi-harness" -m "$model" -d "$WORK_DIR/cases" -o "$WORK_DIR/cases/out-$name" \
        -t "$THREADS" --runs "$RUNS" || status=1
    POKERSI_HARNESS_CASES="$WORK_DIR/cases" POKERSI_HARNESS_OUT="out-$name" "$ROOT/gradlew" -p "$ROOT" \
        -PskipNative -q testDebugUnitTest --tests '*HarnessCasesTest.checkReplies' --rerun -i \
        | grep -E '^ *[0-9]{2}-|FAILED|AssertionError' || status=1
done
exit $status
