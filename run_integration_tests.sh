#!/bin/sh
# Exit on failure
set -e

INTEGRATION_TO_TEST=$1

IN_TRAVIS="${CI:=false}"
VIDEOS_FOLDER=$(pwd)/target/videos
if [ "${IN_TRAVIS}" = "true" ]; then
    VIDEOS_FOLDER=/tmp/videos
fi
echo ${VIDEOS_FOLDER}

if [ "$TRAVIS_PULL_REQUEST" = "false" ] && [ -n "${TRAVIS_TAG}" ] && [ "${TRAVIS_TAG}" != "latest" ]; then
    echo "TRAVIS_TAG=${TRAVIS_TAG}"
	echo "Not running integration tests when a TAG is set, we assume they already ran in the PR."
else
    # If the environment var exists, then we run the integration tests. This is to allow external PRs ro tun
    if [ "$INTEGRATION_TO_TEST" = sauceLabs ]; then
        if [ -n "${SAUCE_USERNAME}" ]; then
            rm ${VIDEOS_FOLDER}/*.flv
            rm ${VIDEOS_FOLDER}/*.mp4
            mvn clean verify -Pintegration-test -DthreadCountProperty=2 -Dskip.surefire.tests=true -DintegrationToTest=${INTEGRATION_TO_TEST}
            # Check for generated videos
            ls -la ${VIDEOS_FOLDER}/saucelabs*.flv || (echo "No Sauce Labs videos were downloaded." && exit 2)
            ls -la ${VIDEOS_FOLDER}/zalenium*.mp4 || (echo "No Zalenium videos were generated." && exit 2)
        fi
    fi
    if [ "$INTEGRATION_TO_TEST" = browserStack ]; then
        if [ -n "${BROWSER_STACK_USER}" ]; then
            rm ${VIDEOS_FOLDER}/*.mp4
            mvn clean verify -Pintegration-test -DthreadCountProperty=2 -Dskip.surefire.tests=true -DintegrationToTest=${INTEGRATION_TO_TEST}
            # Check for generated videos
            ls -la ${VIDEOS_FOLDER}/browserstack*.mp4 || (echo "No BrowserStack videos were downloaded." && exit 2)
            ls -la ${VIDEOS_FOLDER}/zalenium*.mp4 || (echo "No Zalenium videos were generated." && exit 2)
        fi
    fi
    if [ "$INTEGRATION_TO_TEST" = testingBot ]; then
        if [ -n "${TESTINGBOT_KEY}" ]; then
            rm ${VIDEOS_FOLDER}/*.mp4
            mvn clean verify -Pintegration-test -DthreadCountProperty=2 -Dskip.surefire.tests=true -DintegrationToTest=${INTEGRATION_TO_TEST}
            # Check for generated videos
            ls -la ${VIDEOS_FOLDER}/testingbot*.mp4 || (echo "No TestingBot videos were downloaded." && exit 2)
            ls -la ${VIDEOS_FOLDER}/zalenium*.mp4 || (echo "No Zalenium videos were generated." && exit 2)
        fi
    fi
fi

