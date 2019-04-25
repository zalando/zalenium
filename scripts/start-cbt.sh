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
[ -z "${CBT_USERNAME}" ] && die "Required env var CBT_USERNAME"
[ -z "${CBT_AUTHKEY}" ] && die "Required env var CBT_AUTHKEY"
[ -z "${CBT_TUNNEL_ID}" ] && die "Required env var CBT_TUNNEL_ID"


# Start tunnel
cbt_tunnels-linux-x64 \
  --username ${CBT_USERNAME} \
  --authkey ${CBT_AUTHKEY} \
  --tunnelname ${CBT_TUNNEL_ID} > ${CBT_LOG_FILE} &
CBT_TUNNEL_PID=$!



#kills the tunnel through the process id
function shutdown {
  echo "Trapped SIGTERM/SIGINT so shutting down CBT gracefully..."
  kill -SIGTERM ${CBT_TUNNEL_PID}
  wait ${CBT_TUNNEL_PID}
  echo "CBT tunnel shutdown complete."
  exit 0
}

# Run function shutdown() when this process receives a killing signal
trap shutdown SIGTERM SIGINT SIGKILL

# tells bash to wait until child processes have exited
wait
