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
[ -z "${LT_USERNAME}" ] && die "Required env var LT_USERNAME"
[ -z "${LT_ACCESS_KEY}" ] && die "Required env var LT_ACCESS_KEY"
[ -z "${LT_TUNNEL_ID}" ] && die "Required env var LT_TUNNEL_ID"


# Start tunnel
LT \
  --user ${LT_USERNAME} \
  --key ${LT_ACCESS_KEY} \
  --tunnelName ${LT_TUNNEL_ID} > ${LT_LOG_FILE} &
LT_TUNNEL_PID=$!



#kills the tunnel through the process id
function shutdown {
  echo "Trapped SIGTERM/SIGINT so shutting down LambdaTest gracefully..."
  kill -SIGTERM ${LT_TUNNEL_PID}
  wait ${LT_TUNNEL_PID}
  echo "LambdaTest tunnel shutdown complete."
  exit 0
}

# Run function shutdown() when this process receives a killing signal
trap shutdown SIGTERM SIGINT SIGKILL

# tells bash to wait until child processes have exited
wait
