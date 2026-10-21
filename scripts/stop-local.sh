#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
STATE="$ROOT/.run/local-deployment"
HELPER="$ROOT/deploy/local_runtime.py"
if [[ ! -f "$STATE/owner.json" ]]; then
  echo 'No local-script ownership record; nothing stopped.'
  exit 0
fi
command -v python3 >/dev/null || { echo 'Python 3.12+ is required to verify process ownership.' >&2; exit 1; }
if [[ "${1:-}" != '--locked' ]]; then
  exec python3 "$HELPER" lock "$STATE" bash "$0" --locked
fi
STATUS=0
python3 "$HELPER" stop "$STATE" frontend || STATUS=1
python3 "$HELPER" stop "$STATE" backend || STATUS=1
if [[ -s "$STATE/mysql-container.id" ]]; then
  if ! command -v docker >/dev/null || ! docker info >/dev/null 2>&1; then
    echo 'Docker is unavailable; the recorded MySQL container was not changed.' >&2
    STATUS=1
  else
    ID="$(cat "$STATE/mysql-container.id")"
    EXPECTED_OWNER="$(python3 "$HELPER" field "$STATE" owner)"
    EXPECTED_PROJECT="$(python3 "$HELPER" field "$STATE" project)"
    if docker inspect "$ID" >/dev/null 2>&1; then
      ACTUAL="$(docker inspect --format '{{ index .Config.Labels "io.zhuqiyun.local-owner" }}|{{ index .Config.Labels "com.docker.compose.project" }}|{{ index .Config.Labels "com.docker.compose.service" }}' "$ID")"
      if [[ "$ACTUAL" == "$EXPECTED_OWNER|$EXPECTED_PROJECT|mysql" ]]; then
        docker stop --time 20 "$ID" >/dev/null || STATUS=1
        echo 'Stopped the exact owned MySQL container; named volume retained.'
      else
        echo 'MySQL container ownership changed; container was NOT stopped.' >&2
        STATUS=1
      fi
    else
      echo 'Recorded MySQL container no longer exists; no other container touched.'
    fi
  fi
fi
echo 'Local state, logs, attachments, containers and volumes are retained. Manual Compose resources are not managed by this script.'
exit "$STATUS"
