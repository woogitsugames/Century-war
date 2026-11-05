#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077

# Kopie są poza wolumenem Docker; 14 dni retencji można zmienić zmienną środowiskową.
# Opcjonalnie (ustaw w crontab, nie w .env; opis w docs/SERVER_DEPLOY.md §6 i §10):
#   CW_BACKUP_RCLONE_REMOTE            np. b2crypt:century-war — kopia poza serwerem (rclone na hoście)
#   CW_BACKUP_REMOTE_RETENTION_DAYS    ile dni trzymać kopie zdalne (domyślnie 30)
#   CW_BACKUP_PING_URL                 np. https://hc-ping.com/<uuid> — ping po sukcesie, <url>/fail po błędzie
dest="${CW_BACKUP_DIR:-/var/backups/century-war}"
days="${CW_BACKUP_RETENTION_DAYS:-14}"
remote="${CW_BACKUP_RCLONE_REMOTE:-}"
remote_days="${CW_BACKUP_REMOTE_RETENTION_DAYS:-30}"
ping_url="${CW_BACKUP_PING_URL:-}"

valid_days() {
    [[ "$1" =~ ^[1-9][0-9]{0,3}$ ]] && (( 10#$1 <= 3650 ))
}
if ! valid_days "$days"; then
    echo "CW_BACKUP_RETENTION_DAYS musi być liczbą od 1 do 3650" >&2
    exit 2
fi
days=$((10#$days))
if ! valid_days "$remote_days"; then
    echo "CW_BACKUP_REMOTE_RETENTION_DAYS musi być liczbą od 1 do 3650" >&2
    exit 2
fi
remote_days=$((10#$remote_days))
# Nazwa zdalna rclone ma postać nazwa:ścieżka; myślnik na początku byłby opcją rclone.
if [[ -n "$remote" && ( "$remote" != *:* || "$remote" == -* ) ]]; then
    echo "CW_BACKUP_RCLONE_REMOTE musi mieć postać nazwa:ścieżka (np. b2crypt:century-war)" >&2
    exit 2
fi
if [[ -n "$ping_url" && "$ping_url" != https://* ]]; then
    echo "CW_BACKUP_PING_URL musi zaczynać się od https://" >&2
    exit 2
fi
if [[ -n "$remote" ]] && ! command -v rclone >/dev/null 2>&1; then
    echo "Ustawiono CW_BACKUP_RCLONE_REMOTE, ale na hoście nie ma rclone" >&2
    exit 2
fi

name="cw-backup-$(date -u +%Y%m%d-%H%M%S)-$$.sqlite"
inside="/var/lib/cw/$name"
staging="$dest/.$name.partial"
cleanup() {
    local status=$?
    docker compose exec -T leaderboard rm -f "$inside" >/dev/null 2>&1 || true
    rm -f "$staging"
    # Każdy błąd (także rclone) zgłaszamy do monitora; brak pingu też wywoła alert po czasie.
    if (( status != 0 )) && [[ -n "$ping_url" ]]; then
        curl -fsS -m 10 --retry 3 -o /dev/null "$ping_url/fail" || true
    fi
    exit "$status"
}
trap cleanup EXIT

mkdir -p "$dest"
container="$(docker compose ps -q leaderboard)"
if [[ -z "$container" ]]; then
    echo "Brak uruchomionego kontenera leaderboard" >&2
    exit 1
fi

# VACUUM INTO otwiera osobne połączenie SQLite i kopiuje spójny stan także w WAL.
docker compose exec -T leaderboard /opt/century-war/bin/server admin backup "$inside" >/dev/null
docker cp "$container:$inside" "$staging"
test -s "$staging"
chmod 600 "$staging"
mv "$staging" "$dest/$name"
# Rotacja wyłącznie po pomyślnym wykonaniu nowej kopii.
find "$dest" -maxdepth 1 -type f -name 'cw-backup-*.sqlite' -mmin "+$((days * 1440))" -delete
echo "Kopia: $dest/$name"

if [[ -n "$remote" ]]; then
    # Szyfrowanie zapewnia nazwa zdalna typu crypt (rclone config); plik trafia do niej bez zmian.
    rclone copy --no-traverse "$dest/$name" "$remote"
    # Rotacja zdalna też dopiero po udanym wysłaniu nowej kopii.
    rclone delete --min-age "${remote_days}d" --include 'cw-backup-*.sqlite' "$remote"
    echo "Kopia zdalna: $remote/$name"
fi

if [[ -n "$ping_url" ]]; then
    curl -fsS -m 10 --retry 3 -o /dev/null "$ping_url"
fi
