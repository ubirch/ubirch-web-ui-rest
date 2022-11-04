# JanusGraph connection test

This file describes how to check locally this service can properly connect JanusGraph.

## Keycloak Setup

See [how_to_upload_csv_locally.md#Keycloak Setup section](how_to_upload_csv_locally.md).

## JanusGraph Setup
```shell
# clone the discovery service if you didn't yet
git clone git@github.com:ubirch/ubirch-discovery-service.git

# go the discovery service
cd ubirch-discovery-service

# run JanusGraph
sh src/test/resources/embedded-jg/janusgraph-0.6.2/bin/janusgraph-server.sh console ../custom-gremlin-conf.yaml start

# go to the console of JanusGraph
sh src/test/resources/embedded-jg/janusgraph-0.6.2/bin/gremlin.sh

# inside of the JanusGraph console
gremlin> :rem connect tinkerpop.server ../custom-remote.yaml session
```

## Call device endpoints

```shell
# run the service
mvn exec:java -Dexec.mainClass=com.ubirch.webui.Boot

token=`curl -s -d "client_id=admin-cli" -d "username=chrisx" -d "password=password" -d "grant_type=password" "http://localhost:8080/auth/realms/test-realm/protocol/openid-connect/token" | jq -r .access_token`
# curl -X POST -v -H "Content-Type: application/json" -H "authorization: bearer $token" http://localhost:8081/ubirch-web-ui/api/v1/devices/state/:from/:to  -d ':deviceIds'
# devices should be owned by the user(In the example, the user is chrisx)
# the device should belong to OWN_DEVICES_chrisx
curl -X POST -v -H "Content-Type: application/json" -H "authorization: bearer $token" http://localhost:8081/ubirch-web-ui/api/v1/devices/state/2022-10-01/2022-11-05 -d '42956ef1-307e-49c8-995c-9b5b757828cd,3b3da0c2-e97e-4832-9bcb-29e886aeb5a6'
```
