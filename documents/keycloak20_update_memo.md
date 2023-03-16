# Memo about how we updated test realm json files from Keycloak 18 to 20 locally
This is a memo how we updated the test realm json files from Keycloak 18 to 20, which are used for e2e tests.
In summary, we did
1. run Keycloak 18 with empty postgres
2. stop Keycloak 18 and run Keycloak 20 with the same postgres and migrate the data automatically
3. export realm setting as a JSON file from Keycloak UI

## Procedure
Run keycloak version 18 with postgres.
```shell
docker-compose up
```

the docker-compose.yaml file is below
```yaml
volumes:
  postgres_data:
    driver: local
    
services:
  keycloak-console:
    image: quay.io/keycloak/keycloak:18.0.2
    container_name: keycloak-console
    environment:
      KC_DB: postgres
      KC_DB_URL_HOST: postgres
      KC_DB_URL_DATABASE: keycloak
      KC_DB_USERNAME: keycloak
      KC_DB_SCHEMA: public
      KC_DB_PASSWORD: password
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
    command:
      - start-dev
      - --import-realm
      - --http-relative-path=/auth
    volumes:
      - ./test-realm.json:/opt/keycloak/data/import/realm.json
    ports:
      - 8080:8080
    links:
      - postgres

  postgres:
    image: postgres:13.2
    volumes:
      - postgres_data:/var/lib/postgresql/data
    environment:
      POSTGRES_DB: keycloak
      POSTGRES_USER: keycloak
      POSTGRES_PASSWORD: password
    ports:
      - 5432:5432
```

After keycloak started running successfully and the database schema was created in the postgres, stop the docker-compose.
Then changes the docker-compose yaml file as below.

```yaml
volumes:
  postgres_data:
    driver: local

services:    
  keycloak-console:
      image: quay.io/keycloak/keycloak:20.0.5
      container_name: keycloak-console
      environment:
        KC_DB: postgres
        KC_DB_URL_HOST: postgres
        KC_DB_URL_DATABASE: keycloak
        KC_DB_USERNAME: keycloak
        KC_DB_SCHEMA: public
        KC_DB_PASSWORD: password
        KEYCLOAK_ADMIN: admin
        KEYCLOAK_ADMIN_PASSWORD: admin
      ports:
        - '127.0.0.1:8080:8080'
      command: [ "start-dev", "--spi-connections-jpa-quarkus-migration-strategy=update", "--http-relative-path=/auth"]
      links:
        - postgres

  postgres:
    image: postgres:13.2
    volumes:
      - postgres_data:/var/lib/postgresql/data
    environment:
      POSTGRES_DB: keycloak
      POSTGRES_USER: keycloak
      POSTGRES_PASSWORD: password
    ports:
      - 5432:5432
```

After keycloak started running successfully, the database already was migrated to version 20.

- Go to `http://localhost:8080/auth` and log in as the admin.
- export json for the test-realm realm
  - __includes groups, roles and clients__.
- rename the json file into `test-realm.json`
- changes the `secret` value in the `ubirch-2.0-user-access` client part in the json file
    - from `xxxxxx` into `edf3423d-2392-441f-aa81-323de6aadd84`
- put the file in the root directory
- stop docker and rm volumes of postgres properly (docker-compose down -v)

## Test
1. run docker-compose and check if all services start running properly
```shell
docker-compose up
```

2. run the test and check if all tests pass
Change the Keycloak sdk version in the pom and Keycloak test container version in the test.
You need to adjust the values of `keycloak.server` in the `application.test.conf` before running test.
```shell
mvn test
```
