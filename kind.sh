#!/bin/bash
set -euxo pipefail
export PATH=$WSTMP:$PATH
if [ \! -f $WSTMP/kind ]
then
    curl -Lo $WSTMP/kind https://github.com/kubernetes-sigs/kind/releases/download/v0.5.1/kind-$(uname)-amd64
    chmod +x $WSTMP/kind
fi
if [ \! -f $WSTMP/kubectl ]
then
    curl -Lo $WSTMP/kubectl https://storage.googleapis.com/kubernetes-release/release/v1.15.3/bin/linux/amd64/kubectl
    chmod +x $WSTMP/kubectl
fi
kind create cluster --name $BUILD_TAG --wait 5m
trap "kind delete cluster --name $BUILD_TAG" EXIT
export KUBECONFIG="$(kind get kubeconfig-path --name $BUILD_TAG)"
kubectl cluster-info
kubectl run hi --quiet --rm --attach --restart=Never --image=busybox  -- sh -c 'echo hello world'
kind export logs --name $BUILD_TAG $WSTMP/kindlogs
