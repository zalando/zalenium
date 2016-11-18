#!/usr/bin/env bash

SCRIPT_ACTION=$1
ZALENIUM_DOCKER_IMAGE=$2

StartUp()
{
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

    SAUCE_USERNAME="${SAUCE_USERNAME:=abc}"
    SAUCE_ACCESS_KEY="${SAUCE_ACCESS_KEY:=abc}"

    if [ "$SAUCE_USERNAME" = abc ]; then
        echo "SAUCE_USERNAME environment variable is not set, cannot start Sauce Labs node, exiting..."
        exit 1
    fi

    if [ "$SAUCE_ACCESS_KEY" = abc ]; then
        echo "SAUCE_ACCESS_KEY environment variable is not set, cannot start Sauce Labs node, exiting..."
        exit 1
    fi

    echo "Starting Zalenium in docker..."

    IN_TRAVIS="${CI:=false}"
    VIDEOS_FOLDER=${project.build.directory}/videos
    if [ "${IN_TRAVIS}" = "true" ]; then
        VIDEOS_FOLDER=/tmp/videos
    fi

    docker run -d -ti --name zalenium -p 4444:4444 -p 5555:5555 \
          -e SAUCE_USERNAME -e SAUCE_ACCESS_KEY \
          -v ${VIDEOS_FOLDER}:/home/seluser/videos \
          -v /var/run/docker.sock:/var/run/docker.sock \
          ${ZALENIUM_DOCKER_IMAGE} start

    sleep 20

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
