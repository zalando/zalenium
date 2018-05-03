#!/usr/bin/env bash

SCRIPT_ACTION=$1
ZALENIUM_DOCKER_IMAGE=$2
INTEGRATION_TO_TEST=$3

# In OSX install gtimeout through `brew install coreutils`
function mtimeout() {
    if [ "$(uname -s)" = 'Darwin' ]; then
        gtimeout "$@"
    else
        timeout "$@"
    fi
}

# Actively waits for Zalenium to fully starts
# you can copy paste this in your Jenkins scripts
WaitZaleniumStarted()
{
    DONE_MSG="Zalenium is now ready!"
    while ! docker logs zalenium | grep "${DONE_MSG}" >/dev/null; do
        echo -n '.'
        sleep 0.2
    done
}
export -f WaitZaleniumStarted

StartUp()
{
    HOST_OPTIONS=""
    if [ "$(uname -s)" != 'Darwin' ]; then
        HOST_OPTIONS="-e HOST_UID=\"$(id -u)\" -e HOST_GID=\"$(id -g)\""
    fi

    DOCKER_SELENIUM_IMAGE_COUNT=$(docker images | grep "elgalu/selenium" | wc -l)
    if [ ${DOCKER_SELENIUM_IMAGE_COUNT} -eq 0 ]; then
        echo "Seems that docker-selenium's image has not been downloaded yet, please run 'docker pull elgalu/selenium' first"
        exit 1
    fi

    CONTAINERS=$(docker ps -a -f name=zalenium -q | wc -l)
    if [ ${CONTAINERS} -gt 0 ]; then
        echo "Removing exited docker-selenium containers..."
        docker rm -f $(docker ps -a -f name=zalenium -q)
    fi

    VIDEOS_FOLDER=${project.build.directory}/videos
    mkdir -p "${VIDEOS_FOLDER}"

    if [ "$INTEGRATION_TO_TEST" = sauceLabs ]; then
        echo "Starting Zalenium in docker with Sauce Labs..."
        SAUCE_USERNAME="${SAUCE_USERNAME:=abc}"
        SAUCE_ACCESS_KEY="${SAUCE_ACCESS_KEY:=abc}"

        if [ "$SAUCE_USERNAME" = abc ]; then
            echo "SAUCE_USERNAME environment variable is not set, cannot start Sauce Labs node, exiting..."
            exit 2
        fi

        if [ "$SAUCE_ACCESS_KEY" = abc ]; then
            echo "SAUCE_ACCESS_KEY environment variable is not set, cannot start Sauce Labs node, exiting..."
            exit 3
        fi

        docker run -d -ti --name zalenium -p 4444:4444 \
              ${HOST_OPTIONS} \
              -e SAUCE_USERNAME -e SAUCE_ACCESS_KEY \
              -v ${VIDEOS_FOLDER}:/home/seluser/videos \
              -v /var/run/docker.sock:/var/run/docker.sock \
              ${ZALENIUM_DOCKER_IMAGE} start --sauceLabsEnabled true --startTunnel true
    fi

    if [ "$INTEGRATION_TO_TEST" = browserStack ]; then
        echo "Starting Zalenium in docker with BrowserStack..."
        BROWSER_STACK_USER="${BROWSER_STACK_USER:=abc}"
        BROWSER_STACK_KEY="${BROWSER_STACK_KEY:=abc}"

        if [ "$BROWSER_STACK_USER" = abc ]; then
            echo "BROWSER_STACK_USER environment variable is not set, cannot start Browser Stack node, exiting..."
            exit 4
        fi

        if [ "$BROWSER_STACK_KEY" = abc ]; then
            echo "BROWSER_STACK_KEY environment variable is not set, cannot start Browser Stack node, exiting..."
            exit 5
        fi

        docker run -d -ti --name zalenium -p 4444:4444 \
              ${HOST_OPTIONS} \
              -e BROWSER_STACK_USER -e BROWSER_STACK_KEY \
              -v ${VIDEOS_FOLDER}:/home/seluser/videos \
              -v /var/run/docker.sock:/var/run/docker.sock \
              ${ZALENIUM_DOCKER_IMAGE} start --browserStackEnabled true --startTunnel true
    fi

    if [ "$INTEGRATION_TO_TEST" = testingBot ]; then
        echo "Starting Zalenium in docker with TestingBot..."
        TESTINGBOT_KEY="${TESTINGBOT_KEY:=abc}"
        TESTINGBOT_SECRET="${TESTINGBOT_SECRET:=abc}"

        if [ "$TESTINGBOT_KEY" = abc ]; then
            echo "TESTINGBOT_KEY environment variable is not set, cannot start TestingBot node, exiting..."
            exit 6
        fi

        if [ "$TESTINGBOT_SECRET" = abc ]; then
            echo "TESTINGBOT_SECRET environment variable is not set, cannot start TestingBot node, exiting..."
            exit 7
        fi

        docker run -d -ti --name zalenium -p 4444:4444 \
              ${HOST_OPTIONS} \
              -e TESTINGBOT_KEY -e TESTINGBOT_SECRET \
              -v ${VIDEOS_FOLDER}:/home/seluser/videos \
              -v /var/run/docker.sock:/var/run/docker.sock \
              ${ZALENIUM_DOCKER_IMAGE} start --testingBotEnabled true --startTunnel true
    fi

    if ! mtimeout --foreground "2m" bash -c WaitZaleniumStarted; then
        echo "Zalenium failed to start after 2 minutes, failing..."
        exit 8
    fi

    echo "Zalenium in docker started!"
}

ShutDown()
{
    docker stop zalenium
    docker rm zalenium
}

case ${SCRIPT_ACTION} in
    start)
        StartUp
    ;;
    stop)
        ShutDown
    ;;
esac
