#!/usr/bin/env bash

CHROME_CONTAINERS=1
FIREFOX_CONTAINERS=1
MAX_DOCKER_SELENIUM_CONTAINERS=10
SELENIUM_ARTIFACT="$(pwd)/selenium-server-standalone-${selenium-server.major-minor.version}.${selenium-server.patch-level.version}.jar"
ZALENIUM_ARTIFACT="$(pwd)/${project.build.finalName}.jar"
SAUCE_LABS_ENABLED=true
VIDEO_RECORDING_ENABLED=true
SCREEN_WIDTH=1900
SCREEN_HEIGHT=1880
TZ="Europe/Berlin"

PID_PATH_SELENIUM=/tmp/selenium-pid
PID_PATH_DOCKER_SELENIUM_NODE=/tmp/docker-selenium-node-pid
PID_PATH_SAUCE_LABS_NODE=/tmp/sauce-labs-node-pid

WaitSeleniumHub()
{
    # Other option is to wait for certain text at
    #  logs/stdout.zalenium.hub.log
    while ! curl -sSL "http://localhost:4444/wd/hub/status" 2>&1 \
            | jq -r '.status' 2>&1 | grep "13" >/dev/null; do
        echo -n '.'
        sleep 0.2
    done
}
export -f WaitSeleniumHub

WaitStarterProxy()
{
    # Other option is to wait for certain text at
    #  logs/stdout.zalenium.docker.node.log
    while ! curl -sSL "http://localhost:30000/wd/hub/status" 2>&1 \
            | jq -r '.state' 2>&1 | grep "success" >/dev/null; do
        echo -n '.'
        sleep 0.2
    done
}
export -f WaitStarterProxy

WaitSauceLabsProxy()
{
    # Wait for the sauce node success
    while ! curl -sSL "http://localhost:30001/wd/hub/status" 2>&1 \
            | jq -r '.state' 2>&1 | grep "success" >/dev/null; do
        echo -n '.'
        sleep 0.2
    done

    # Also wait for the sauce url though this is optional
    DONE_MSG="ondemand.saucelabs.com"
    while ! docker logs zalenium | grep "${DONE_MSG}" >/dev/null; do
        echo -n '.'
        sleep 0.2
    done
}
export -f WaitSauceLabsProxy

EnsureCleanEnv()
{
    CONTAINERS=$(docker ps -a -f name=zalenium_ -q | wc -l)
    if [ ${CONTAINERS} -gt 0 ]; then
        echo "Removing exited docker-selenium containers..."
        docker rm -f $(docker ps -a -f name=zalenium_ -q)
    fi
}

EnsureDockerWorks()
{
    if ! docker images elgalu/selenium >/dev/null; then
        echo "Docker seems to be not working properly, check the above error."
        exit 1
    fi
}

DockerTerminate()
{
  echo "Trapped SIGTERM/SIGINT so shutting down Zalenium gracefully..."
  ShutDown
  wait
  exit 0
}

# Run function DockerTerminate() when this process receives a killing signal
trap DockerTerminate SIGTERM SIGINT SIGKILL

