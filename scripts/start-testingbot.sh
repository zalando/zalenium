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
[ -z "${TESTINGBOT_KEY}" ] && die "Required env var TESTINGBOT_KEY"
[ -z "${TESTINGBOT_SECRET}" ] && die "Required env var TESTINGBOT_SECRET"
[ -z "${TESTINGBOT_TUNNEL_OPTS}" ] && die "Required env var TESTINGBOT_TUNNEL_OPTS"

# Wait for this process dependencies
# - none, the tunnel has no dependencies
# timeout --foreground ${WAIT_TIMEOUT} wait-xvfb.sh

# Start tunnel
java -jar /usr/local/bin/testingbot-tunnel.jar \
  ${TESTINGBOT_KEY} \
  ${TESTINGBOT_SECRET} \
  --se-port 4447 ${TESTINGBOT_TUNNEL_OPTS} > ${TESTINGBOT_LOG_FILE} 2>&1 &
TESTINGBOT_TUNNEL_PID=$!

function shutdown {
  echo "Trapped SIGTERM/SIGINT so shutting down TestingBot gracefully..."
  kill -SIGTERM ${TESTINGBOT_TUNNEL_PID}
  wait ${TESTINGBOT_TUNNEL_PID}
  echo "TestingBot tunnel shutdown complete."
  exit 0
}

# Run function shutdown() when this process receives a killing signal
trap shutdown SIGTERM SIGINT SIGKILL

# tells bash to wait until child processes have exited
wait