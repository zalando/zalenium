#!/usr/bin/env bash

# This script receives 1 value as a parameter which is a new version number.

# set variables
NEW_VERSION=$1
CHART_FILE="$(pwd)/Chart.yaml"
VALID_VERSION_REGEX='^[0-9]+.[0-9]+.[0-9]+'

# checks
if [[ ! "${NEW_VERSION}" =~ ${VALID_VERSION_REGEX} ]]; then
  echo "ERROR: Invalid version number."
  exit 1
fi

if [[ ! -e "${CHART_FILE}" ]]; then
  echo "ERROR: ${CHART_FILE} not found."
  exit 1
fi

which helm > /dev/null
if [[ $? != 0 ]]; then
  echo "ERROR: HELM not found."
  exit 1
fi

# reading current configs from Charts.yaml
CHART_NAME=$(grep -e '^name:' "${CHART_FILE}" | cut -d" " -f2)
CHART_VERSION=$(grep -e '^version:' "${CHART_FILE}" | cut -d" " -f2)
CHART_APPVERSION=$(grep -e '^appVersion:' "${CHART_FILE}" | cut -d" " -f2)

# debug
if [[ "${CHART_VERSION}" == "${NEW_VERSION}" ]]; then
  echo "New package version and the current version are equal. No need to update."
  exit 0
else
  echo "Updating chart ${CHART_NAME} from ${CHART_VERSION} to ${NEW_VERSION}.."
fi

# checking if we're in OSX
uname | grep -iq 'darwin'
[[ $? == 0 ]] && mysed="sed -i.bak -e" || mysed="sed -i.bak"

# set the new version number
$mysed "s/${CHART_VERSION}/${NEW_VERSION}/" "${CHART_FILE}"
$mysed "s/${CHART_APPVERSION}/${NEW_VERSION}/" "${CHART_FILE}"

# create a new package
helm package .
helm repo index .

[[ -e "${CHART_FILE}.bak" ]] && rm -rf "${CHART_FILE}.bak"
