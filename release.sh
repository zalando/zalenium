#!/usr/bin/env bash

set -e

RELEASE_VERSION=${1?"Usage $0 release-version [next-version]"}
NEXT_VERSION=${2?"Usage $0 release-version [next-version]. Parameter 'next-version' is missing."}

# release
mvn scm:check-local-modification versions:set -DnewVersion=${RELEASE_VERSION} scm:add -Dincludes="**/pom.xml" scm:checkin -Dmessage="Release $RELEASE_VERSION"

mvn scm:tag

# next development version
mvn versions:set -DnewVersion=${NEXT_VERSION} scm:add -Dincludes="**/pom.xml" scm:checkin -Dmessage="Develop $NEXT_VERSION"