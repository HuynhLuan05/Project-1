#!/bin/bash
# Re-render backend services with GCE BackendConfig (health on :8090 /actuator/health/liveness).
set -euo pipefail
cd "$(dirname "$0")"

read -rd '' DOMAIN < <(yq -r '.domain' ./cluster-config.yaml) || DOMAIN=yas.local.com

BACKENDS=(
  cart customer inventory location media order payment product promotion
  rating recommendation search tax webhook sampledata
  backoffice-bff storefront-bff
)

for chart in "${BACKENDS[@]}"; do
  echo "── helm upgrade ${chart} ──"
  helm dependency build "../charts/${chart}"
  helm upgrade --install "${chart}" "../charts/${chart}" \
    --namespace yas \
    --reuse-values
done

echo "── swagger-ui (BackendConfig /swagger-ui/) ──"
helm upgrade --install swagger-ui ../charts/swagger-ui \
  --namespace yas \
  --reuse-values \
  --set ingress.host="api.${DOMAIN}" \
  --set apiDocsBaseUrl="http://api.${DOMAIN}"

echo "Done. GCE may take 5–15 minutes to mark backends HEALTHY."
