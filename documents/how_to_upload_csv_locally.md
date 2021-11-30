# How to upload csv locally

The `api/v1/devices/batch` endpoint enables us to upload a csv to register device on Keycloak and store identity(with the Identity Service) via Kafka.

```shell
docker-compose up
```

## Keycloak Setup

Go to [PopulateRealm object](../src/test/scala/com/ubirch/webui/PopulateRealm.scala) and run the `main` function

## Upload batch
```shell
# run the service
mvn exec:java -Dexec.mainClass=com.ubirch.webui.Boot

token=`curl -s -d "client_id=admin-cli" -d "username=elCarlos" -d "password=password" -d "grant_type=password" "http://localhost:8080/auth/realms/test-realm/protocol/openid-connect/token" | jq -r .access_token`
curl -v -H "authorization: bearer $token" -F 'file=@${csv file path}' -F 'batch_type=sim_import' -F 'skip_header=true' -F 'batch_description=This is a description' -F 'batch_tags=tag1, tag2, tag3' -F 'batch_provider=sim' http://localhost:8081/ubirch-web-ui/api/v1/devices/batch
```

## With Identity service

If you want to store identities with the Identity service, you need to run the identity service and Cassandra.
In order to run the Identity service, refer to [Quick Dev Start-Up](https://github.com/ubirch/ubirch-id-service#quick-dev-start-up).

__Note__: you need to comment out the Kafka container part in the docker-compose, if you already run Kafka locally. Moreover, you need to change the server port as both of services are using 8081.
