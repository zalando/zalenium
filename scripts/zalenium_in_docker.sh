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

    echo "Starting Zalenium in docker..."

    docker run -d -ti --name zalenium -p 4444:4444 \
          -e SAUCE_USERNAME -e SAUCE_ACCESS_KEY \
          -v /tmp/videos:/home/seluser/videos \
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

function usage()
{
    echo "Usage:"
    echo ""
    echo "./zalenium.sh"
    echo -e "\t -h --help"
    echo -e "\t start <options, see below>"
    echo -e "\t --chromeContainers -> Number of Chrome containers created on startup. Default is 1 when parameter is absent."
    echo -e "\t --firefoxContainers -> Number of Firefox containers created on startup. Default is 1 when parameter is absent."
    echo -e "\t --maxDockerSeleniumContainers -> Max number of docker-selenium containers running at the same time. Default is 10 when parameter is absent."
    echo -e "\t --sauceLabsEnabled -> Determines if the Sauce Labs node is started. Defaults to 'true' when parameter absent."
    echo -e "\t stop"
    echo ""
    echo -e "\t Example: Starting Zalenium with 2 Chrome containers and without Sauce Labs"
    echo -e "\t ./zalenium.sh start --chromeContainers 2 --sauceLabsEnabled false"
}

case ${SCRIPT_ACTION} in
    start)
        StartUp
    ;;
    stop)
        ShutDown
        ;;
    *)
        usage
    ;;
esac
