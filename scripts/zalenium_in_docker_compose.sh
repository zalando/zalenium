#!/usr/bin/env bash

# set -e: exit asap if a command exits with a non-zero status
# set -x: print each command right before it is executed
set -xe

SCRIPT_ACTION=$1

COMPOSE_FILE="${project.build.directory}/docker-compose-test.yaml"

# In OSX install gtimeout through `brew install coreutils`
function mtimeout() {
    if [ "$(uname -s)" = 'Darwin' ]; then
        gtimeout "$@"
    else
        timeout "$@"
    fi
}

echoerr() { printf "%s\n" "$*" >&2; }

# print error and exit
die () {
  echoerr "ERROR: $1"
  # if $2 is defined AND NOT EMPTY, use $2; otherwise, set to "160"
  errnum=${2-160}
  exit $errnum
}

WaitZaleniumStarted()
{
    DONE_MSG="Zalenium is now ready!"
    while ! docker logs zalenium | grep "${DONE_MSG}" >/dev/null; do
        echo -n '.'
        sleep 0.2
    done
}
export -f WaitZaleniumStarted

StartUp()
{
    # Required params or defaults
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

    # Wait for Zalenium
    if ! mtimeout --foreground "2m" bash -c WaitZaleniumStarted; then
        echo "Zalenium failed to start after 2 minutes, failing..."
        exit 8
    fi

    echo "Zalenium started! Ready to run some tests!"
}

ShutDown()
{
    # Leave a clean environment
    docker-compose -f ${COMPOSE_FILE} -p zalenium down
}

case ${SCRIPT_ACTION} in
    start)
        StartUp
    ;;
    stop)
        ShutDown
    ;;
esac



