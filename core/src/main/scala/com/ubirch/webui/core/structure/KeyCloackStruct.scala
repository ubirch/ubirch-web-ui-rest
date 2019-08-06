package com.ubirch.webui.core.structure

case class ClientRepresentation(access: Map[String, String] = null,
                                adminUrl: String = null,
                                attributes: Map[String, String] = null,
                                authenticationFlowBindingOverrides: Map[String, String] = null,
                                authorizationServicesEnabled: Boolean = false,
                                authorizationSettings: ResourceRepresentation = null,
                                baseUrl: String = null,
                                bearerOnly: String = null,
                                clientAuthenticatorType: String = null,
                                clientId: String = null,
                                consentRequired: Boolean = false,
                                defaultClientScopes: List[String] = null,
                                defaultRoles: List[String] = null,
                                description: String = null,
                                directAccessGrantsEnabled: Boolean = false,
                                enabled: Boolean = false,
                                frontchannelLogout: Boolean = false,
                                fullScopeAllowed: Boolean = false,
                                id: String = null,
                                implicitFlowEnabled: Boolean = false,
                                name: String = null,
                                nodeReRegistrationTimeout: Int = 0,
                                notBefore: Int = 0,
                                optionalClientScopes: List[String] = null,
                                origin: String,
                                protocol: String,
                                protocolMappers: List[ProtocolMapperRepresentation] = null,
                                publicClient: Boolean = false,
                                redirectUris: List[String] = null,
                                registeredNodes: Map[String, String] = null,
                                registrationAccessToken: String = null,
                                rootUrl: String = null,
                                secret: String = null,
                                serviceAccountsEnabled: Boolean = false,
                                standardFlowEnabled: Boolean = false,
                                surrogateAuthRequired: Boolean = false,
                                webOrigins: List[String] = null

                               )

case class ResourceServerRepresentation(allowRemoteResourceManagement: Boolean = false,
                                        clientId: String = null,
                                        id: String = null,
                                        name: String = null,
                                        policies: PolicyRepresentation = null,
                                        policyEnforcementMode: String = null,
                                        resources: ResourceRepresentation = null,
                                        scopes: ScopeRepresentation = null
                                       )

case class PolicyRepresentation(config: Map[String, String] = null,
                                decisionStrategy: String = null,
                                description: String = null,
                                id: String = null,
                                logic: String = null,
                                owner: String = null,
                                policies: List[String] = null,
                                resources: List[String] = null,
                                scopes: List[String] = null,
                                theType: String = null
                               )

case class ResourceRepresentation(id: String = null,
                                  attributes: Map[String, String] = null,
                                  displayName: String = null,
                                  icon_uri: String = null,
                                  Name: String = null,
                                  ownerManagedAccess: Boolean = false,
                                  scopes: List[ScopeRepresentation] = Nil,
                                  theType: String = null,
                                  uris: List[String] = Nil
                                 )

case class ScopeRepresentation(displayName: String = null,
                               iconUri: String = null,
                               id: String = null,
                               name: String = null,
                               policies: PolicyRepresentation = null,
                               resources: ResourceRepresentation = null
                              )

case class ProtocolMapperRepresentation(config: Map[String, String] = null,
                                        id: String = null,
                                        name: String = null,
                                        protocol: String = null,
                                        protocolMapper: String = null
                                       )