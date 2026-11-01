#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077

# Kopie są poza wolumenem Docker; 14 dni retencji można zmienić zmienną środowiskową.
dest="${CW_BACKUP_DIR:-/var/backups/century-war}"
days="${CW_BACKUP_RETENTION_DAYS:-14}"
if [[ ! "$days" =~ ^[1-9][0-9]{0,3}$ ]] || (( 10#$days > 3650 )); then
    echo "CW_BACKUP_RETENTION_DAYS musi być liczbą od 1 do 3650" >&2
    exit 2
fi
days=$((10#$days))
mkdir -p "$dest"
container="$(docker compose ps -q leaderboard)"
if [[ -z "$container" ]]; then
    echo "Brak uruchomionego kontenera leaderboard" >&2
    exit 1
fi

name="cw-backup-$(date -u +%Y%m%d-%H%M%S)-$$.sqlite"
inside="/var/lib/cw/$name"
staging="$dest/.$name.partial"
cleanup() {
    docker compose exec -T leaderboard rm -f "$inside" >/dev/null 2>&1 || true
    rm -f "$staging"
}
trap cleanup EXIT

# VACUUM INTO otwiera osobne połączenie SQLite i kopiuje spójny stan także w WAL.
docker compose exec -T leaderboard /opt/century-war/bin/server admin backup "$inside" >/dev/null
docker cp "$container:$inside" "$staging"
test -s "$staging"
chmod 600 "$staging"
mv "$staging" "$dest/$name"
# Rotacja wyłącznie po pomyślnym wykonaniu nowej kopii.
find "$dest" -maxdepth 1 -type f -name 'cw-backup-*.sqlite' -mmin "+$((days * 1440))" -delete
echo "Kopia: $dest/$name"
