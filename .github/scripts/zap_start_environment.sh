#!/usr/bin/env bash
set -euo pipefail

db="${SCAN_NAMESPACE}-db"
docker run --detach --pull=never --name "$db" --network none --hostname localhost \
  --security-opt no-new-privileges --cap-drop NET_RAW \
  -e POSTGRES_DB=zap -e POSTGRES_USER=zap -e POSTGRES_PASSWORD=zap \
  "$POSTGRES_IMAGE"

docker run --rm --pull=never --network "container:$db" --cap-drop ALL \
  --security-opt no-new-privileges \
  -v "$PWD/.github/scripts:/checks:ro" --entrypoint python3 \
  "$ZAP_IMAGE" /checks/zap_verify_isolation.py

ready=false
for attempt in $(seq 1 60); do
  if docker exec "$db" pg_isready --host 127.0.0.1 -U zap -d zap; then
    ready=true
    break
  fi
  sleep 2
done
if [ "$ready" != true ]; then
  echo "::error::Isolated PostgreSQL did not become ready."
  exit 1
fi

docker run --rm --pull=never --name "${SCAN_NAMESPACE}-migrations" --network "container:$db" \
  --cap-drop ALL --security-opt no-new-privileges \
  -v "$PWD/shared-schema/src/main/resources/db/changelog:/liquibase/changelog/db/changelog:ro" \
  "$LIQUIBASE_IMAGE" --search-path=/liquibase/changelog \
  --changelog-file=db/changelog/db.changelog-master.yaml \
  --url=jdbc:postgresql://127.0.0.1:5432/zap --username=zap --password=zap update

docker exec -i "$db" psql --username zap --dbname zap --single-transaction \
  --set ON_ERROR_STOP=1 --set oid="$ZAP_TEST_OID" \
  --set firm_code="$ZAP_TEST_FIRM_CODE" --set scan_mode="$SCAN_MODE" \
  < .github/zap/seed.sql

jar=$(find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' -print)
if [ ! -f "$jar" ]; then
  echo "::error::Expected exactly one executable application jar."
  exit 1
fi
docker run --detach --pull=never --name "${SCAN_NAMESPACE}-app" --network "container:$db" \
  --user 10000:10000 --cap-drop ALL --security-opt no-new-privileges \
  --read-only --tmpfs /tmp \
  --env-file .github/zap/app.env \
  -v "$PWD/$jar:/app/application.jar:ro" \
  "$APP_IMAGE" java -jar /app/application.jar

docker run --rm --pull=never --network "container:$db" --cap-drop ALL \
  --security-opt no-new-privileges --entrypoint python3 "$ZAP_IMAGE" -c '
import time, urllib.error, urllib.request
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
for attempt in range(90):
    try:
        with opener.open("http://127.0.0.1:8080/actuator/health", timeout=2) as response:
            if response.status == 200:
                break
    except (OSError, urllib.error.URLError):
        time.sleep(2)
else:
    raise RuntimeError("Isolated API did not become healthy")
print("Isolated API is healthy.")
'
