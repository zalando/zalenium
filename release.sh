#!/usr/bin/env bash

set -e

# How to use
# ./release.sh release FIXED_VERSION, e.g. ./release.sh release 1.0.0
# ./release.sh develop SNAPSHOT_VERSION, e.g. ./release.sh release 1.1.0-SNAPSHOT

SCRIPT_ACTION=$1
NEW_VERSION=${2?"Usage $0 release|develop version"}

release_version()
{
    # release
    mvn scm:check-local-modification versions:set -DnewVersion=${NEW_VERSION} scm:add -Dincludes="**/pom.xml" scm:checkin -Dmessage="Release $NEW_VERSION"

    mvn scm:tag
}

develop_version()
{
    # next development version, since it only updates the pom.xml, we skip the Travis build
    mvn versions:set -DnewVersion=${NEW_VERSION} scm:add -Dincludes="**/pom.xml" scm:checkin -Dmessage="Develop $NEW_VERSION"
}

case ${SCRIPT_ACTION} in
    release)
        release_version
    ;;
    develop)
        develop_version
    ;;
esac