#!/usr/bin/env bash

CHROME_CONTAINERS=1
FIREFOX_CONTAINERS=1
MAX_DOCKER_SELENIUM_CONTAINERS=10
SELENIUM_ARTIFACT="$(pwd)/selenium-server-standalone-2.53.1.jar"
ZALENIUM_ARTIFACT="$(pwd)/${project.build.finalName}.jar"
SAUCE_LABS_ENABLED=true

PID_PATH_SELENIUM=/tmp/selenium-pid
PID_PATH_DOCKER_SELENIUM_NODE=/tmp/docker-selenium-node-pid
PID_PATH_SAUCE_LABS_NODE=/tmp/sauce-labs-node-pid

# Omit output only when running outsided of dockerized Zalenium
if [ "${DOCKER_ALONGSIDE_DOCKER}" != "true" ]; then
    export NO_HUP="nohup"
    export REDIRECT_OUTPUT="2>&1 </dev/null"
fi

DockerTerminate()
{
  echo "Trapped SIGTERM/SIGINT so shutting down Zalenium gracefully..."
  ShutDown
  [ "${DOCKER_ALONGSIDE_DOCKER}" = "true" ] && wait
  exit 0
}

# Run function DockerTerminate() when this process receives a killing signal
trap DockerTerminate SIGTERM SIGINT SIGKILL

StartUp()
{

    DOCKER_SELENIUM_IMAGE_COUNT=$(docker images | grep "elgalu/selenium" | wc -l)
    if [ ${DOCKER_SELENIUM_IMAGE_COUNT} -eq 0 ]; then
        echo "Seems that docker-selenium's image has not been downloaded yet, please run 'docker pull elgalu/selenium' first"
        exit 1
    fi

    if ! [[ ${CHROME_CONTAINERS} =~ ^-?[0-9]+$ ]]; then
        echo "Parameter --chromeContainers must be an integer. Exiting..."
        exit 1
    fi

    if ! [[ ${FIREFOX_CONTAINERS} =~ ^-?[0-9]+$ ]]; then
        echo "Parameter --firefoxContainers must be an integer. Exiting..."
        exit 1
    fi

    if ! [[ ${MAX_DOCKER_SELENIUM_CONTAINERS} =~ ^-?[0-9]+$ ]]; then
        echo "Parameter --maxDockerSeleniumContainers must be an integer. Exiting..."
        exit 1
    fi

    if [ ! -f ${SELENIUM_ARTIFACT} ];
    then
        echo "Selenium JAR not present, exiting start script."
        exit 1
    fi

    if [ ! -f ${ZALENIUM_ARTIFACT} ];
    then
        echo "Zalenium JAR not present, exiting start script."
        exit 1
    fi

    if [ -z ${SAUCE_LABS_ENABLED} ]; then
        SAUCE_LABS_ENABLED=true
    fi

    if [ "$SAUCE_LABS_ENABLED" = true ]; then
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
    fi

    export ZALENIUM_CHROME_CONTAINERS=${CHROME_CONTAINERS}
    export ZALENIUM_FIREFOX_CONTAINERS=${FIREFOX_CONTAINERS}
    export ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS=${MAX_DOCKER_SELENIUM_CONTAINERS}

    echo "Starting Selenium Hub..."

    mkdir -p logs
    rm logs/*hub*.log
    ${NO_HUP} java -cp ${SELENIUM_ARTIFACT}:${ZALENIUM_ARTIFACT} org.openqa.grid.selenium.GridLauncher \
    -role hub -throwOnCapabilityNotPresent true > logs/stdout.zalenium.hub.log ${REDIRECT_OUTPUT} &
    echo $! > ${PID_PATH_SELENIUM}

    IN_TRAVIS="${CI:=false}"
    if [ "${IN_TRAVIS}" = "true" ]; then
        sleep 20
    else
        sleep 1
    fi
    echo "Selenium Hub started!"

    rm logs/*node*.log

    echo "Starting DockerSeleniumStarter node..."

    ${NO_HUP} java -jar ${SELENIUM_ARTIFACT} -role node -hub http://localhost:4444/grid/register \
     -proxy de.zalando.tip.zalenium.proxy.DockerSeleniumStarterRemoteProxy \
     -port 30000 > logs/stdout.zalenium.docker.node.log ${REDIRECT_OUTPUT} &
    echo $! > ${PID_PATH_DOCKER_SELENIUM_NODE}

    if [ "${IN_TRAVIS}" = "true" ]; then
        sleep 20
    else
        sleep 10
    fi
    echo "DockerSeleniumStarter node started!"

    if [ "$SAUCE_LABS_ENABLED" = true ]; then
        echo "Starting Sauce Labs node..."
        ${NO_HUP} java -jar ${SELENIUM_ARTIFACT} -role node -hub http://localhost:4444/grid/register \
         -proxy de.zalando.tip.zalenium.proxy.SauceLabsRemoteProxy \
         -port 30001 > logs/stdout.zalenium.sauce.node.log ${REDIRECT_OUTPUT} &
        echo $! > ${PID_PATH_SAUCE_LABS_NODE}

        if [ "${IN_TRAVIS}" = "true" ]; then
            sleep 20
        else
            sleep 2
        fi
        echo "Sauce Labs node started!"
    else
        echo "Sauce Labs not enabled..."
    fi

    # When running in docker do not exit this script
    [ "${DOCKER_ALONGSIDE_DOCKER}" = "true" ] && wait
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

    CONTAINERS=$(docker ps -a -f ancestor=elgalu/selenium -q | wc -l)
    if [ ${CONTAINERS} -gt 0 ]; then
        echo "Removing exited docker-selenium containers..."
        docker rm -f $(docker ps -a -f ancestor=elgalu/selenium -q)
    fi
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
    echo -e "\t --seleniumArtifact -> Absolute path of the Selenium JAR. If parameter absent, the JAR is expected to be in the same folder."
    echo -e "\t --zaleniumArtifact -> Absolute path of the Zalenium JAR. If parameter absent, the JAR is expected to be in the same folder."
    echo -e "\t --sauceLabsEnabled -> Determines if the Sauce Labs node is started. Defaults to 'true' when parameter absent."
    echo -e "\t stop"
    echo ""
    echo -e "\t Example: Starting Zalenium with 2 Chrome containers and without Sauce Labs"
    echo -e "\t ./zalenium.sh start --chromeContainers 2 --sauceLabsEnabled false"
}

SCRIPT_ACTION=$1
shift
case ${SCRIPT_ACTION} in
    start)
        NUM_PARAMETERS=$#
        if [ $((NUM_PARAMETERS % 2)) -ne 0 ]; then
            echo "Uneven amount of parameters entered, please check your input."
            usage
            exit 1
        fi
        while [ "$1" != "" ]; do
            PARAM=`echo $1`
            VALUE=`echo $2`
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
                --seleniumArtifact)
                    SELENIUM_ARTIFACT=${VALUE}
                    ;;
                --zaleniumArtifact)
                    ZALENIUM_ARTIFACT=${VALUE}
                    ;;
                --sauceLabsEnabled)
                    SAUCE_LABS_ENABLED=${VALUE}
                    ;;
                *)
                    echo "ERROR: unknown parameter \"$PARAM\""
                    usage
                    exit 1
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
