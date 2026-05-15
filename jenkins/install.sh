#!/usr/bin/env bash
# Installs Jenkins on the GKE cluster, plus the RBAC needed for the
# developer_build / teardown pipelines to drive Helm against the `yas` namespace.
#
# Prerequisites: kubectl + helm on PATH, kubeconfig pointing at the GKE cluster,
# and the `yas` namespace must already exist (created by Member 1).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

NAMESPACE="jenkins"
RELEASE="jenkins"
CHART_REPO="https://charts.jenkins.io"

echo ">>> Ensuring jenkins namespace exists"
kubectl get ns "${NAMESPACE}" >/dev/null 2>&1 \
  || kubectl create namespace "${NAMESPACE}"

echo ">>> Applying RBAC (yas-deployer SA + Role/RoleBinding in yas ns, ClusterRole for node reads)"
kubectl apply -f "${SCRIPT_DIR}/rbac.yaml"

echo ">>> Adding Jenkins Helm repo"
helm repo add jenkins "${CHART_REPO}" >/dev/null 2>&1 || true
helm repo update >/dev/null

echo ">>> Installing/upgrading Jenkins"
helm upgrade --install "${RELEASE}" jenkins/jenkins \
  --namespace "${NAMESPACE}" \
  -f "${SCRIPT_DIR}/values.yaml"

echo
echo ">>> Helm release applied. Watch the pod come up:"
echo "    kubectl -n ${NAMESPACE} get pods -w"
echo "    kubectl -n ${NAMESPACE} rollout status statefulset/${RELEASE} --timeout=25m"

echo
echo ">>> Done. Useful follow-up commands:"
echo "    kubectl -n ${NAMESPACE} get svc ${RELEASE}      # LoadBalancer EXTERNAL-IP"
echo "    kubectl -n ${NAMESPACE} exec -it ${RELEASE}-0 -c jenkins -- /bin/cat /run/secrets/additional/chart-admin-password"
