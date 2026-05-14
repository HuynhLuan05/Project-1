#!/bin/bash
set -e

# ==============================================================================
# Script cài đặt công cụ: ArgoCD và Istio Service Mesh
# ==============================================================================

echo "Bắt đầu cài đặt ArgoCD..."
kubectl create namespace argocd || true
kubectl apply --server-side --force-conflicts -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

echo "Đợi ArgoCD server khởi động..."
kubectl wait --for=condition=available deployment/argocd-server -n argocd --timeout=300s

echo "Bắt đầu cài đặt Istio..."
# Cài đặt qua Helm
helm repo add istio https://istio-release.storage.googleapis.com/charts
helm repo update

# Cài Istio Base (CRDs)
kubectl create namespace istio-system || true
helm install istio-base istio/base -n istio-system --set defaultRevision=default || helm upgrade istio-base istio/base -n istio-system

# Cài Istiod (Control Plane)
helm install istiod istio/istiod -n istio-system --wait || helm upgrade istiod istio/istiod -n istio-system --wait

# (Tùy chọn) Cài Kiali để quan sát
echo "Bắt đầu cài đặt Kiali Dashboard..."
helm repo add kiali https://kiali.org/helm-charts
kubectl apply -f https://raw.githubusercontent.com/istio/istio/release-1.20/samples/addons/prometheus.yaml # Cần prometheus cho Kiali
helm install kiali-server kiali/kiali-server -n istio-system --set service.type=NodePort || helm upgrade kiali-server kiali/kiali-server -n istio-system

echo "============================================================"
echo "Cài đặt hoàn tất"
echo "Bật Istio sidecar injection cho namespace dev và staging..."
kubectl create namespace dev || true
kubectl create namespace staging || true
kubectl label namespace dev istio-injection=enabled --overwrite
kubectl label namespace staging istio-injection=enabled --overwrite
echo "Để lấy password ArgoCD, chạy lệnh: kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d"
echo "Để map port ArgoCD: kubectl port-forward svc/argocd-server -n argocd 8080:443"
echo "Để map port Kiali: kubectl port-forward svc/kiali -n istio-system 20001:20001"
echo "============================================================"

