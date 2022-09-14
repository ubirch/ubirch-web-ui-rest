# Memo about how updated test-realm.json from Keycloak 15 to 18 locally
This is a memo how we updated the `test-realm.json` from Keycloak 15 to 18, which is used for e2e tests.
In summary, we ran Keycloak with the initial setting and setup manually via Keycloak UI, then exported the setting as `test-realm.json`.
This way was actually not efficient. We should find a more efficient way such as using migration script that Keycloak normally provides.

## 1. run keycloak 18 without importing test-realm
Run Keycloak 18 with initial setting by docker-compose.
```yaml
keycloak-console:
    image: quay.io/keycloak/keycloak:18.0.2
    container_name: keycloak-console
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
    command:
      - start-dev
      - --http-relative-path=/auth
    ports:
      - 8080:8080
```

## 2. setup manually
Setup Keycloak for our test setting manually via Keycloak UI.
You can log in the Keycloak administration console by using the `KEYCLOAK_ADMIN` and `KEYCLOAK_ADMIN_PASSWORD` values.
The url is `http://localhost:8080/auth`.

- create test-realm
  - displayName -> ubirch web ui login for tenant
 
- create ES Key
  - Realm Settings -> Keys -> Providers -> ecdsa-generated -> create
  - Tokens -> change default signature algorithm into ES256

- roles
  - create USER role

- clients
  - create protocolMappers for admin-cli
    - name: groups
    - Mapper Type: Group Membership 
    - Token Claim Name: groups
    - all flags are 'ON'
  - create ubirch-2.0-user-access client
    - name: Ubirch AdminUI 2.0
    - description: Ubirch AdminUI 2.0 auf localhost
    - rootUrl: http://localhost:9101
    - adminUrl: http://localhost:9101
    - redirectUris: http://localhost:9101/*, https://localhost:8080/auth/realms/test-realm/broker/ubirch-2.0-keycloak-users/endpoint
    - webOrigins: http://localhost:9101
    - Access Token Signature Algorithm: ES256
    - ID Token Signature Algorithm: ES256
    - create protocolMappers
      - name: groups
      - Mapper Type: Group Membership
      - Token Claim Name: groups
      - all flags are 'ON'
  - create ubirch-device-access
    - Access Type: confidential
    - Direct Access Grants Enabled: ON
    - Standard Flow Enabled: ON
    - Authorization Enabled: ON
- groups
  - create TENANTS_ubirch
    - create TENANT_size under that
      - create TENANT_OU_default, TENANT_OU_small, TENANT_OU_medium and TENANT_OU_large under that
  - [subgroups reference](https://hn-docs.readthedocs.io/en/latest/administrator/groups.html)

- Identity Providers
  - create ubirch-2.0-centralised-user-auth
    - providerId: keycloak-oidc
    - alias: ubirch-2.0-centralised-user-auth
    - displayName: ubirch 2.0 Centralised User Login
    - enabled: ON
    - trustEmail: ON
    - storeToken: ON
    - validateSignature: true,
    - clientId: test-realm-connector,
    - tokenUrl: http://localhost:8080/realms/test-realm/protocol/openid-connect/token,
    - authorizationUrl: http://localhost:8080/realms/test-realm/protocol/openid-connect/auth,
    - clientAuthMethod: client_secret_post,
    - jwksUrl: http://localhost:8080/realms/test-realm/protocol/openid-connect/certs,
    - logoutUrl: http://localhost:8080/realms/test-realm/protocol/openid-connect/logout,
    - syncMode: IMPORT,
    - clientSecret: centralised_user_auth_secret,
    - issuer: https://localhost:8080/realms/test-realm,
    - useJwksUrl: "true"

## 3. export realm setting
Export the realm setting as a JSON file by the `Export` page in Keycloak.
Put a check mark on `Export groups and roles` and `Export clients`.

## 4. change the test-realm.json manually
After export the realm setting as a JSON file, rename the file into `test-realm.json` and put it in the root directory.

- set secret into the `Ubirch AdminUI 2.0` client
```
"secret": "edf3423d-2392-441f-aa81-323de6aadd84"
```
