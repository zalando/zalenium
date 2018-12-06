#!/usr/bin/env bash

CONTAINER_NAME="zalenium"
SELENIUM_IMAGE_NAME=${SELENIUM_IMAGE_NAME:-"elgalu/selenium"}
MAX_TEST_SESSIONS=${MAX_TEST_SESSIONS:-1}
DESIRED_CONTAINERS=${DESIRED_CONTAINERS:-2}
MAX_DOCKER_SELENIUM_CONTAINERS=${MAX_DOCKER_SELENIUM_CONTAINERS:-10}
ZALENIUM_ARTIFACT="$(pwd)/${project.build.finalName}.jar"
DEPRECATED_PARAMETERS=false
SAUCE_LABS_ENABLED=${SAUCE_LABS_ENABLED:-false}
BROWSER_STACK_ENABLED=${BROWSER_STACK_ENABLED:-false}
TESTINGBOT_ENABLED=${TESTINGBOT_ENABLED:-false}
VIDEO_RECORDING_ENABLED=${VIDEO_RECORDING_ENABLED:-true}
SCREEN_WIDTH=${SCREEN_WIDTH:-1920}
SCREEN_HEIGHT=${SCREEN_HEIGHT:-1080}
TZ=${TZ:-"Europe/Berlin"}
SEND_ANONYMOUS_USAGE_INFO=${SEND_ANONYMOUS_USAGE_INFO:-true}
START_TUNNEL=${START_TUNNEL:-false}
DEBUG_ENABLED=${DEBUG_ENABLED:-false}
KEEP_ONLY_FAILED_TESTS=${KEEP_ONLY_FAILED_TESTS:-false}
RETENTION_PERIOD=${RETENTION_PERIOD:-3}
LOG_JSON=${LOG_JSON:-false}
LOGBACK_PATH=${LOGBACK_PATH:-logback.xml}
NEW_SESSION_WAIT_TIMEOUT=${NEW_SESSION_WAIT_TIMEOUT:-600000}
WAIT_FOR_AVAILABLE_NODES=${WAIT_FOR_AVAILABLE_NODES:-true}
# Time in ms to wait for a container to start
TIME_TO_WAIT_TO_START=${TIME_TO_WAIT_TO_START:-180000}
# Maximum amount of times a test request is processed before starting a new node
MAX_TIMES_TO_PROCESS_REQUEST=${MAX_TIMES_TO_PROCESS_REQUEST:-30}

GA_TRACKING_ID="UA-88441352-3"
GA_ENDPOINT=https://www.google-analytics.com/collect
GA_API_VERSION="1"

KUBERNETES_ENABLED=false
if [ -f /var/run/secrets/kubernetes.io/serviceaccount/token ]; then
    KUBERNETES_ENABLED=true
fi

PID_PATH_SELENIUM=/tmp/selenium-pid
PID_PATH_DOCKER_SELENIUM_NODE=/tmp/docker-selenium-node-pid
PID_PATH_SAUCE_LABS_NODE=/tmp/sauce-labs-node-pid
PID_PATH_TESTINGBOT_NODE=/tmp/testingbot-node-pid
PID_PATH_BROWSER_STACK_NODE=/tmp/browser-stack-node-pid
PID_PATH_SAUCE_LABS_TUNNEL=/tmp/sauce-labs-tunnel-pid
PID_PATH_TESTINGBOT_TUNNEL=/tmp/testingbot-tunnel-pid
PID_PATH_BROWSER_STACK_TUNNEL=/tmp/browser-stack-tunnel-pid

echoerr() { printf "%s\n" "$*" >&2; }

# print error and exit
die() {
  echoerr "ERROR: $1"
  # if $2 is defined AND NOT EMPTY, use $2; otherwise, set to "160"
  errnum=${2-160}
  exit $errnum
}

WaitSeleniumHub()
{
    # Other option is to wait for certain text at
    #  logs/stdout.zalenium.hub.log
    while ! curl -sSL "http://localhost:4444/wd/hub/status" 2>&1 \
            | jq -r '.status' 2>&1 | grep "0" >/dev/null; do
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
            | jq -r '.status' 2>&1 | grep "0" >/dev/null; do
        echo -n '.'
        sleep 0.2
    done
}
export -f WaitStarterProxy

WaitStarterProxyToRegister()
{
    # Also wait for the Proxy to be registered into the hub
    while ! curl -sSL "http://localhost:4444/grid/console" 2>&1 \
            | grep "DockerSeleniumStarterRemoteProxy" 2>&1 >/dev/null; do
        echo -n '.'
        sleep 0.2
    done
}
export -f WaitStarterProxyToRegister