StartUp()
{
    EnsureDockerWorks
    EnsureCleanEnv

    DOCKER_SELENIUM_IMAGE_COUNT=$(docker images | grep "elgalu/selenium" | wc -l)
    if [ ${DOCKER_SELENIUM_IMAGE_COUNT} -eq 0 ]; then
        echo "Seems that docker-selenium's image has not been downloaded yet, please run 'docker pull elgalu/selenium' first"
        exit 1
    fi

    if [ ! -f ${SELENIUM_ARTIFACT} ];
    then
        echo "Selenium JAR not present, exiting start script."
        exit 2
    fi

    if [ ! -f ${ZALENIUM_ARTIFACT} ];
    then
        echo "Zalenium JAR not present, exiting start script."
        exit 3
    fi

    if ! which nginx >/dev/null; then
        echo "Nginx reverse proxy not installed, quitting."
        exit 4
    fi

    if [ -z ${SAUCE_LABS_ENABLED} ]; then
        SAUCE_LABS_ENABLED=true
    fi

    if [ "$SAUCE_LABS_ENABLED" = true ]; then
        SAUCE_USERNAME="${SAUCE_USERNAME:=abc}"
        SAUCE_ACCESS_KEY="${SAUCE_ACCESS_KEY:=abc}"

        if [ "$SAUCE_USERNAME" = abc ]; then
            echo "SAUCE_USERNAME environment variable is not set, cannot start Sauce Labs node, exiting..."
            exit 5
        fi

        if [ "$SAUCE_ACCESS_KEY" = abc ]; then
            echo "SAUCE_ACCESS_KEY environment variable is not set, cannot start Sauce Labs node, exiting..."
            exit 6
        fi
    fi

    export ZALENIUM_CHROME_CONTAINERS=${CHROME_CONTAINERS}
    export ZALENIUM_FIREFOX_CONTAINERS=${FIREFOX_CONTAINERS}
    export ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS=${MAX_DOCKER_SELENIUM_CONTAINERS}
    export ZALENIUM_VIDEO_RECORDING_ENABLED=${VIDEO_RECORDING_ENABLED}
    export ZALENIUM_TZ=${TZ}
    export ZALENIUM_SCREEN_WIDTH=${SCREEN_WIDTH}
    export ZALENIUM_SCREEN_HEIGHT=${SCREEN_HEIGHT}

    echo "Starting Nginx reverse proxy..."
    nginx

    echo "Starting Selenium Hub..."

    mkdir -p logs

    java -cp ${SELENIUM_ARTIFACT}:${ZALENIUM_ARTIFACT} org.openqa.grid.selenium.GridLauncher \
    -role hub -servlets de.zalando.tip.zalenium.servlet.live \
    -throwOnCapabilityNotPresent true > logs/stdout.zalenium.hub.log &
    echo $! > ${PID_PATH_SELENIUM}

    if ! timeout --foreground "1m" bash -c WaitSeleniumHub; then
        echo "GridLauncher failed to start after 1 minute, failing..."
        curl "http://localhost:4444/wd/hub/status"
        exit 11
    fi
    echo "Selenium Hub started!"

    echo "Starting DockerSeleniumStarter node..."

    java -jar ${SELENIUM_ARTIFACT} -role node -hub http://localhost:4444/grid/register \
     -proxy de.zalando.tip.zalenium.proxy.DockerSeleniumStarterRemoteProxy \
     -port 30000 > logs/stdout.zalenium.docker.node.log &
    echo $! > ${PID_PATH_DOCKER_SELENIUM_NODE}

    if ! timeout --foreground "30s" bash -c WaitStarterProxy; then
        echo "StarterRemoteProxy failed to start after 30 seconds, failing..."
        exit 12
    fi
    echo "DockerSeleniumStarter node started!"

    if ! curl -sSL "http://localhost:4444" | grep Grid >/dev/null; then
        echo "Error: The Grid is not listening at port 4444"
        exit 7
    fi

    if ! curl -sSL "http://localhost:5555/proxy/4444/" | grep Grid >/dev/null; then
        echo "Error: Nginx is not redirecting to the grid"
        exit 8
    fi

    if [ "$SAUCE_LABS_ENABLED" = true ]; then
        echo "Starting Sauce Labs node..."
        java -jar ${SELENIUM_ARTIFACT} -role node -hub http://localhost:4444/grid/register \
         -proxy de.zalando.tip.zalenium.proxy.SauceLabsRemoteProxy \
         -port 30001 > logs/stdout.zalenium.sauce.node.log &
        echo $! > ${PID_PATH_SAUCE_LABS_NODE}

        if ! timeout --foreground "40s" bash -c WaitSauceLabsProxy; then
            echo "SauceLabsRemoteProxy failed to start after 40 seconds, failing..."
            exit 12
        fi
        echo "Sauce Labs node started!"
    else
        echo "Sauce Labs not enabled..."
    fi

    echo "Zalenium is now ready!"

    # When running in docker do not exit this script
    wait
}

