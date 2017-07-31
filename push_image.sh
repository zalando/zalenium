#!/usr/bin/env bash

# Exit on failure
set -e

# Push docker image when a tag is set and it is the master branch.
# The tag will be set locally in one of the developer's machine.

echo "TRAVIS_TAG=${TRAVIS_TAG}"

cat scm-source.json

if [ "$TRAVIS_PULL_REQUEST" = "false" ] && [ -n "${TRAVIS_TAG}" ] && [ "${TRAVIS_TAG}" != "latest" ]; then
	echo "Building image..."
	mvn clean package -Pbuild-docker-image -DskipTests=true
	echo "Starting to push Zalenium image..."
	docker login -u="$DOCKER_USERNAME" -p="$DOCKER_PASSWORD"
    echo "Logged in to docker with user '${DOCKER_USERNAME}'"
    echo "docker tag and docker push using TRAVIS_TAG=${TRAVIS_TAG}"
    docker tag zalenium:${TRAVIS_TAG} dosel/zalenium:${TRAVIS_TAG}
    docker push dosel/zalenium:${TRAVIS_TAG} | tee docker_push.log
    if [[ "${TRAVIS_TAG}" == "3."* ]]; then
        echo "Marking image with Selenium 3 as as zalenium:3 and latest..."
        docker tag zalenium:${TRAVIS_TAG} dosel/zalenium:3
        docker tag zalenium:${TRAVIS_TAG} dosel/zalenium:latest
        docker push dosel/zalenium:latest
        docker push dosel/zalenium:3
    else
        echo "Marking image with Selenium 2 as as zalenium:2..."
        docker tag zalenium:${TRAVIS_TAG} dosel/zalenium:2
        docker push dosel/zalenium:2
    fi
else
	echo "Image not being pushed, either this is a PR, no tag is set, or the branch is not master."
fi