#!/bin/sh
# Exit on failure
set -e

# If the environment var exists, then we run the integration tests. This is to allow external PRs ro tun
if [ -n "${SAUCE_USERNAME}" ]; then
    mvn clean verify -Pintegration-test -DthreadCountProperty=2 -Dskip.surefire.tests=true -DintegrationToTest=sauceLabs
fi
if [ -n "${BROWSER_STACK_USER}" ]; then
    mvn clean verify -Pintegration-test -DthreadCountProperty=2 -Dskip.surefire.tests=true -DintegrationToTest=browserStack
fi
if [ -n "${TESTINGBOT_KEY}" ]; then
    mvn clean verify -Pintegration-test -DthreadCountProperty=2 -Dskip.surefire.tests=true -DintegrationToTest=testingBot
fi
