#!/usr/bin/env bash
set -euxo pipefail
cd $(dirname $0)

: ${WORKSPACE_TMP:=/tmp}

machine_name=$(uname -m)
if [ "$machine_name" = "x86_64" ]
then
    arch=amd64
else
    arch=$machine_name
fi
export PATH=$WORKSPACE_TMP:$PATH
if [ \! -x "$WORKSPACE_TMP/kind" ]
then
    curl -sLo "$WORKSPACE_TMP/kind" "https://github.com/kubernetes-sigs/kind/releases/download/v0.23.0/kind-$(uname | tr '[:upper:]' '[:lower:]')-${arch}"
    chmod +x "$WORKSPACE_TMP/kind"
fi
if [ \! -x "$WORKSPACE_TMP/kubectl" ]
then
    curl -sLo "$WORKSPACE_TMP/kubectl" "https://storage.googleapis.com/kubernetes-release/release/v1.30.1/bin/$(uname | tr '[:upper:]' '[:lower:]')/${arch}/kubectl"
    chmod +x "$WORKSPACE_TMP/kubectl"
fi
if [ \! -x "$WORKSPACE_TMP/ktunnel" ]
then
    (cd "$WORKSPACE_TMP"; curl -sL "https://github.com/omrikiei/ktunnel/releases/download/v1.6.1/ktunnel_1.6.1_$(uname)_${machine_name}.tar.gz" | tar xvfz - ktunnel)
fi

export cluster=ci$RANDOM
export KUBECONFIG="$WORKSPACE_TMP/kubeconfig-$cluster"
kind create cluster --name $cluster --wait 5m
function cleanup() {
    set +e
    if [ -v ktunnel_pid ] && ps -p $ktunnel_pid > /dev/null
    then
        kill $ktunnel_pid
    fi
    kind export logs --name $cluster "$WORKSPACE_TMP/kindlogs"
    kind delete cluster --name $cluster
    rm "$KUBECONFIG"
}
trap cleanup EXIT
kubectl cluster-info

if ${KIND_PRELOAD:-false}
then
   ./kind-preload.sh
fi

ktunnel expose jenkins 8000:8000 8001:8001 &
ktunnel_pid=$!

mvn \
    --show-version \
    -B \
    -ntp \
    -Djenkins.host.address=jenkins.default \
    -Dport=8000 \
    -DslaveAgentPort=8001 \
    -Dmaven.test.failure.ignore \
    verify \
    "$@"
