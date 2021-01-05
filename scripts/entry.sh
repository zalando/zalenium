#!/usr/bin/env bash

# set -e: exit asap if a command exits with a non-zero status
set -e

echoerr() { printf "%s\n" "$*" >&2; }

# print error and exit
die () {
  echoerr "ERROR: $1"
  # if $2 is defined AND NOT EMPTY, use $2; otherwise, set to "150"
  errnum=${2-188}
  exit $errnum
}

#==============================================
# OpenShift or non-sudo environments support
#==============================================

CURRENT_UID="$(id -u)"
CURRENT_GID="$(id -g)"

# Ensure that assigned uid has entry in /etc/passwd.
if ! whoami &> /dev/null; then
  echo "extrauser:x:${CURRENT_UID}:0::/home/extrauser:/bin/bash" >> /etc/passwd
fi

# Flag to know if we have sudo access
if sudo pwd >/dev/null 2>&1; then
  export WE_HAVE_SUDO_ACCESS="true"
else
  export WE_HAVE_SUDO_ACCESS="false"
  warn "We don't have sudo"
fi

if [ ${CURRENT_GID} -ne 1000 ]; then
  if [ "${WE_HAVE_SUDO_ACCESS}" == "true" ]; then
    sudo groupadd -f --gid ${CURRENT_GID} selgroup
    sudo gpasswd -a $(whoami) selgroup
  fi
fi

__run_with_gosu="false"
if [ "${__run_with_gosu}" == "true" ]; then
    # We still need gosu when accessing the docker.sock
    exec sudo --preserve-env gosu seluser ./zalenium.sh "$@"
else
    exec sudo --preserve-env ./zalenium.sh "$@"
fi
