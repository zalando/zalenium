#!/usr/bin/env bash


# Taken from https://github.com/aaron-prindle/minikube-travis-example/blob/master/minikube-ci-initialize.sh
# Thanks to https://github.com/aaron-prindle
#
# Copyright 2017 Google, Inc. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

curl -Lo minikube https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64 && chmod +x minikube
curl -Lo kubectl https://storage.googleapis.com/kubernetes-release/release/$(curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt)/bin/linux/amd64/kubectl && chmod +x kubectl

export MINIKUBE_WANTUPDATENOTIFICATION=false
export MINIKUBE_WANTREPORTERRORPROMPT=false
export MINIKUBE_HOME=$HOME
export CHANGE_MINIKUBE_NONE_USER=true
mkdir $HOME/.kube &> /dev/null || true
touch $HOME/.kube/config

export KUBECONFIG=$HOME/.kube/config
sudo -E ./minikube start --vm-driver=none --extra-config=apiserver.InsecureServingOptions.BindAddress="127.0.0.1" --extra-config=apiserver.InsecureServingOptions.BindPort="8080"

# this for loop waits until kubectl can access the api server that minikube has created
KUBECTL_UP="false"
for i in {1..150} # timeout for 5 minutes
do
   ./kubectl get po &> /dev/null
   if [ $? -ne 1 ]; then
      KUBECTL_UP="true"
      break
  fi
  sleep 2
done
if [ "$KUBECTL_UP" != "true" ]; then
  echo "INIT FAILURE: kubectl could not reach api-server in allotted time"
  exit 1
fi
# kubectl commands are now able to interact with minikube cluster

# OPTIONAL depending on kube-dns requirement
# this for loop waits until the kubernetes addons are active
KUBE_ADDONS_UP="false"
for i in {1..150} # timeout for 5 minutes
do
     # Here we are making sure that kubectl is returning the addon pods for the namespace kube-system
     # Without this check, the second if statement won't be in the proper state for execution
     if [[ $(./kubectl get po -n kube-system -l k8s-app=kube-dns | tail -n +2 | grep "kube-dns") ]]; then
       # Here we are taking the checking the number of running pods for the namespace kube-system
       # and making sure that the value on each side of the '/' is equal (ex: 3/3 pods running)
       # this is necessary to ensure that all addons have come up
       if [[ ! $(./kubectl get po -n kube-system | tail -n +2 | awk '{print $2}' | grep -wEv '^([1-9]+)\/\1$') ]]; then
         echo "INIT SUCCESS: all kubernetes addons pods are up and running"
         KUBE_ADDONS_UP="true"
         break
     fi
   fi
  sleep 2
done
if [ "$KUBE_ADDONS_UP" != "true" ]; then
  echo "INIT FAILURE: kubernetes addons did not come up in allotted time"
  exit 1
fi
# kube-addons is available for cluster services
