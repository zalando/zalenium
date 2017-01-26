#!/bin/sh
# Exit on failure
set -e

INTEGRATION_TO_TEST=$1

# If the environment var exists, then we run the integration tests. This is to allow external PRs ro tun
if [ "$INTEGRATION_TO_TEST" = sauceLabs ]; then
    if [ -n "${SAUCE_USERNAME}" ]; then
        mvn clean verify -Pintegration-test -DthreadCountProperty=2 -Dskip.surefire.tests=true -DintegrationToTest=${INTEGRATION_TO_TEST}
    fi
fi
if [ "$INTEGRATION_TO_TEST" = browserStack ]; then
    if [ -n "${BROWSER_STACK_USER}" ]; then
        mvn clean verify -Pintegration-test -DthreadCountProperty=2 -Dskip.surefire.tests=true -DintegrationToTest=${INTEGRATION_TO_TEST}
    fi
fi
if [ "$INTEGRATION_TO_TEST" = testingBot ]; then
    if [ -n "${TESTINGBOT_KEY}" ]; then
        mvn clean verify -Pintegration-test -DthreadCountProperty=2 -Dskip.surefire.tests=true -DintegrationToTest=${INTEGRATION_TO_TEST}
    fi
fi
