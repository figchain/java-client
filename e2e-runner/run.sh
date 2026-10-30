#!/usr/bin/env bash
# Builds the runner against the current e2e tenant (once per tenant) and runs one mode.
set -euo pipefail
cd "$(dirname "$0")"
if [ "$(cat target/.tenant 2>/dev/null)" != "$FC_TENANT_ID" ]; then
    # The plugin always needs the live API, even for the server-unreachable modes.
    FIGCHAIN_URL="$FC_LIVE_API_URL" FIGCHAIN_CLIENT_SECRET="$FC_CLIENT_SECRET" \
        mvn -q -B clean package dependency:build-classpath -Dmdep.outputFile=target/classpath.txt >&2
    echo "$FC_TENANT_ID" > target/.tenant
fi
exec java -Dorg.slf4j.simpleLogger.logFile=System.err -Dorg.slf4j.simpleLogger.defaultLogLevel=warn \
    -cp "target/classes:$(cat target/classpath.txt)" io.figchain.e2e.Runner "$@"
