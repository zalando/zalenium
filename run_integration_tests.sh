#!/bin/sh
# Exit on failure
set -e

# If the environment var exists, then we run the integration tests. This is to allow external PRs ro tun
if [ -n "${SAUCE_USERNAME}" ]; then
    mvn clean verify -Pintegration-test -DthreadCountProperty=2 -Dskip.surefire.tests=true
fi
