#!/usr/bin/env bash
set -euxo pipefail
cd $(dirname $0)

m2=$(dirname $(mvn help:evaluate -Dexpression=settings.localRepository -q -DforceStdout))
cfg=$(mktemp --tmpdir kind-XXXXX.yaml)
trap "rm -f $cfg" EXIT
cat >"$cfg" <<EOF
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
- role: control-plane
  extraMounts:
  - hostPath: $m2
    containerPath: /m2
EOF
if [[ -v cluster ]]
then
  cat >>"$cfg" <<EOF
name: $cluster
EOF
fi
kind create cluster --config "$cfg" --wait 5m
