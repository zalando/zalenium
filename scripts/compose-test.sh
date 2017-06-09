#!/usr/bin/env bash

# set -e: exit asap if a command exits with a non-zero status
# set -x: print each command right before it is executed
set -xe

echoerr() { printf "%s\n" "$*" >&2; }

# print error and exit
die () {
  echoerr "ERROR: $1"
  # if $2 is defined AND NOT EMPTY, use $2; otherwise, set to "160"
  errnum=${2-160}
  exit $errnum
}

WaitStarterProxyToRegister()
{
    # Wait for the Proxy to be registered into the hub
    while ! curl -sSL "http://localhost:4444/grid/console" 2>&1 \
            | grep "DockerSeleniumStarterRemoteProxy" 2>&1 >/dev/null; do
        echo -n '.'
        sleep 0.2
    done
}
export -f WaitStarterProxyToRegister

WaitSeleniumHub()
{
    # Other option is to wait for certain text at
    #  logs/stdout.zalenium.hub.log
    while ! curl -sSL "http://localhost:4444/wd/hub/status" 2>&1 \
            | jq -r '.status' 2>&1 | grep "13" >/dev/null; do
        echo -n '.'
        sleep 0.2
    done
}
export -f WaitSeleniumHub

# Required params or defaults
export COMPOSE_FILE="docs/docker-compose.yaml"
if [ "$(uname -s)" != 'Darwin' ]; then
  export HOST_UID="$(id -u)"
  export HOST_GID="$(id -g)"
fi

# Avoid "An HTTP request took too long to complete." error
export COMPOSE_HTTP_TIMEOUT=360

# Ensure we have a clean environment
docker-compose -f ${COMPOSE_FILE} -p zalenium down || true
docker stop zalenium || true
docker rm zalenium || true
rm -rf /tmp/videos
mkdir -p /tmp/videos

# Start in daemon mode
docker-compose -f ${COMPOSE_FILE} -p zalenium up --force-recreate -d

# Attach to the logs but as a background process so we can see the logs on time
docker-compose -f ${COMPOSE_FILE} -p zalenium logs --follow &

# Additional dependencies
pip install --user "selenium==3.3.1"
pip install --user "retrying==1.3.3"

# Wait for Zalenium
if ! timeout --foreground "1m" bash -c WaitSeleniumHub; then
    echo "GridLauncher failed to start after 1 minute, failing..."
    curl "http://localhost:4444/wd/hub/status"
    exit 11
fi

# Wait for Zalenium
if ! timeout --foreground "45s" bash -c WaitStarterProxyToRegister; then
    echo "StarterRemoteProxy failed to register to the hub after 45 seconds, failing..."
    exit 13
fi

echo "Zalenium started! Ready to run some tests!"

# Run some tests
python scripts/python_test.py chrome
python scripts/python_test.py firefox

# Leave a clean environment
docker-compose -f ${COMPOSE_FILE} -p zalenium down

# Videos should still be there
ls -la /tmp/videos
