# Ubirch web admin UI API

REST API for the new Ubirch web admin interface.

## Getting started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes. See deployment for notes on how to deploy the project on a live system.

If you want to do test for `api/v1/devices/batch` endpoint locally, follow [this instruction](./documents/how_to_upload_csv_locally.md).

### Prerequisites

What things you need to run the REST api and how to install them.

* **Keycloak server:** 
    * Get the KeyCloak server zip [here](https://www.keycloak.org/downloads.html), extract it and run ```bin/standalone.sh```. This will start a local KeyCloak instance on port 8080. Configure it by logging in the KeyCloak web admin interface [here](http://localhost:8080/auth) and creating a new admin user.
    * Create a new realm, called **test-realm**.
    * In this realm, create a new client called _ubirch-2.0-user-access-local_ and configure it with the following informations:
        * Root URL: http://localhost:9101
        * Valid Redirect URIs: http://localhost:9101/\*
        * Admin URL: http://localhost:9101/
        * Web Origins: http://localhost:9101/
        * Access Token Signature Algorithm: ES256
        * ID Token Signature Algorithm: ES256
        * User Info Signed Response Algorithm: None
    * Access the jwk of the newly created client [here](http://localhost:8080/auth/realms/test-realm/protocol/openid-connect/certs) and pass it in core/src/main/resources/application.base.conf. Also change the relevant information in this file if need be.
* **PostMan** (optional). Provide an easy way to send REST requests to the server and obtaining access token delivered by KeyCloak. Installation instructions can be found on the project [webpage](https://www.getpostman.com/downloads/).

### Starting the project

This project can be started by executing the main function in the com.ubirch.webui.server.Boot class.

## Running the tests
When running tests locally you have to adjust 2 values related to keycloak in the application.test.conf file as
described in that file. Change it back before committing, as our build pipeline requires different values for now.

Then just run: 
```mvn clean test```.


## Deployment
A docker image can be found as ubirch/web-admin-api-server:latest. A new one can be created automatically through the spotify docker maven plugin by running ```mvn install```.

Helm charts are provided to deploy the system easily. You can modify their parameters in helm-charts/webui-api/values.yaml.
 
## Built With
Scalatra - The web framework used.
Maven - Dependency Management.
KeyCloak - The user management system.
