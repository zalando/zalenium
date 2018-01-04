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
[ -z "${SAUCE_TUNNEL_MAX_RETRY_ATTEMPTS}" ] && die "Required SAUCE_TUNNEL_MAX_RETRY_ATTEMPTS"
[ -z "${SAUCE_USERNAME}" ] && die "Required env var SAUCE_USERNAME"
[ -z "${SAUCE_ACCESS_KEY}" ] && die "Required env var SAUCE_ACCESS_KEY"
[ -z "${SAUCE_TUNNEL_READY_FILE}" ] && die "Required SAUCE_TUNNEL_READY_FILE"
[ -z "${SAUCE_TUNNEL_ID}" ] && die "Required env var SAUCE_TUNNEL_ID"

# Wait for this process dependencies
# - none, the tunnel has no dependencies
# timeout --foreground ${WAIT_TIMEOUT} wait-xvfb.sh

# Do a smoke doctor run to make sure everything is ok and too keep
# for the logs
if [ "${SAUCE_TUNNEL_DOCTOR_TEST}" = "true" ]; then
  sc --doctor --user "${SAUCE_USERNAME}" --api-key "${SAUCE_ACCESS_KEY}"
fi

# Start tunnel
i=0
set +e
# for i in 1 2 3 4 5; do
until [ $i -ge $SAUCE_TUNNEL_MAX_RETRY_ATTEMPTS ]; do
  if [ $i -ge 1 ]; then
    echo "Failed attempt $i to start Sauce tunnel, will retry..."
    killall -SIGINT sc || true
    sleep 1
  fi
  SAUCE_DNS_COMMAND=""
  if [ ! -z "${SAUCE_TUNNEL_DNS}" ]; then
    SAUCE_DNS_COMMAND="--dns ${SAUCE_TUNNEL_DNS}"
  fi
  sc --se-port 4446 \
     --user "${SAUCE_USERNAME}" \
     --api-key "${SAUCE_ACCESS_KEY}" \
    ${SAUCE_DNS_COMMAND} \
     --readyfile "${SAUCE_TUNNEL_READY_FILE}" \
     --tunnel-identifier "${SAUCE_TUNNEL_ID}" > ${SAUCE_LOG_FILE} &
  SAUCE_TUNNEL_PID=$!
  (timeout --foreground ${SAUCE_WAIT_TIMEOUT} ./wait-saucelabs.sh) && break
  i=$[$i+1]
done
set -e #restore
if [ $i -ge $SAUCE_TUNNEL_MAX_RETRY_ATTEMPTS ]; then
  echo "Failed to start Sauce tunnel after $i attempts"
  kill -SIGINT ${SAUCE_TUNNEL_PID}
  killall -SIGINT sc || true
  wait ${SAUCE_TUNNEL_PID}
  echo "Will run in doctor mode to get more feedback on why it failed"
  sc --doctor --user "${SAUCE_USERNAME}" --api-key "${SAUCE_ACCESS_KEY}"
  exit 1
fi

function shutdown {
  echo "Trapped SIGTERM/SIGINT so shutting down Sauce Labs gracefully..."
  kill -SIGINT ${SAUCE_TUNNEL_PID}
  wait ${SAUCE_TUNNEL_PID}
  echo "Sauce Labs tunnel shutdown complete."
  exit 0
}

# Wait for the file to exists
echo "Waiting for file ${SAUCE_TUNNEL_READY_FILE} to be created..."
while ! ls -l "${SAUCE_TUNNEL_READY_FILE}" >/dev/null 2>&1; do sleep 0.1; done

# Run function shutdown() when this process receives a killing signal
trap shutdown SIGTERM SIGINT SIGKILL

# tells bash to wait until child processes have exited
wait