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

export DONE_MSG="You can now access your local server(s) in our remote browser"

if [ "${BROWSER_STACK_TUNNEL}" = "true" ]; then
  echo "Waiting for BrowserStack tunnel to start..."
  # Required params
  [ -z "${BROWSER_STACK_LOG_FILE}" ] && die "Required env var BROWSER_STACK_LOG_FILE"
  while ! grep -s "${DONE_MSG}" ${BROWSER_STACK_LOG_FILE} >/dev/null; do
    echo -n '.'
    sleep 0.2;
  done
  echo "BrowserStack tunnel started! (wait-browserstack.sh)"
else
  echo "Won't start BrowserStack tunnel due to BROWSER_STACK_TUNNEL false"
fi
