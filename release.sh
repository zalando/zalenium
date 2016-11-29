#!/usr/bin/env bash

set -e

# How to use
# ./release.sh 
# ./release.sh release FIXED_VERSION, e.g. ./release.sh release 1.0.0
# ./release.sh develop SNAPSHOT_VERSION, e.g. ./release.sh release 1.1.0-SNAPSHOT

if [ -z "$1" ]; then
    read -p "Environment (release|develop) : " ENVIRONMENT
else
    ENVIRONMENT=$1
fi

if [ -z "$2" ]; then
    read -p "Application version: " VERSION
else
    VERSION=$2
fi

release_version()
{
    # release
    mvn scm:check-local-modification versions:set -DnewVersion=${VERSION} scm:add -Dincludes="**/pom.xml" scm:checkin -Dmessage="Release $VERSION"

    mvn scm:tag
}

develop_version()
{
    # next development version, since it only updates the pom.xml, we skip the Travis build
    mvn versions:set -DnewVersion=${VERSION} scm:add -Dincludes="**/pom.xml" scm:checkin -Dmessage="Develop $VERSION"
}

case ${ENVIRONMENT} in
    release)
        release_version
    ;;
    develop)
        develop_version
    ;;
    *)
        echo "Invalid environment! Valid options: release, develop"
    ;;
esac
