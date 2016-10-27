#!/usr/bin/env bash

# To run docker alongside docker we still need sudo inside the container
# TODO: this can potentially be fixed perhaps by passing $UID
#       during docker run time.
exec sudo --preserve-env ./zalenium.sh "$@"
