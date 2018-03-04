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

export DONE_MSG="Sauce Connect is up, you may start your tests."
export GOODBYE_MSG="Goodbye."

if [ "${SAUCE_TUNNEL}" = "true" ]; then
  echo "Waiting for Sauce Labs tunnel to start..."
  # Required params
  [ -z "${SAUCE_LOG_FILE}" ] && die "Required env var SAUCE_LOG_FILE"
  while ! grep -s "${DONE_MSG}" ${SAUCE_LOG_FILE} >/dev/null; do
    if grep "${GOODBYE_MSG}" ${SAUCE_LOG_FILE} >/dev/null; then
      cat /home/seluser/logs/saucelabs-* 2>&1
      die "Found GOODBYE_MSG '${GOODBYE_MSG}' in output so quitting."
    fi
    echo -n '.'
    sleep 0.2;
  done
  echo "Sauce Labs tunnel started! (wait-saucelabs.sh)"
else
  echo "Won't start Sauce Labs tunnel due to SAUCE_TUNNEL false"
fi