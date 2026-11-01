#!/bin/bash
# mergeauto.sh N : merge gpt/N-*, auto-resolve L.kt and android.yml as "both"; list remaining
cd /tmp/claude-0/trial
b=$(git branch -r | grep "gpt/$1-" | tr -d ' ')
git merge --no-ff --no-edit -m "Scal $b" $b >/dev/null 2>&1 && { echo "$1 OK"; exit 0; }
for f in app/src/main/java/com/centurywar/engine/L.kt .github/workflows/android.yml; do
  if git diff --name-only --diff-filter=U | grep -qx "$f"; then
    n=$(grep -c "^<<<<<<<" $f); for i in $(seq $n -1 1); do python3 /tmp/claude-0/tools/res.py $f $i both >/dev/null; done; git add $f
  fi
done
rem=$(git diff --name-only --diff-filter=U)
if [ -z "$rem" ]; then git commit -q --no-edit; echo "$1 OK(auto)"; else echo "$1 REMAINING: $rem"; fi
