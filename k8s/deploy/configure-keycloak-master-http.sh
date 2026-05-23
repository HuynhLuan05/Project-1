set -e
set -x

NAMESPACE="${NAMESPACE:-keycloak}"
POD_NAME="${POD_NAME:-keycloak-0}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:80}"
ADMIN_USERNAME="$(
  kubectl -n "${NAMESPACE}" get secret keycloak-credentials -o jsonpath="{.data.username}" | base64 -d
)"
ADMIN_PASSWORD="$(
  kubectl -n "${NAMESPACE}" get secret keycloak-credentials -o jsonpath="{.data.password}" | base64 -d
)"

kubectl -n "${NAMESPACE}" wait --for=condition=ready "pod/${POD_NAME}" --timeout=600s

kubectl -n "${NAMESPACE}" exec "${POD_NAME}" -- sh -c '
  set -e

  /opt/keycloak/bin/kcadm.sh config credentials \
    --server "'"${KEYCLOAK_URL}"'" \
    --realm master \
    --user "'"${ADMIN_USERNAME}"'" \
    --password "'"${ADMIN_PASSWORD}"'"

  /opt/keycloak/bin/kcadm.sh update realms/master -s sslRequired=NONE || \
  /opt/keycloak/bin/kcadm.sh update realms/master -s sslRequired=none
'