ShutDown()
{

    if [ -f ${PID_PATH_SELENIUM} ];
    then
        echo "Stopping Hub..."
        PID=$(cat ${PID_PATH_SELENIUM});
        kill ${PID};
        _returnedValue=$?
        if [ ${_returnedValue} -ne 0 ] ; then
            echo "Failed to send kill signal to Selenium Hub!"
        else
            rm ${PID_PATH_SELENIUM}
        fi
    fi

    if [ -f ${PID_PATH_DOCKER_SELENIUM_NODE} ];
    then
        echo "Stopping DockerSeleniumStarter node..."
        PID=$(cat ${PID_PATH_DOCKER_SELENIUM_NODE});
        kill ${PID};
        if [ ${_returnedValue} -ne 0 ] ; then
            echo "Failed to send kill signal to DockerSeleniumStarter node!"
        else
            rm ${PID_PATH_DOCKER_SELENIUM_NODE}
        fi
    fi

    if [ -f ${PID_PATH_SAUCE_LABS_NODE} ];
    then
        echo "Stopping Sauce Labs node..."
        PID=$(cat ${PID_PATH_SAUCE_LABS_NODE});
        kill ${PID};
        if [ ${_returnedValue} -ne 0 ] ; then
            echo "Failed to send kill signal to Sauce Labs node!"
        else
            rm ${PID_PATH_SAUCE_LABS_NODE}
        fi
    fi

    EnsureCleanEnv
}

function usage()
{
    echo "Usage:"
    echo ""
    echo "zalenium"
    echo -e "\t -h --help"
    echo -e "\t start <options, see below>"
    echo -e "\t --chromeContainers -> Number of Chrome containers created on startup. Default is 1 when parameter is absent."
    echo -e "\t --firefoxContainers -> Number of Firefox containers created on startup. Default is 1 when parameter is absent."
    echo -e "\t --maxDockerSeleniumContainers -> Max number of docker-selenium containers running at the same time. Default is 10 when parameter is absent."
    echo -e "\t --sauceLabsEnabled -> Determines if the Sauce Labs node is started. Defaults to 'true' when parameter absent."
    echo -e "\t --videoRecordingEnabled -> Sets if video is recorded in every test. Defaults to 'true' when parameter absent."
    echo -e "\t --screenWidth -> Sets the screen width. Defaults to 1900"
    echo -e "\t --screenHeight -> Sets the screen height. Defaults to 1880"
    echo -e "\t --timeZone -> Sets the time zone in the containers. Defaults to \"Europe/Berlin\""
    echo ""
    echo -e "\t stop"
    echo ""
    echo -e "\t Examples:"
    echo -e "\t - Starting Zalenium with 2 Chrome containers and without Sauce Labs"
    echo -e "\t start --chromeContainers 2 --sauceLabsEnabled false"
    echo -e "\t - Starting Zalenium screen width 1440 and height 810, time zone \"America/Montreal\""
    echo -e "\t start --screenWidth 1440 --screenHeight 810 --timeZone \"America/Montreal\""
}

SCRIPT_ACTION=$1
shift
case ${SCRIPT_ACTION} in
    start)
        NUM_PARAMETERS=$#
        if [ $((NUM_PARAMETERS % 2)) -ne 0 ]; then
            echo "Uneven amount of parameters entered, please check your input."
            usage
            exit 9
        fi
        while [ "$1" != "" ]; do
            PARAM=$(echo $1)
            VALUE=$(echo $2)
            case ${PARAM} in
                -h | --help)
                    usage
                    exit
                    ;;
                --chromeContainers)
                    CHROME_CONTAINERS=${VALUE}
                    ;;
                --firefoxContainers)
                    FIREFOX_CONTAINERS=${VALUE}
                    ;;
                --maxDockerSeleniumContainers)
                    MAX_DOCKER_SELENIUM_CONTAINERS=${VALUE}
                    ;;
                --sauceLabsEnabled)
                    SAUCE_LABS_ENABLED=${VALUE}
                    ;;
                --videoRecordingEnabled)
                    VIDEO_RECORDING_ENABLED=${VALUE}
                    ;;
                --screenWidth)
                    SCREEN_WIDTH=${VALUE}
                    ;;
                --screenHeight)
                    SCREEN_HEIGHT=${VALUE}
                    ;;
                --timeZone)
                    TZ=${VALUE}
                    ;;
                *)
                    echo "ERROR: unknown parameter \"$PARAM\""
                    usage
                    exit 10
                    ;;
            esac
            shift 2
        done

        StartUp
    ;;
    stop)
        ShutDown
        ;;
    *)
        usage
    ;;
esac
