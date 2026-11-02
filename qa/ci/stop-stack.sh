#!/usr/bin/env bash
# Baja los procesos que levantó qa/ci/start-stack.sh.
set -u
PIDS="$(cd "$(dirname "$0")" && pwd)/.pids"
[ -f "$PIDS" ] || exit 0
while read -r pid; do
  kill "$pid" 2>/dev/null || true
done < "$PIDS"
rm -f "$PIDS"