WaitSauceLabsProxy()
{
    # Wait for the sauce node success
    while ! curl -sSL "http://localhost:30001/wd/hub/status" 2>&1 \
            | jq -r '.status' 2>&1 | grep "0" >/dev/null; do
        echo -n '.'
        sleep 0.2
    done

    # Also wait for the Proxy to be registered into the hub
    while ! curl -sSL "http://localhost:4444/grid/console" 2>&1 \
            | grep "SauceLabsRemoteProxy" 2>&1 >/dev/null; do
        echo -n '.'
        sleep 0.2
    done
}
export -f WaitSauceLabsProxy

WaitBrowserStackProxy()
{
    # Wait for the sauce node success
    while ! curl -sSL "http://localhost:30002/wd/hub/status" 2>&1 \
            | jq -r '.status' 2>&1 | grep "0" >/dev/null; do
        echo -n '.'
        sleep 0.2
    done

    # Also wait for the Proxy to be registered into the hub
    while ! curl -sSL "http://localhost:4444/grid/console" 2>&1 \
            | grep "BrowserStackRemoteProxy" 2>&1 >/dev/null; do
        echo -n '.'
        sleep 0.2
    done
}
export -f WaitBrowserStackProxy

WaitTestingBotProxy()
{
    # Wait for the testingbot node success
    while ! curl -sSL "http://localhost:30003/wd/hub/status" 2>&1 \
            | jq -r '.status' 2>&1 | grep "0" >/dev/null; do
        echo -n '.'
        sleep 0.2
    done

    # Also wait for the Proxy to be registered into the hub
    while ! curl -sSL "http://localhost:4444/grid/console" 2>&1 \
            | grep "TestingBotRemoteProxy" 2>&1 >/dev/null; do
        echo -n '.'
        sleep 0.2
    done
}
export -f WaitTestingBotProxy

