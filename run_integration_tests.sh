#!/bin/sh
# Exit on failure
set -e

INTEGRATION_TO_TEST=$1

VIDEOS_FOLDER=$(pwd)/target/videos
echo ${VIDEOS_FOLDER}

if [ "$TRAVIS_PULL_REQUEST" = "false" ] && [ -n "${TRAVIS_TAG}" ] && [ "${TRAVIS_TAG}" != "latest" ]; then
    echo "TRAVIS_TAG=${TRAVIS_TAG}"
	echo "Not running integration tests when a TAG is set, we assume they already ran in the PR."
else
    # If the environment var exists, then we run the integration tests. This is to allow external PRs ro tun
    if [ "$INTEGRATION_TO_TEST" = sauceLabs ]; then
        if [ -n "${SAUCE_USERNAME}" ]; then
            sudo env "PATH=$PATH" mvn clean
            mvn clean verify -Pintegration-test -DthreadCountProperty=2 -Dskip.surefire.tests=true -DintegrationToTest=${INTEGRATION_TO_TEST}
            # Check for generated videos
            ls -la ${VIDEOS_FOLDER}/saucelabs*.mp4 || (echo "No Sauce Labs videos were downloaded." && exit 2)
            ls -la ${VIDEOS_FOLDER}/zalenium*.mp4 || (echo "No Zalenium videos were generated." && exit 2)
        fi
    fi
    if [ "$INTEGRATION_TO_TEST" = browserStack ]; then
        if [ -n "${BROWSER_STACK_USER}" ]; then
            sudo env "PATH=$PATH" mvn clean
            mvn clean verify -Pintegration-test -DthreadCountProperty=2 -Dskip.surefire.tests=true -DintegrationToTest=${INTEGRATION_TO_TEST}
            # Check for generated videos
            ls -la ${VIDEOS_FOLDER}/browserstack*.mp4 || (echo "No BrowserStack videos were downloaded." && exit 2)
            ls -la ${VIDEOS_FOLDER}/zalenium*.mp4 || (echo "No Zalenium videos were generated." && exit 2)
        fi
    fi
    if [ "$INTEGRATION_TO_TEST" = testingBot ]; then
        if [ -n "${TESTINGBOT_KEY}" ]; then
            sudo env "PATH=$PATH" mvn clean
            mvn clean verify -Pintegration-test -DthreadCountProperty=2 -Dskip.surefire.tests=true -DintegrationToTest=${INTEGRATION_TO_TEST}
            # Check for generated videos
            ls -la ${VIDEOS_FOLDER}/testingbot*.mp4 || (echo "No TestingBot videos were downloaded." && exit 2)
            ls -la ${VIDEOS_FOLDER}/zalenium*.mp4 || (echo "No Zalenium videos were generated." && exit 2)
        fi
    fi
    if [ "$INTEGRATION_TO_TEST" = dockerCompose ]; then
        if [ -n "${SAUCE_USERNAME}" ]; then
            sudo env "PATH=$PATH" mvn clean
            mvn clean package -Pbuild-docker-image -DskipTests=true
            mkdir -p "${VIDEOS_FOLDER}"
            chmod +x target/zalenium_in_docker_compose.sh
            target/zalenium_in_docker_compose.sh start
            mvn verify -Pintegration-test -DthreadCountProperty=2 -Dskip.surefire.tests=true -Dskip.failsafe.setup=true -DintegrationToTest=sauceLabs
            # Check for generated videos
            ls -la ${VIDEOS_FOLDER}/saucelabs*.mp4 || (echo "No Sauce Labs videos were downloaded." && exit 2)
            ls -la ${VIDEOS_FOLDER}/zalenium*.mp4 || (echo "No Zalenium videos were generated." && exit 2)
            target/zalenium_in_docker_compose.sh stop
        fi
    fi
    if [ "$INTEGRATION_TO_TEST" = minikube ]; then
        MINIKUBE_IP=$(minikube ip)
        export ZALENIUM_GRID_PORT=$(kubectl get svc zalenium --namespace zalenium -o go-template='{{ index (index .spec.ports 0) "nodePort" }}{{ "\n" }}')
        export ZALENIUM_GRID_HOST=$MINIKUBE_IP
        mvn verify -Pintegration-test -DthreadCountProperty=2 -Dskip.surefire.tests=true -Dskip.failsafe.setup=true -Dgroups=minikube
    fi
fi

