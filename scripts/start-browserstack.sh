#!/usr/bin/env bash

# set -e: exit asap if a command exits with a non-zero status
set -e

echoerr() { printf "%s\n" "$*" >&2; }

# print error and exit
die () {
  echoerr "ERROR: $1"
  # if $2 is defined AND NOT EMPTY, use $2; otherwise, set to "160"
  errnum=${2-160}
  exit $errnum
}

# Required params
[ -z "${BROWSER_STACK_KEY}" ] && die "Required env var BROWSER_STACK_KEY"
[ -z "${BROWSER_STACK_TUNNEL_OPTS}" ] && die "Required env var BROWSER_STACK_TUNNEL_OPTS"
[ -z "${BROWSER_STACK_TUNNEL_ID}" ] && die "Required env var BROWSER_STACK_TUNNEL_ID"

# Wait for this process dependencies
# - none, the tunnel has no dependencies
# timeout --foreground ${WAIT_TIMEOUT} wait-xvfb.sh

# Start tunnel
BrowserStackLocal \
  ${BROWSER_STACK_KEY} \
  ${BROWSER_STACK_TUNNEL_OPTS} \
  -localIdentifier ${BROWSER_STACK_TUNNEL_ID} > ${BROWSER_STACK_LOG_FILE} &
BROWSER_STACK_TUNNEL_PID=$!

function shutdown {
  echo "Trapped SIGTERM/SIGINT so shutting down BrowserStack gracefully..."
  kill -SIGINT ${BROWSER_STACK_TUNNEL_PID}
  wait ${BROWSER_STACK_TUNNEL_PID}
  echo "BrowserStack tunnel shutdown complete."
  exit 0
}

# Run function shutdown() when this process receives a killing signal
trap shutdown SIGTERM SIGINT SIGKILL

# tells bash to wait until child processes have exited
wait