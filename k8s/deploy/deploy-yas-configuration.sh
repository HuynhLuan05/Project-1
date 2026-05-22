
set -x

DOMAIN="${DOMAIN:-yas.local.com}"
IDENTITY_PUBLIC_BASE_URL="${IDENTITY_PUBLIC_BASE_URL:-http://identity.${DOMAIN}}"
IDENTITY_INTERNAL_BASE_URL="${IDENTITY_INTERNAL_BASE_URL:-http://keycloak-service.keycloak.svc.cluster.local}"
REALM="${REALM:-Yas}"
ISSUER_URI_INTERNAL="${IDENTITY_INTERNAL_BASE_URL}/realms/${REALM}"
AUTHORIZATION_URL_PUBLIC="${IDENTITY_PUBLIC_BASE_URL}/realms/${REALM}/protocol/openid-connect/auth"
TOKEN_URL_PUBLIC="${IDENTITY_PUBLIC_BASE_URL}/realms/${REALM}/protocol/openid-connect/token"
TOKEN_URL_INTERNAL="${IDENTITY_INTERNAL_BASE_URL}/realms/${REALM}/protocol/openid-connect/token"
JWK_SET_URI_INTERNAL="${IDENTITY_INTERNAL_BASE_URL}/realms/${REALM}/protocol/openid-connect/certs"
USER_INFO_URI_INTERNAL="${IDENTITY_INTERNAL_BASE_URL}/realms/${REALM}/protocol/openid-connect/userinfo"
END_SESSION_ENDPOINT_PUBLIC="${IDENTITY_PUBLIC_BASE_URL}/realms/${REALM}/protocol/openid-connect/logout"

# Auto restart when change configmap or secret
helm repo add stakater https://stakater.github.io/stakater-charts
helm repo update

helm dependency build ../charts/yas-configuration
helm upgrade --install yas-configuration ../charts/yas-configuration \
--namespace yas --create-namespace \
--set applicationConfig.spring.security.oauth2.resourceserver.jwt.issuer-uri="${ISSUER_URI_INTERNAL}" \
--set applicationConfig.springdoc.oauthflow.authorization-url="${AUTHORIZATION_URL_PUBLIC}" \
--set applicationConfig.springdoc.oauthflow.token-url="${TOKEN_URL_PUBLIC}" \
--set-string backofficeBffExtraConfig.spring.security.oauth2.client.provider.keycloak.issuer-uri= \
--set backofficeBffExtraConfig.spring.security.oauth2.client.provider.keycloak.authorization-uri="${AUTHORIZATION_URL_PUBLIC}" \
--set backofficeBffExtraConfig.spring.security.oauth2.client.provider.keycloak.token-uri="${TOKEN_URL_INTERNAL}" \
--set backofficeBffExtraConfig.spring.security.oauth2.client.provider.keycloak.jwk-set-uri="${JWK_SET_URI_INTERNAL}" \
--set backofficeBffExtraConfig.spring.security.oauth2.client.provider.keycloak.user-info-uri="${USER_INFO_URI_INTERNAL}" \
--set backofficeBffExtraConfig.spring.security.oauth2.client.provider.keycloak.user-name-attribute="preferred_username" \
--set backofficeBffExtraConfig.spring.security.oauth2.client.provider.keycloak.configuration-metadata.end_session_endpoint="${END_SESSION_ENDPOINT_PUBLIC}" \
--set-string storefrontBffExtraConfig.spring.security.oauth2.client.provider.keycloak.issuer-uri= \
--set storefrontBffExtraConfig.spring.security.oauth2.client.provider.keycloak.authorization-uri="${AUTHORIZATION_URL_PUBLIC}" \
--set storefrontBffExtraConfig.spring.security.oauth2.client.provider.keycloak.token-uri="${TOKEN_URL_INTERNAL}" \
--set storefrontBffExtraConfig.spring.security.oauth2.client.provider.keycloak.jwk-set-uri="${JWK_SET_URI_INTERNAL}" \
--set storefrontBffExtraConfig.spring.security.oauth2.client.provider.keycloak.user-info-uri="${USER_INFO_URI_INTERNAL}" \
--set storefrontBffExtraConfig.spring.security.oauth2.client.provider.keycloak.user-name-attribute="preferred_username" \
--set storefrontBffExtraConfig.spring.security.oauth2.client.provider.keycloak.configuration-metadata.end_session_endpoint="${END_SESSION_ENDPOINT_PUBLIC}" \
--set customerApplicationConfig.keycloak.auth-server-url="${IDENTITY_INTERNAL_BASE_URL}"
    
