#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
STATE="$ROOT/.run/local-deployment"
HELPER="$ROOT/deploy/local_runtime.py"
command -v python3 >/dev/null || { echo 'Python 3.12+ is required.' >&2; exit 1; }
python3 -c 'import sys; assert sys.version_info >= (3,12), "Python 3.12+ is required"'
if [[ "${1:-}" != '--locked' ]]; then
  python3 "$HELPER" init "$ROOT" "$STATE"
  exec python3 "$HELPER" lock "$STATE" bash "$0" --locked
fi
trap 'echo "Startup stopped. Inspect .run/local-deployment/*.log; scripts/stop-local.sh stops only resources owned by this local launcher." >&2' ERR
umask 077
# Read literal, single-line dotenv values without sourcing or evaluating .env.
ENV_VALUES="$(python3 "$HELPER" env "$ROOT/.env")"
while IFS='=' read -r key value; do
  [[ -z "$key" ]] || export "$key=$value"
done <<< "$ENV_VALUES"
for service in backend frontend; do
  if python3 "$HELPER" active "$STATE" "$service"; then
    echo "Local $service is already running; use scripts/stop-local.sh before restarting." >&2
    exit 1
  fi
done
command -v java >/dev/null || { echo 'JDK 21 is required.' >&2; exit 1; }
command -v javac >/dev/null || { echo 'A full JDK 21 (including javac) is required.' >&2; exit 1; }
JAVA_MAJOR="$(java -version 2>&1 | sed -nE 's/.*version "([0-9]+).*/\1/p' | head -1)"
[[ "$JAVA_MAJOR" == '21' ]] || { echo 'Select JDK 21 with JAVA_HOME/PATH for this invocation.' >&2; exit 1; }
command -v node >/dev/null && command -v npm >/dev/null || { echo 'Node.js 22 and npm are required.' >&2; exit 1; }
NODE_MAJOR="$(node -p 'process.versions.node.split(".")[0]')"
[[ "$NODE_MAJOR" -ge 22 ]] || { echo 'Node.js 22+ is required (Docker builds use Node 22).' >&2; exit 1; }
API_PORT="${API_PORT:-18080}"
LOCAL_UI_PORT="${LOCAL_UI_PORT:-5173}"
DB_PORT="${DB_PORT:-13306}"
[[ "$API_PORT" != "$LOCAL_UI_PORT" ]] || { echo 'API_PORT and LOCAL_UI_PORT must differ.' >&2; exit 1; }
python3 "$HELPER" ports "$API_PORT" "$LOCAL_UI_PORT"
MYSQL_DATABASE="${MYSQL_DATABASE:-zhuqiyun}"
MYSQL_USER="${MYSQL_USER:-zqy}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-zqy_local_demo}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-zqy_root_demo}"
[[ "$MYSQL_DATABASE" =~ ^[A-Za-z0-9_]+$ ]] || { echo 'Use letters, digits, or underscores for MYSQL_DATABASE.' >&2; exit 1; }
export MYSQL_DATABASE MYSQL_USER MYSQL_PASSWORD MYSQL_ROOT_PASSWORD DB_PORT
export LOCAL_OWNER_ID="$(python3 "$HELPER" field "$STATE" owner)"
LOCAL_PROJECT="$(python3 "$HELPER" field "$STATE" project)"
if [[ "${LOCAL_USE_EXISTING_DB:-false}" != 'true' ]]; then
  command -v docker >/dev/null || { echo 'Docker with Compose is required for the local database.' >&2; exit 1; }
  docker compose version >/dev/null
  docker info >/dev/null 2>&1 || { echo 'Start the Docker daemon first.' >&2; exit 1; }
  OWN_RUNNING=false
  if [[ -s "$STATE/mysql-container.id" ]]; then
    OLD_ID="$(cat "$STATE/mysql-container.id")"
    OLD_OWNER="$(docker inspect --format '{{ index .Config.Labels "io.zhuqiyun.local-owner" }}' "$OLD_ID" 2>/dev/null || true)"
    if [[ "$OLD_OWNER" == "$LOCAL_OWNER_ID" ]] && [[ "$(docker inspect --format '{{.State.Running}}' "$OLD_ID" 2>/dev/null)" == 'true' ]]; then
      OWN_RUNNING=true
    fi
  fi
  if [[ "$OWN_RUNNING" != 'true' ]]; then python3 "$HELPER" ports "$DB_PORT"; fi
  echo "Starting owned MySQL Compose project: $LOCAL_PROJECT"
  # Every invocation has a persistent unique project name and random ownership label.
  if ! docker compose -f "$ROOT/compose.yml" --project-name "$LOCAL_PROJECT" up -d mysql; then
    docker compose -f "$ROOT/compose.yml" --project-name "$LOCAL_PROJECT" ps --all --quiet mysql > "$STATE/mysql-container.id" || true
    exit 1
  fi
  docker compose -f "$ROOT/compose.yml" --project-name "$LOCAL_PROJECT" ps --all --quiet mysql > "$STATE/mysql-container.id"
  MYSQL_ID="$(cat "$STATE/mysql-container.id")"
  HEALTH='starting'
  for ((attempt=0; attempt<180; attempt++)); do
    HEALTH="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "$MYSQL_ID")"
    [[ "$HEALTH" != 'healthy' ]] || break
    [[ "$HEALTH" != 'unhealthy' ]] || { echo 'MySQL healthcheck failed; inspect this project with docker compose logs mysql.' >&2; exit 1; }
    sleep 1
  done
  [[ "$HEALTH" == 'healthy' ]] || { echo 'MySQL readiness timed out.' >&2; exit 1; }
