
set -x

# Auto restart when change configmap or secret
helm repo add stakater https://stakater.github.io/stakater-charts
helm repo update

helm dependency build ../charts/yas-configuration
helm upgrade --install yas-configuration ../charts/yas-configuration \
--namespace yas --create-namespace \
--set applicationConfig.spring.security.oauth2.resourceserver.jwt.issuer-uri="http://keycloak-service.keycloak.svc.cluster.local/realms/Yas" \
--set applicationConfig.springdoc.oauthflow.authorization-url="http://keycloak-service.keycloak.svc.cluster.local/realms/Yas/protocol/openid-connect/auth" \
--set applicationConfig.springdoc.oauthflow.token-url="http://keycloak-service.keycloak.svc.cluster.local/realms/Yas/protocol/openid-connect/token" \
--set backofficeBffExtraConfig.spring.security.oauth2.client.provider.keycloak.issuer-uri="http://keycloak-service.keycloak.svc.cluster.local/realms/Yas" \
--set storefrontBffExtraConfig.spring.security.oauth2.client.provider.keycloak.issuer-uri="http://keycloak-service.keycloak.svc.cluster.local/realms/Yas" \
--set customerApplicationConfig.keycloak.auth-server-url="http://keycloak-service.keycloak.svc.cluster.local"
    