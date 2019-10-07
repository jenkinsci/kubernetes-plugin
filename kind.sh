#!/bin/bash
set -euxo pipefail
curl -Lo $WSTMP/kind https://github.com/kubernetes-sigs/kind/releases/download/v0.5.1/kind-$(uname)-amd64
chmod +x $WSTMP/kind 
export PATH=$WSTMP:$PATH
kind create cluster --name $BUILD_TAG
trap "kind delete cluster --name $BUILD_TAG" EXIT
export KUBECONFIG="$(kind get kubeconfig-path --name $BUILD_TAG)"
kubectl cluster-info
kubectl run hi --quiet --rm --attach --restart=Never --image=busybox  -- sh -c 'echo hello world'
kind export logs $WSTMP/kindlogs