fi
export DB_URL="${DB_URL:-jdbc:mysql://127.0.0.1:${DB_PORT}/${MYSQL_DATABASE}?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&preserveInstants=true&characterEncoding=utf8&permitMysqlScheme=true}"
export DB_USER="${DB_USER:-$MYSQL_USER}" DB_PASSWORD="${DB_PASSWORD:-$MYSQL_PASSWORD}"
export SERVER_PORT="$API_PORT" APP_SEED="${APP_SEED:-true}" DEMO_PASSWORD="${DEMO_PASSWORD:-Zhuqi2026!}"
export ATTACHMENT_DIR="$STATE/attachments" TZ=Asia/Shanghai
CACHE="${ZQY_MAVEN_CACHE:-${XDG_CACHE_HOME:-$HOME/.cache}/zhuqiyun}"
MAVEN="$(python3 "$HELPER" maven "$CACHE")"
echo 'Building retained vendor common, then backend (tests skipped; see README evidence status).'
"$MAVEN" -B -ntp -s "$ROOT/deploy/maven-settings.xml" -gs "$ROOT/deploy/maven-settings.xml" \
  "-Dmaven.repo.local=$CACHE/repository" -f "$ROOT/vendor/ruoyi/pom.xml" -DskipTests install > "$STATE/vendor-build.log" 2>&1
"$MAVEN" -B -ntp -s "$ROOT/deploy/maven-settings.xml" -gs "$ROOT/deploy/maven-settings.xml" \
  "-Dmaven.repo.local=$CACHE/repository" -f "$ROOT/backend/pom.xml" -DskipTests package > "$STATE/backend-build.log" 2>&1
mkdir -p "$STATE/attachments"
JARS=("$ROOT"/backend/target/construction-erp-*.jar)
[[ "${#JARS[@]}" == '1' && -f "${JARS[0]}" ]] || { echo 'Expected one backend application JAR.' >&2; exit 1; }
echo 'Installing frontend dependencies from package-lock.json.'
(cd "$ROOT/frontend" && npm --userconfig "$ROOT/deploy/npm-user.conf" --globalconfig /dev/null ci --no-audit --no-fund) > "$STATE/frontend-install.log" 2>&1
python3 "$HELPER" start "$STATE" backend "$STATE/backend.log" "$ROOT/backend" java -jar "${JARS[0]}"
python3 "$HELPER" wait-http "$STATE" backend "http://127.0.0.1:$API_PORT/api/auth/me" 200,401,403 120
export API_PROXY_TARGET="http://127.0.0.1:$API_PORT"
python3 "$HELPER" start "$STATE" frontend "$STATE/frontend.log" "$ROOT/frontend" \
  node "$ROOT/frontend/node_modules/vite/bin/vite.js" --host 127.0.0.1 --port "$LOCAL_UI_PORT" --strictPort
python3 "$HELPER" wait-http "$STATE" frontend "http://127.0.0.1:$LOCAL_UI_PORT/" 200 60
printf 'Local UI: http://127.0.0.1:%s\nAPI: http://127.0.0.1:%s\nLogs: %s\nStop: scripts/stop-local.sh\n' "$LOCAL_UI_PORT" "$API_PORT" "$STATE"
echo 'HTTP responded; business acceptance and DemoSeed results must be checked separately.'
