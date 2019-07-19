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

export DONE_MSG="Secure connection established, you may start your tests now"
export GOODBYE_MSG="GoodBye."

if [ "${LT_TUNNEL}" = "true" ]; then
  echo "Waiting for LambdaTest tunnel to start..."
  # Required params
  [ -z "${LT_LOG_FILE}" ] && die "Required env var LT_LOG_FILE"
  while ! grep -s "${DONE_MSG}" ${LT_LOG_FILE} >/dev/null; do
    if grep "${GOODBYE_MSG}" ${LT_LOG_FILE} >/dev/null; then
      die "Found GOODBYE_MSG '${GOODBYE_MSG}' in output so quitting."
    fi
    echo -n '.'
    sleep 0.2;
  done
  echo "LambdaTest tunnel started! (wait-lambdatest.sh)"
else
  echo "Won't start LambdaTest tunnel due to LT_TUNNEL false"
fi