#!/usr/bin/env bash
# Applies the Leaf-PvP changes on top of Leaf's patched sources (after ./gradlew applyAllPatches).
# pvp/apply.py swaps exact pieces of Leaf's code and fails the build when Leaf moved one; any
# pvp/minecraft/*.diff (leaf-server/src/minecraft/java) and pvp/paper/*.diff (paper-server/src/main/java) are applied
# after it. New classes live in leaf-server/src/main/java/io/github/lmalololol/leafpvp and need neither.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
python3 "$root/pvp/apply.py" "$root"
apply() {
  local dir="$1" target="$2"
  [ -d "$root/pvp/$dir" ] || return 0
  for d in $(ls "$root/pvp/$dir"/*.diff 2>/dev/null | sort); do
    echo "applying $(basename "$d") to $target"
    git -C "$root/$target" apply --whitespace=nowarn "$d"
  done
}
apply minecraft leaf-server/src/minecraft/java
apply paper paper-server/src/main/java
echo "Leaf-PvP changes applied"
