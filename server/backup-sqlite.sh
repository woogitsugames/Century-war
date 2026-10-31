#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077

dest="${CW_BACKUP_DIR:-/var/backups/century-war}"
mkdir -p "$dest"
container="$(docker compose ps -q leaderboard)"
if [[ -z "$container" ]]; then
    echo "Brak uruchomionego kontenera leaderboard" >&2
    exit 1
fi

name="cw-backup-$(date -u +%Y%m%d-%H%M%S).sqlite"
inside="/var/lib/cw/$name"
cleanup() { docker compose exec -T leaderboard rm -f "$inside" >/dev/null 2>&1 || true; }
trap cleanup EXIT

# SQLite tworzy spójny obraz także podczas zapisywania wyników w trybie WAL.
docker compose exec -T leaderboard sqlite3 /var/lib/cw/leaderboards.db ".backup '$inside'"
docker cp "$container:$inside" "$dest/$name"
test -s "$dest/$name"
find "$dest" -type f -name 'cw-backup-*.sqlite' -mtime +14 -delete
echo "Kopia: $dest/$name"
