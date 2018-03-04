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

export DONE_MSG="You may start your tests"

if [ "${TESTINGBOT_TUNNEL}" = "true" ]; then
  echo "Waiting for TestingBot tunnel to start..."
  # Required params
  [ -z "${TESTINGBOT_LOG_FILE}" ] && die "Required env var TESTINGBOT_LOG_FILE"
  while ! grep -s "${DONE_MSG}" ${TESTINGBOT_LOG_FILE} >/dev/null; do
    echo -n '.'
    sleep 0.2;
  done
  echo "TestingBot tunnel started! (wait-testingbot.sh)"
else
  echo "Won't start TestingBot tunnel due to TESTINGBOT_TUNNEL false"
fi