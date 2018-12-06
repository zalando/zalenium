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
    sudo groupadd --gid ${CURRENT_GID} selgroup
    sudo gpasswd -a $(whoami) selgroup
  fi
fi

#===================================================================
# K8s, vs shared -v /usr/bin/docker vs figure out the Docker version
#===================================================================

USE_KUBERNETES=false
if [ -f /var/run/secrets/kubernetes.io/serviceaccount/token ]; then
    echo "Kubernetes service account found."
    USE_KUBERNETES=true
elif [ -f /usr/bin/docker ]; then
    echo "Docker binary already present, will use that one."
else
    # Grab the complete docker version `1.12.5` out of the partial one `1.12`
    export DOCKER_VERSION=$(ls /usr/bin/docker-${DOCKER}* | grep -Po '(?<=docker-)([a-z0-9\.-]+)' | head -1)
    # Link the docker binary to the selected docker version via e.g. `-e DOCKER=1.11`
    if [ -f /usr/bin/docker-${DOCKER_VERSION} ]; then
        sudo ln -s /usr/bin/docker-${DOCKER_VERSION} /usr/bin/docker
    else
        echo "Something went wrong trying to find DOCKER_VERSION=${DOCKER_VERSION} with DOCKER=${DOCKER}"
        ls -la /usr/bin/docker*
        echo "Trying to fetch latest docker binary at /usr/bin/docker*"
        DOCKER_BIN=$(ls -U /usr/bin/docker* | sort -r | head -1)
        if [ -f ${DOCKER_BIN} ]; then
            sudo ln -s ${DOCKER_BIN} /usr/bin/docker
        else
            echo "FATAL: Last attempt to find a valid docker binary to use failed."
            echo "FATAL: DOCKER_BIN=${DOCKER_BIN}"
            exit 1
        fi
    fi
fi

__run_with_gosu="false"

#============================================================
# Spaghetti conditionals to handle a variety of environments
#============================================================

# If this was docker run with: -e HOST_GID="$(id -g)" -e HOST_UID="$(id -u)"
if [ "${HOST_UID}" != "" ] && [ "${HOST_GID}" != "" ]; then
    # Then we can create a user with the same group and user id (*nix)
    # so it can run docker without sudo.
    # But guard against errors
    if sudo usermod -u ${HOST_UID} -g ${HOST_GID} seluser; then
        export HOME="/home/seluser"
        export USER="seluser"
        echo -n "stat: /var/run/docker.sock:: "
        if stat --format="%g" /var/run/docker.sock; then
            DOCKER_HOST_GID="$(stat --format="%g" /var/run/docker.sock)"
            if [ ${DOCKER_HOST_GID} != "0" ]; then
                # We create a docker group to which we can add our seluser
                echo -n "sudo groupadd --gid ${DOCKER_HOST_GID} docker:: "
                if sudo groupadd --gid ${DOCKER_HOST_GID} docker; then
                    if getent group ${DOCKER_HOST_GID} | cut -d: -f1; then
                        DOCKER_GROUP_NAME=$(getent group ${DOCKER_HOST_GID} | cut -d: -f1)
                        if [ "${DOCKER_GROUP_NAME}" != "" ]; then
                            if sudo gpasswd -a seluser ${DOCKER_GROUP_NAME}; then
                                __run_with_gosu="true"
                            else
                                log "Error while gpasswd -a seluser"
                            fi
                        else
                            log "Var DOCKER_GROUP_NAME is ${DOCKER_GROUP_NAME}"
                        fi
                    else
                        log "Error while getent group ${DOCKER_HOST_GID}"
                    fi
                else
                    log "Error while sudo groupadd --gid ${DOCKER_HOST_GID} docker"
                fi
            else
                __run_with_gosu="true"
            fi
        else
            log "Error while stat /var/run/docker.sock"
        fi
    else
        log "Error while sudo usermod -u ${HOST_UID} -g ${HOST_GID} seluser"
    fi
fi

if [ "${__run_with_gosu}" == "true" ]; then
    # We still need gosu when accessing the docker.sock
    exec sudo --preserve-env gosu seluser ./zalenium.sh "$@"
else
    # We will need sudo to run docker alongside docker
    # because we don't have the matching group and user id (*nix)
    if [ "${USE_KUBERNETES}" == "false" ]; then
        # Make sure Docker works (with sudo) before continuing
        docker --version
        sudo docker -H ${DOCKER_HOST} images elgalu/selenium >/dev/null
        # Replace the current process with zalenium.sh
        exec sudo --preserve-env ./zalenium.sh "$@"
    else
        # Removing the 'sudo' in Kubernetes
        # Replace the current process with zalenium.sh
        exec ./zalenium.sh "$@"
    fi
fi
