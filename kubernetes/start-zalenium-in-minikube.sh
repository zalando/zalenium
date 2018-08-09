#!/usr/bin/env bash

kubectl create --validate=false -f kubernetes
kubectl get serviceAccounts
kubectl get pv
kubectl get pvc
kubectl get deployments
kubectl get services

MINIKUBE_IP=$(minikube ip)
ZALENIUM_GRID_PORT=$(kubectl get svc zalenium --namespace zalenium -o go-template='{{ index (index .spec.ports 0) "nodePort" }}{{ "\n" }}')

echo $MINIKUBE_IP:$ZALENIUM_GRID_PORT/wd/hub/status

ZALENIUM_UP="false"
# Waiting for 5 minutes
WAIT_UNTIL=$((SECONDS + 300))
while [ $SECONDS -lt ${WAIT_UNTIL} ]; do
    curl -sSL $MINIKUBE_IP:$ZALENIUM_GRID_PORT/wd/hub/status 2>&1 \
            | jq -r '.value.ready' 2>&1 | grep "true" >/dev/null

    if [ $? -ne 1 ]; then
        ZALENIUM_UP="true"
        break
    fi
    echo -n '.'

    sleep 2
done

curl $MINIKUBE_IP:$ZALENIUM_GRID_PORT/wd/hub/status

if [ "$ZALENIUM_UP" != "true" ]; then
    echo "FAILURE starting Zalenium..."
    exit 1
fi