WaitForVideosTransferred() {
    local __amount_of_tests_with_video=$(jq .executedTestsWithVideo /home/seluser/videos/executedTestsInfo.json)

    if [ ${__amount_of_tests_with_video} -gt 0 ]; then
        local __amount_of_mp4_files=$(find /home/seluser/videos/ -name '*.mp4' | wc -l)
        while [ "${__amount_of_mp4_files}" -lt "${__amount_of_tests_with_video}" ]; do
            log "Waiting for ${__amount_of_mp4_files} mp4 files to be a total of ${__amount_of_tests_with_video}..."
            sleep 4

            # Also check if there are mkv, this would mean that
            # docker-selenium failed to convert them to mp4
            #local __amount_of_mkv_files=$(ls -1q find /home/seluser/videos/ -name '*.mkv' | wc -l)
            local __amount_of_mkv_files=$(find /home/seluser/videos/ -name '*.mkv' | wc -l)
            if [ ${__amount_of_mkv_files} -gt 0 ]; then
                for __filename in /home/seluser/videos/*.mkv; do
                    local __new_file_name="$(basename ${__filename} .mkv).mp4"
                    log "Renaming ${__filename} into ${__new_file_name} ..."
                    mv "${__filename}" "${__new_file_name}"
                    log "You may consider re-encoding the file to fix the video length later on..."
                done
            fi

            __amount_of_mp4_files=$(find /home/seluser/videos/ -name '*.mp4' | wc -l)
        done
    fi
}
export -f WaitForVideosTransferred

EnsureDockerWorks()
{
    log "Ensuring docker works..."
    if ! docker -H ${DOCKER_HOST} ps >/dev/null; then
        echo "Docker seems to be not working properly, check the above error."
        exit 1
    fi
}

DisplayDataProcessingAgreement()
{
    echo "*************************************** Data Processing Agreement ***************************************"
    echo -e "By using this software you agree that the following non-PII (non personally identifiable information)
data will be collected, processed and used by Zalando SE for the purpose of improving our test
infrastructure tools. Anonymisation with respect of the IP address means that only the first two octets
of the IP address are collected.

See the complete license at https://github.com/zalando/zalenium/blob/master/LICENSE.md"
    echo "*************************************** Data Processing Agreement ***************************************"
}

DockerTerminate()
{
    echo "Trapped SIGTERM/SIGINT so shutting down Zalenium gracefully..."
    ShutDown
    if [ "$SEND_ANONYMOUS_USAGE_INFO" = true ]; then
        DisplayDataProcessingAgreement
        # Random ID used for Google Analytics
        # If it is running inside the Zalando Jenkins env, we pick the team name from the $BUILD_URL
        # else we pick it from the random ID generated by each docker installation, not related to the user nor the machine
        if [[ $BUILD_URL == *"zalan.do"* ]]; then
            RANDOM_USER_GA_ID=$(echo $BUILD_URL | cut -d'/' -f 3 | cut -d'.' -f 1)
        elif [[ $CDP_TARGET_REPOSITORY == *"github.com"* ]] || [[ $CDP_TARGET_REPOSITORY == *"github.bus.zalan.do"* ]]; then
            RANDOM_USER_GA_ID=$(echo $CDP_TARGET_REPOSITORY | cut -d'/' -f 2)
        elif [[ $KUBERNETES_ENABLED == "true" ]]; then
            RANDOM_USER_GA_ID=k8s-$(echo -n $HOSTNAME$KUBERNETES_SERVICE_HOST | md5sum)
        else
            RANDOM_USER_GA_ID=docker-$(docker -H ${DOCKER_HOST} info 2>&1 | grep -Po '(?<=^ID: )(\w{4}:.+)')
        fi

        # Gathering the options used to start Zalenium, in order to learn about the used options
        ZALENIUM_STOP_COMMAND="zalenium.sh stop"

        local args=(
            --max-time 10
            --data v=${GA_API_VERSION}
            --data aip=1
            --data t=screenview
            --data tid="$GA_TRACKING_ID"
            --data cid="$RANDOM_USER_GA_ID"
            --data an="zalenium"
            --data av="${project.build.finalName}.jar"
            --data cd="$ZALENIUM_STOP_COMMAND"
            --data sc="end"
            --data ds="docker"
        )

        if [[ "${project.build.finalName}.jar" == *"SNAPSHOT"* ]]; then
            echo "Not sending info to GA since this is a SNAPSHOT version"
        else
            curl ${GA_ENDPOINT} "${args[@]}" \
                --silent --output /dev/null &>/dev/null
        fi

    fi
    wait
    exit 0
}

# Run function DockerTerminate() when this process receives a killing signal
trap DockerTerminate SIGTERM SIGINT SIGKILL

StartUp()
{
    if [ ${KUBERNETES_ENABLED} == "false" ]; then
        EnsureDockerWorks
        CONTAINER_ID=$(grep docker /proc/self/cgroup | head -n 1 | grep -o -E '[0-9a-f]{64}' | tail -n 1)
        CONTAINER_NAME=$(docker -H ${DOCKER_HOST} inspect ${CONTAINER_ID} | jq -r '.[0].Name' | sed 's/\///g')

        log "Ensuring docker-selenium is available..."
        DOCKER_SELENIUM_IMAGE_COUNT=$(docker -H ${DOCKER_HOST} images ${SELENIUM_IMAGE_NAME} --quiet | wc -l)
        if [ ${DOCKER_SELENIUM_IMAGE_COUNT} -eq 0 ]; then
            if [ ${PULL_SELENIUM_IMAGE:-false} == "true" ]; then
                echo "Pulling docker-selenium's image: ${SELENIUM_IMAGE_NAME}"
                docker -H ${DOCKER_HOST} pull ${SELENIUM_IMAGE_NAME}
            else
                echo "Seems that docker-selenium's image has not been pulled yet"
                echo "Please run 'docker pull elgalu/selenium', or use your own compatible image via --seleniumImageName"
                exit 1
            fi
        fi
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

    if [ -z ${BROWSER_STACK_ENABLED} ]; then
        BROWSER_STACK_ENABLED=true
    fi

    if [ "$BROWSER_STACK_ENABLED" = true ]; then
        BROWSER_STACK_USER="${BROWSER_STACK_USER:=abc}"
        BROWSER_STACK_KEY="${BROWSER_STACK_KEY:=abc}"

        if [ "$BROWSER_STACK_USER" = abc ]; then
            echo "BROWSER_STACK_USER environment variable is not set, cannot start Browser Stack node, exiting..."
            exit 5
        fi

        if [ "$BROWSER_STACK_KEY" = abc ]; then
            echo "BROWSER_STACK_KEY environment variable is not set, cannot start Browser Stack node, exiting..."
            exit 6
        fi
    fi

    if [ "$TESTINGBOT_ENABLED" = true ]; then
        TESTINGBOT_KEY="${TESTINGBOT_KEY:=abc}"
        TESTINGBOT_SECRET="${TESTINGBOT_SECRET:=abc}"

        if [ "$TESTINGBOT_KEY" = abc ]; then
            echo "TESTINGBOT_KEY environment variable is not set, cannot start TestingBot node, exiting..."
            exit 4
        fi

        if [ "$TESTINGBOT_SECRET" = abc ]; then
            echo "TESTINGBOT_SECRET environment variable is not set, cannot start TestingBot node, exiting..."
            exit 5
        fi
    fi

    export ZALENIUM_DESIRED_CONTAINERS=${DESIRED_CONTAINERS}
    export ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS=${MAX_DOCKER_SELENIUM_CONTAINERS}
    export ZALENIUM_VIDEO_RECORDING_ENABLED=${VIDEO_RECORDING_ENABLED}
    export ZALENIUM_TZ=${TZ}
    export ZALENIUM_SCREEN_WIDTH=${SCREEN_WIDTH}
    export ZALENIUM_SCREEN_HEIGHT=${SCREEN_HEIGHT}
    export ZALENIUM_CONTAINER_NAME=${CONTAINER_NAME}
    export ZALENIUM_SELENIUM_IMAGE_NAME=${SELENIUM_IMAGE_NAME}
    export ZALENIUM_MAX_TEST_SESSIONS=${MAX_TEST_SESSIONS}
    export ZALENIUM_KEEP_ONLY_FAILED_TESTS=${KEEP_ONLY_FAILED_TESTS}
    export ZALENIUM_RETENTION_PERIOD=${RETENTION_PERIOD}
    export ZALENIUM_NODE_PARAMS=${SELENIUM_NODE_PARAMS}

    # Random ID used for Google Analytics
    # If it is running inside the Zalando Jenkins env, we pick the team name from the $BUILD_URL
    # else we pick it from the random ID generated by each docker installation, not related to the user nor the machine
    if [[ $BUILD_URL == *"zalan.do"* ]]; then
        RANDOM_USER_GA_ID=$(echo $BUILD_URL | cut -d'/' -f 3 | cut -d'.' -f 1)
    elif [[ $CDP_TARGET_REPOSITORY == *"github.com"* ]] || [[ $CDP_TARGET_REPOSITORY == *"github.bus.zalan.do"* ]]; then
        RANDOM_USER_GA_ID=$(echo $CDP_TARGET_REPOSITORY | cut -d'/' -f 2)
    elif [[ $KUBERNETES_ENABLED == "true" ]]; then
        RANDOM_USER_GA_ID=k8s-$(echo -n $HOSTNAME$KUBERNETES_SERVICE_HOST | md5sum)
    else
        RANDOM_USER_GA_ID=docker-$(docker -H ${DOCKER_HOST} info 2>&1 | grep -Po '(?<=^ID: )(\w{4}:.+)')
    fi

    export ZALENIUM_GA_API_VERSION=${GA_API_VERSION}
    export ZALENIUM_GA_TRACKING_ID=${GA_TRACKING_ID}
    export ZALENIUM_GA_ENDPOINT=${GA_ENDPOINT}
    export ZALENIUM_GA_ANONYMOUS_CLIENT_ID=${RANDOM_USER_GA_ID}
    if [[ "${project.build.finalName}.jar" != *"SNAPSHOT"* ]]; then
        export ZALENIUM_SEND_ANONYMOUS_USAGE_INFO=${SEND_ANONYMOUS_USAGE_INFO}
    fi

    #==============================================
    # Java blocks until kernel have enough entropy
    # to generate the /dev/random seed
    #==============================================
    # See: SeleniumHQ/docker-selenium/issues/14
    if [ "${WE_HAVE_SUDO_ACCESS}" == "true" ]; then
      # We found that, for better entropy, running haveged
      # with --privileged and sudo here works more reliable
      sudo -E haveged
    else
      haveged
    fi

    echo "Copying files for Dashboard..."
    cp /home/seluser/index.html /home/seluser/videos/index.html
    cp -r /home/seluser/css /home/seluser/videos
    cp -r /home/seluser/js /home/seluser/videos

    if [ ! -z ${GRID_USER} ] && [ ! -z ${GRID_PASSWORD} ]; then
        echo "Enabling basic auth via startup script..."
        htpasswd -bc /home/seluser/.htpasswd ${GRID_USER} ${GRID_PASSWORD}
    fi

    echo "Starting Nginx reverse proxy..."
    nginx

    echo "Starting Selenium Hub..."

    mkdir -p logs

    DEBUG_MODE=info
    if [ "$DEBUG_ENABLED" = true ]; then
        DEBUG_MODE=debug
        DEBUG_FLAG=-debug
    fi

    LOGBACK_APPENDER=STDOUT

    if [ "$LOG_JSON" == true ]; then
        LOGBACK_APPENDER=JSON
    fi

    java ${ZALENIUM_EXTRA_JVM_PARAMS} -Dlogback.loglevel=${DEBUG_MODE} \
    -Dlogback.appender=${LOGBACK_APPENDER} \
    -Dlogback.configurationFile=${LOGBACK_PATH} \
    -Djava.util.logging.config.file=logging_${DEBUG_MODE}.properties \
    -cp ${ZALENIUM_ARTIFACT} org.openqa.grid.selenium.GridLauncherV3 \
    -role hub -port 4445 -newSessionWaitTimeout ${NEW_SESSION_WAIT_TIMEOUT} \
    -registry de.zalando.ep.zalenium.registry.ZaleniumRegistry \
    ${SELENIUM_HUB_PARAMS} \
    ${DEBUG_FLAG} &

    echo $! > ${PID_PATH_SELENIUM}

    if ! timeout --foreground "1m" bash -c WaitSeleniumHub; then
        echo "GridLauncher failed to start after 1 minute, failing..."
        curl "http://localhost:4444/wd/hub/status"
        exit 11
    fi
    echo "Selenium Hub started!"

    if ! curl -sSL "http://localhost:4444" | grep Grid >/dev/null; then
        echo "Error: The Grid is not listening at port 4444"
        exit 7
    fi

    if [ "$SAUCE_LABS_ENABLED" = true ]; then
        echo "Starting Sauce Labs node..."
        java -Dlogback.loglevel=${DEBUG_MODE} -Dlogback.appender=${LOGBACK_APPENDER} \
         -Dlogback.configurationFile=${LOGBACK_PATH} \
         -Djava.util.logging.config.file=logging_${DEBUG_MODE}.properties \
         -cp ${ZALENIUM_ARTIFACT} org.openqa.grid.selenium.GridLauncherV3 -role node -hub http://localhost:4444/grid/register \
         -registerCycle 0 -proxy de.zalando.ep.zalenium.proxy.SauceLabsRemoteProxy \
         -nodePolling 90000 -port 30001 ${DEBUG_FLAG} &
        echo $! > ${PID_PATH_SAUCE_LABS_NODE}

        if ! timeout --foreground "40s" bash -c WaitSauceLabsProxy; then
            echo "SauceLabsRemoteProxy failed to start after 40 seconds, failing..."
            exit 12
        fi
        echo "Sauce Labs node started!"
        if [ "$START_TUNNEL" = true ]; then
            export SAUCE_LOG_FILE="$(pwd)/logs/saucelabs-stdout.log"
            export SAUCE_TUNNEL="true"
            echo "Starting Sauce Connect..."
            [ -z "${SAUCE_TUNNEL_ID}" ] && die "$0: Required env var SAUCE_TUNNEL_ID"
            ./start-saucelabs.sh &
            echo $! > ${PID_PATH_SAUCE_LABS_TUNNEL}
            # Now wait for the tunnel to be ready
            timeout --foreground ${SAUCE_WAIT_TIMEOUT} ./wait-saucelabs.sh
        fi
    else
        echo "Sauce Labs not enabled..."
    fi

    if [ "$BROWSER_STACK_ENABLED" = true ]; then
        echo "Starting Browser Stack node..."
        java -Dlogback.loglevel=${DEBUG_MODE} -Dlogback.appender=${LOGBACK_APPENDER} \
         -Dlogback.configurationFile=${LOGBACK_PATH} \
         -Djava.util.logging.config.file=logging_${DEBUG_MODE}.properties \
         -cp ${ZALENIUM_ARTIFACT} org.openqa.grid.selenium.GridLauncherV3 -role node -hub http://localhost:4444/grid/register \
         -registerCycle 0 -proxy de.zalando.ep.zalenium.proxy.BrowserStackRemoteProxy \
         -nodePolling 90000 -port 30002 ${DEBUG_FLAG} &
        echo $! > ${PID_PATH_BROWSER_STACK_NODE}

        if ! timeout --foreground "40s" bash -c WaitBrowserStackProxy; then
            echo "BrowserStackRemoteProxy failed to start after 40 seconds, failing..."
            exit 12
        fi
        echo "Browser Stack node started!"
        if [ "$START_TUNNEL" = true ]; then
            export BROWSER_STACK_LOG_FILE="$(pwd)/logs/browserstack-stdout.log"
            export BROWSER_STACK_TUNNEL="true"
            echo "Starting BrowserStackLocal..."
            ./start-browserstack.sh &
            echo $! > ${PID_PATH_BROWSER_STACK_TUNNEL}
            # Now wait for the tunnel to be ready
            timeout --foreground ${BROWSER_STACK_WAIT_TIMEOUT} ./wait-browserstack.sh
        fi
    else
        echo "Browser Stack not enabled..."
    fi

    if [ "$TESTINGBOT_ENABLED" = true ]; then
        echo "Starting TestingBot node..."
        java -Dlogback.loglevel=${DEBUG_MODE} -Dlogback.appender=${LOGBACK_APPENDER} \
         -Dlogback.configurationFile=${LOGBACK_PATH} \
         -Djava.util.logging.config.file=logging_${DEBUG_MODE}.properties \
         -cp ${ZALENIUM_ARTIFACT} org.openqa.grid.selenium.GridLauncherV3 -role node -hub http://localhost:4444/grid/register \
         -registerCycle 0 -proxy de.zalando.ep.zalenium.proxy.TestingBotRemoteProxy \
         -nodePolling 90000 -port 30003 ${DEBUG_FLAG} &
        echo $! > ${PID_PATH_TESTINGBOT_NODE}

        if ! timeout --foreground "40s" bash -c WaitTestingBotProxy; then
            echo "TestingBotRemoteProxy failed to start after 40 seconds, failing..."
            exit 12
        fi
        echo "TestingBot node started!"
        if [ "$START_TUNNEL" = true ]; then
            export TESTINGBOT_LOG_FILE="$(pwd)/logs/testingbot-stdout.log"
            export TESTINGBOT_TUNNEL="true"
            echo "Starting TestingBot Tunnel..."
            ./start-testingbot.sh &
            echo $! > ${PID_PATH_TESTINGBOT_TUNNEL}
            # Now wait for the tunnel to be ready
            timeout --foreground ${TESTINGBOT_WAIT_TIMEOUT} ./wait-testingbot.sh
        fi
    else
        echo "TestingBot not enabled..."
    fi

    echo "Zalenium is now ready!"

    if [ "$SEND_ANONYMOUS_USAGE_INFO" = true ]; then

        DisplayDataProcessingAgreement
        if [ ${KUBERNETES_ENABLED} == "false" ]; then
            docker -H ${DOCKER_HOST} info >docker_info.txt 2>&1

            # Random ID generated by each docker installation, not related to the user nor the machine
            DOCKER_CLIENT_VERSION=$(docker -v)
            DOCKER_SERVER_VERSION=$(grep -Po '(?<=^Server Version: )(\d{1,2}.+)' docker_info.txt)
            KERNEL_VERSION=$(grep -Po '(?<=^Kernel Version: )(\d{1,2}.+)' docker_info.txt)
            OPERATING_SYSTEM=$(grep -Po '(?<=^Operating System: )(\w{1,2}.+)' docker_info.txt)
            OS_TYPE=$(grep -Po '(?<=^OSType: )(\w{1,2}.+)' docker_info.txt)
            ARCHITECTURE=$(grep -Po '(?<=^Architecture: )(\w{1,2}.+)' docker_info.txt)
            CPU_NUMBER=$(grep -Po '(?<=^CPUs: )(\d{1,2})' docker_info.txt)
            TOTAL_MEMORY=$(grep -Po '(?<=^Total Memory: )(\w{1,2}.+)(?<= )' docker_info.txt)
        else
            # We just log that Kubernetes is being used
            DOCKER_CLIENT_VERSION=Kubernetes
            DOCKER_SERVER_VERSION=Kubernetes
            KERNEL_VERSION=0.0
            OPERATING_SYSTEM=Kubernetes
            OS_TYPE=Kubernetes
            ARCHITECTURE=x86_64
            CPU_NUMBER=0
            TOTAL_MEMORY=0.0
        fi

        # Gathering the options used to start Zalenium, in order to learn about the used options
        ZALENIUM_START_COMMAND="zalenium.sh --deprecatedParameters $DEPRECATED_PARAMETERS
            --desiredContainers $DESIRED_CONTAINERS --maxDockerSeleniumContainers $MAX_DOCKER_SELENIUM_CONTAINERS
            --maxTestSessions $MAX_TEST_SESSIONS --sauceLabsEnabled $SAUCE_LABS_ENABLED
            --browserStackEnabled $BROWSER_STACK_ENABLED --testingBotEnabled $TESTINGBOT_ENABLED
            --videoRecordingEnabled $VIDEO_RECORDING_ENABLED --screenWidth $SCREEN_WIDTH --screenHeight $SCREEN_HEIGHT
            --timeZone $TZ"

        local args=(
            --max-time 10
            --data v=${GA_API_VERSION}
            --data aip=1
            --data t=screenview
            --data tid="$GA_TRACKING_ID"
            --data cid="$RANDOM_USER_GA_ID"
            --data an="zalenium"
            --data av="${project.build.finalName}.jar"
            --data cd="$ZALENIUM_START_COMMAND"
            --data sc="start"
            --data ds="docker"
            --data cd1="$DOCKER_CLIENT_VERSION"
            --data cd2="$DOCKER_SERVER_VERSION"
            --data cd3="$KERNEL_VERSION"
            --data cd4="$OPERATING_SYSTEM"
            --data cd5="$OS_TYPE"
            --data cd6="$ARCHITECTURE"
            --data cm1="$CPU_NUMBER"
            --data cm2="$TOTAL_MEMORY"
        )

        if [[ "${project.build.finalName}.jar" == *"SNAPSHOT"* ]]; then
            echo "Not sending info to GA since this is a SNAPSHOT version"
        else
            curl ${GA_ENDPOINT} \
                "${args[@]}" --silent --output /dev/null &>/dev/null & disown
        fi

    fi

    # When running in docker do not exit this script
    wait
}

ShutDown()
{
    if [ -f ${PID_PATH_SAUCE_LABS_NODE} ];
    then
        echo "Stopping Sauce Labs node..."
        PID=$(cat ${PID_PATH_SAUCE_LABS_NODE});
        kill ${PID};
        _returnedValue=$?
        if [ "${_returnedValue}" != "0" ] ; then
            echo "Failed to send kill signal to Sauce Labs node!"
        else
            rm ${PID_PATH_SAUCE_LABS_NODE}
        fi
    fi

    if [ -f ${PID_PATH_BROWSER_STACK_NODE} ];
    then
        echo "Stopping Browser Stack node..."
        PID=$(cat ${PID_PATH_BROWSER_STACK_NODE});
        kill ${PID};
        _returnedValue=$?
        if [ "${_returnedValue}" != "0" ] ; then
            echo "Failed to send kill signal to Browser Stack node!"
        else
            rm ${PID_PATH_BROWSER_STACK_NODE}
        fi
    fi

    if [ -f ${PID_PATH_TESTINGBOT_NODE} ];
    then
        echo "Stopping TestingBot node..."
        PID=$(cat ${PID_PATH_TESTINGBOT_NODE});
        kill ${PID};
        _returnedValue=$?
        if [ "${_returnedValue}" != "0" ] ; then
            echo "Failed to send kill signal to TestingBot node!"
        else
            rm ${PID_PATH_TESTINGBOT_NODE}
        fi
    fi

    if [ -f ${PID_PATH_SAUCE_LABS_TUNNEL} ];
    then
        echo "Stopping Sauce Connect..."
        PID=$(cat ${PID_PATH_SAUCE_LABS_TUNNEL});
        kill -SIGTERM ${PID};
        wait ${PID};
        _returnedValue=$?
        if [ "${_returnedValue}" != "0" ] ; then
            echo "Failed to send kill signal to Sauce Connect!"
        else
            rm ${PID_PATH_SAUCE_LABS_TUNNEL}
        fi
    fi

    if [ -f ${PID_PATH_BROWSER_STACK_TUNNEL} ];
    then
        echo "Stopping BrowserStackLocal..."
        PID=$(cat ${PID_PATH_BROWSER_STACK_TUNNEL});
        kill -SIGTERM ${PID};
        wait ${PID};
        _returnedValue=$?
        if [ "${_returnedValue}" != "0" ] ; then
            echo "Failed to send kill signal to BrowserStackLocal!"
        else
            rm ${PID_PATH_BROWSER_STACK_TUNNEL}
        fi
    fi

    if [ -f ${PID_PATH_TESTINGBOT_TUNNEL} ];
    then
        echo "Stopping TestingBot tunnel..."
        PID=$(cat ${PID_PATH_TESTINGBOT_TUNNEL});
        kill -SIGTERM ${PID};
        wait ${PID};
        _returnedValue=$?
        if [ "${_returnedValue}" != "0" ] ; then
            echo "Failed to send kill signal to the TestingBot tunnel!"
        else
            rm ${PID_PATH_TESTINGBOT_TUNNEL}
        fi
    fi

    if [ -f /home/seluser/videos/executedTestsInfo.json ]; then
        # Wait for the dashboard and the videos, if applies
        if timeout --foreground "40s" bash -c WaitForVideosTransferred; then
            local __total=$(jq .executedTestsWithVideo /home/seluser/videos/executedTestsInfo.json)
            log "WaitForVideosTransferred succeeded for a total of ${__total}"
        else
            log "WaitForVideosTransferred failed after 40 seconds!"
        fi
    fi

    if [ -f ${PID_PATH_SELENIUM} ];
    then
        echo "Stopping Hub..."
        PID=$(cat ${PID_PATH_SELENIUM});
        kill ${PID};
        _returnedValue=$?
        if [ "${_returnedValue}" != "0" ] ; then
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
        _returnedValue=$?
        if [ "${_returnedValue}" != "0" ] ; then
            echo "Failed to send kill signal to DockerSeleniumStarter node!"
        else
            rm ${PID_PATH_DOCKER_SELENIUM_NODE}
        fi
    fi
}

function usage()
{
    echo "Usage:"
    echo ""
    echo "zalenium"
    echo -e "\t -h --help"
    echo -e "\t start <options, see below>"
    echo -e "\t --desiredContainers -> Number of nodes/containers created on startup. Default is 2."
    echo -e "\t --maxDockerSeleniumContainers -> Max number of docker-selenium containers running at the same time. Default is 10."
    echo -e "\t --sauceLabsEnabled -> Determines if the Sauce Labs node is started. Defaults to 'false'."
    echo -e "\t --browserStackEnabled -> Determines if the Browser Stack node is started. Defaults to 'false'."
    echo -e "\t --testingBotEnabled -> Determines if the TestingBot node is started. Defaults to 'false'."
    echo -e "\t --startTunnel -> When using a cloud testing platform is enabled, starts the tunnel to allow local testing. Defaults to 'false'."
    echo -e "\t --videoRecordingEnabled -> Sets if video is recorded in every test. Defaults to 'true'."
    echo -e "\t --screenWidth -> Sets the screen width. Defaults to 1900"
    echo -e "\t --screenHeight -> Sets the screen height. Defaults to 1880"
    echo -e "\t --timeZone -> Sets the time zone in the containers. Defaults to \"Europe/Berlin\""
    echo -e "\t --sendAnonymousUsageInfo -> Collects anonymous usage of the tool. Defaults to 'true'"
    echo -e "\t --debugEnabled -> enables LogLevel.FINE. Defaults to 'false'"
    echo -e "\t --logJson -> output logs in json format. Defaults to 'false'"
    echo -e "\t --logbackConfigFilePath -> path to a custom logback config file. Defaults to 'logback.xml'"
    echo -e "\t --seleniumImageName -> enables overriding of the Docker selenium image to use. Defaults to \"elgalu/selenium\""
    echo -e "\t --gridUser -> allows you to specify a user to enable basic auth protection, --gridPassword must be provided also."
    echo -e "\t --gridPassword -> allows you to specify a password to enable basic auth protection, --gridUser must be provided also."
    echo -e "\t --maxTestSessions -> max amount of tests executed per container, defaults to '1'."
    echo -e "\t --keepOnlyFailedTests -> Keeps only videos of failed tests (you need to send a cookie). Defaults to 'false'"
 	echo -e "\t --retentionPeriod -> Number of day's a testentry should be kept in dashboard before cleanup. Defaults to 3"
    echo ""
    echo -e "\t stop"
    echo ""
    echo -e "\t Examples:"
    echo -e "\t - Starting Zalenium with 2 containers and with Sauce Labs"
    echo -e "\t start --desiredContainers 2 --sauceLabsEnabled true"
    echo -e "\t - Starting Zalenium with 2 containers and with BrowserStack"
    echo -e "\t start --desiredContainers 2 --browserStackEnabled true"
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
                --desiredContainers)
                    DESIRED_CONTAINERS=${VALUE}
                    ;;
                --maxDockerSeleniumContainers)
                    MAX_DOCKER_SELENIUM_CONTAINERS=${VALUE}
                    ;;
                --sauceLabsEnabled)
                    SAUCE_LABS_ENABLED=${VALUE}
                    ;;
                --browserStackEnabled)
                    BROWSER_STACK_ENABLED=${VALUE}
                    ;;
                --testingBotEnabled)
                    TESTINGBOT_ENABLED=${VALUE}
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
                --sendAnonymousUsageInfo)
                    SEND_ANONYMOUS_USAGE_INFO=${VALUE}
                    ;;
                --startTunnel)
                    START_TUNNEL=${VALUE}
                    ;;
                --debugEnabled)
                    DEBUG_ENABLED=${VALUE}
                    ;;
                --logJson)
                    LOG_JSON=${VALUE}
                    ;;
                --logbackConfigFilePath)
                    LOGBACK_PATH=${VALUE}
                    ;;
                --seleniumImageName)
                    SELENIUM_IMAGE_NAME=${VALUE}
                    ;;
                --gridUser)
                    GRID_USER=${VALUE}
                    ;;
                --gridPassword)
                    GRID_PASSWORD=${VALUE}
                    ;;
                --maxTestSessions)
                    MAX_TEST_SESSIONS=${VALUE}
                    ;;
                --keepOnlyFailedTests)
                    KEEP_ONLY_FAILED_TESTS=${VALUE}
                    ;;
                --retentionPeriod)
                    RETENTION_PERIOD=${VALUE}
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