# How to run locally

- Keycloak
- IdentityService 
- Cassandra

```shell
docker-compose up
# if you want to run the containers as daemon
docker-compose up -d
```

## Keycloak Setup

Go to [PopulateRealm object](../src/test/scala/com/ubirch/webui/PopulateRealm.scala) and call the `doIt` method by adding a main method.
```scala
def main(args: Array[String]): Unit = {
  doIt()
}
```

## Upload batch
```shell
# run the service
mvn exec:java -Dexec.mainClass=com.ubirch.webui.Boot

token=`curl -s -d "client_id=admin-cli" -d "username=elCarlos" -d "password=password" -d "grant_type=password" "http://localhost:8080/auth/realms/test-realm/protocol/openid-connect/token" | jq -r .access_token`
curl -v -H "authorization: bearer $token" -F 'file=@${csv file path}' -F 'batch_type=sim_import' -F 'skip_header=true' -F 'batch_description=This is a description' -F 'batch_tags=tag1, tag2, tag3' -F 'batch_provider=sim' http://localhost:8081/ubirch-web-ui/api/v1/devices/batch
```
