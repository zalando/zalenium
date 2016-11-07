#!/usr/bin/env bash

# Exit on failure
set -e

# Push docker image when a tag is set and it is the master branch.
# The tag will be set locally in one of the developer's machine.

if [ "$TRAVIS_BRANCH" = "master" ] && [ "$TRAVIS_PULL_REQUEST" = "false" ] && [ -n "${TRAVIS_TAG-}" ] && [ "${TRAVIS_TAG}" != "latest" ]; then
	echo "Starting to push Zalenium image..."
	docker login -u="$DOCKER_USERNAME" -p="$DOCKER_PASSWORD"
    echo "Logged in to docker with user '${DOCKER_USERNAME}'"
    echo "docker tag and docker push using TRAVIS_TAG=${TRAVIS_TAG}"
    docker tag zalenium:latest dosel/zalenium:${TRAVIS_TAG}
    docker tag zalenium:latest dosel/zalenium:latest
    docker push dosel/zalenium:${TRAVIS_TAG} | tee docker_push.log
    docker push dosel/zalenium:latest
else
	echo "Image not being pushed, either this is a PR, no tag is set, or the branch is not master."
fi