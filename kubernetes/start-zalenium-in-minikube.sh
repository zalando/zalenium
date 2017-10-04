#!/usr/bin/env bash

./kubectl create --validate=false -f kubernetes/serviceAccount.yaml
./kubectl get serviceAccounts
./kubectl create --validate=false -f kubernetes/persistentVolume.yaml
./kubectl get pv
./kubectl create --validate=false -f kubernetes/persistentVolumeClaim.yaml
./kubectl get pvc
./kubectl create --validate=false -f kubernetes/zaleniumDeployment.yaml
./kubectl get deployments
./kubectl create --validate=false -f kubernetes/service.yaml
./kubectl get services

MINIKUBE_IP=$(./minikube ip)
ZALENIUM_GRID_PORT=$(./kubectl get svc zalenium -o go-template='{{ index (index .spec.ports 0) "nodePort" }}{{ "\n" }}')

echo $MINIKUBE_IP:$ZALENIUM_GRID_PORT/wd/hub/status

sleep 30

curl $MINIKUBE_IP:$ZALENIUM_GRID_PORT/wd/hub/status


# this for loop waits until kubectl can access the api server that minikube has created
#KUBECTL_UP="false"
#for i in {1..150} # timeout for 5 minutes
#do
#   ./kubectl get po &> /dev/null
#   if [ $? -ne 1 ]; then
#      KUBECTL_UP="true"
#      break
#  fi
#  sleep 2
#done
#if [ "$KUBECTL_UP" != "true" ]; then
#  echo "INIT FAILURE: kubectl could not reach api-server in allotted time"
#  exit 1
#fi
# kubectl commands are now able to interact with minikube cluster

