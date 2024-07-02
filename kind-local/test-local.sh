#!/usr/bin/env bash
set -euxo pipefail

gateway_ip=$(docker network inspect kind | jq '[.[].IPAM.Config[].Gateway][0]')
sed "s/@GATEWAY_IP@/$gateway_ip/g" < dockerhost.yaml | kubectl apply -f -

mvn -f ../pom.xml test -Dtest="$@" -Djenkins.host.address=dockerhost -Dhudson.TcpSlaveAgentListener.hostName=dockerhost -DconnectorHost=0.0.0.0
