## 📌 Cluster Information

| Item             | Value                          |
|------------------|-------------------------------|
| **Project ID**   | `decent-seeker-496610-k1`     |
| **Cluster Name** | `yas-cluster`                 |
| **Location**     | `us-central1-a`               |
| **Namespaces**   | `yas`, `keycloak`, `observability`, `postgres`, `kafka` |

***

## Prerequisites

Make sure the following tools are installed on your machine before starting:

- [gcloud CLI](https://cloud.google.com/sdk/docs/install)
- [kubectl](https://kubernetes.io/docs/tasks/tools/)
- [helm](https://helm.sh/docs/intro/install/) ≥ 3.13
- [yq](https://github.com/mikefarah/yq#install) ≥ 4.x

You also need:

- access to the shared Google Cloud project `decent-seeker-496610-k1`
- permission to read or manage the GKE cluster `yas-cluster`

This guide assumes all teammates already have Google Cloud access. You do **not** need a service account JSON key or `key.json` file.

***

## Step 1 — Install gcloud CLI

### Linux / WSL (Ubuntu)

```bash
sudo apt-get update
sudo apt-get install -y apt-transport-https ca-certificates gnupg curl

curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | sudo gpg --dearmor -o /usr/share/keyrings/cloud.google.gpg

echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main" | \
  sudo tee -a /etc/apt/sources.list.d/google-cloud-sdk.list

sudo apt-get update && sudo apt-get install -y google-cloud-cli
```

### macOS

```bash
brew install --cask google-cloud-sdk
```

### Windows

Download and run the installer from:
https://cloud.google.com/sdk/docs/install#windows

***

## Step 2 — Install kubectl

### Linux / WSL

```bash
sudo apt-get install -y kubectl
```

### macOS

```bash
brew install kubectl
```

### Windows

```bash
gcloud components install kubectl
```

***

## Step 3 — Authenticate and Connect to the Cluster

Run the following commands **in order**:

```bash
# 1. Log in with your Google account
gcloud auth login

# 2. Set the GCP project
gcloud config set project decent-seeker-496610-k1

# 3. Fetch kubeconfig credentials for the GKE cluster
gcloud container clusters get-credentials yas-cluster \
  --zone us-central1-a \
  --project decent-seeker-496610-k1

# 4. Verify the connection
kubectl config current-context
kubectl get nodes
kubectl get pods -n yas
```

If you are using WSL and browser login does not open cleanly, use:

```bash
gcloud auth login --no-launch-browser
```

### Expected Output

```
NAME                                       STATUS   ROLES    AGE
gke-yas-cluster-...     Ready    <none>   ...

NAME                          READY   STATUS    RESTARTS
product-68b9c8c5d4-xxxxx      1/1     Running   0
...
```

***

## Step 4 — Add Entries to `/etc/hosts`

The cluster exposes public services through **GCE Ingress / Load Balancers**. You need to map the ingress IPs to the local hostnames.

### Get the Ingress IPs

```bash
kubectl get ingress -A
```

Expected public hosts:

- `storefront.yas.local.com`
- `backoffice.yas.local.com`
- `api.yas.local.com`
- `identity.yas.local.com`
- `grafana.yas.local.com`

### Linux / WSL

```bash
sudo nano /etc/hosts
```

Add the following lines, replacing each placeholder with the ingress `ADDRESS` from `kubectl get ingress -A`:

```
<YAS_INGRESS_IP>       storefront.yas.local.com
<YAS_INGRESS_IP>       backoffice.yas.local.com
<YAS_INGRESS_IP>       api.yas.local.com
<KEYCLOAK_INGRESS_IP>  identity.yas.local.com
<GRAFANA_INGRESS_IP>   grafana.yas.local.com
```

Example from the current cluster:

```text
8.233.85.27 storefront.yas.local.com
8.233.85.27 backoffice.yas.local.com
8.233.85.27 api.yas.local.com
8.233.208.49 identity.yas.local.com
8.233.82.194 grafana.yas.local.com
```

### Windows

Open `C:\Windows\System32\drivers\etc\hosts` using **Notepad as Administrator**, then add the same lines above.

***

## Step 5 — Deploy YAS Applications

Navigate to the `k8s/deploy` folder:

```bash
cd k8s/deploy
```

### 5a. Add Helm Repositories

```bash
helm repo add postgres-operator-charts https://opensource.zalando.com/postgres-operator/charts/postgres-operator
helm repo add akhq https://akhq.io/
helm repo add elastic https://helm.elastic.co
helm repo add grafana https://grafana.github.io/helm-charts
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts
helm repo add jetstack https://charts.jetstack.io
helm repo update
```

### 5b. Install Infrastructure Services

```bash
# Identity provider (Keycloak)
./setup-keycloak.sh

# Redis (session store for BFF services)
./setup-redis.sh

# PostgreSQL, Kafka, Elasticsearch, Prometheus/Grafana
./setup-cluster.sh
```

Wait for all infrastructure pods to be `Running` before continuing:

```bash
kubectl get pods -n keycloak -w
kubectl get pods -n postgres -w
kubectl get pods -n kafka -w
kubectl get pods -n observability -w
```

> ⏱️ This can take **10–20 minutes** on a fresh GKE cluster.

If the shared cluster is already up and you only need to use the system for observability or testing, you can skip the deployment steps and go directly to Step 6 after Step 4.

### 5c. Deploy All YAS Microservices

```bash
./deploy-yas-applications.sh
```

This deploys the YAS applications into the `yas` namespace.

Monitor deployment progress:

```bash
kubectl get pods -n yas -w
# Wait until all pods show 1/1 Running
```

### 5d. Apply Public Ingress

```bash
kubectl apply -f api-gateway-nodeport.yaml
kubectl apply -f yas-ingress.yaml
```

Notes:

- `yas-ingress.yaml` exposes `storefront`, `backoffice`, and `api` through a GCE load balancer.
- Keycloak public access is created by `setup-keycloak.sh`.
- Grafana public access is created from `observability/prometheus.values.yaml`.

Verify the public ingress:

```bash
kubectl get ingress -A
```

***

## Step 6 — Access the Services

Once `/etc/hosts` is configured (Step 4) and the ingresses are ready (Step 5d), open your browser:

| Service | URL | Description |
|---------|-----|-------------|
| 🛒 **Storefront** | `http://storefront.yas.local.com/` | Customer-facing shopping site |
| 🏢 **Backoffice** | `http://backoffice.yas.local.com/` | Admin & merchant management panel |
| 🔐 **Identity** | `http://identity.yas.local.com/` | Keycloak — authentication & authorization |
| 📖 **Swagger UI** | `http://api.yas.local.com/swagger-ui/` | REST API documentation |
| 📊 **Grafana** | `http://grafana.yas.local.com/` | Observability UI |

> 💡 **First run note:** Storefront and Backoffice may fail on the very first startup while waiting for Keycloak to be ready. Wait 2–3 minutes and refresh the page.

If a teammate only needs to observe the running system, the minimum setup is:

1. Step 3: authenticate and get cluster credentials
2. Step 4: add hosts file entries
3. Step 6: open the public URLs
4. Step 7: use Grafana / Tempo / Prometheus

### Default Credentials

| Service | Username | Password | Notes |
|---------|----------|----------|-------|
| **Backoffice** | `admin` | `password` | Authenticated via Keycloak |
| **Keycloak Admin Console** | `admin` | `admin` | http://identity.yas.local.com/ |
| **Grafana** | `admin` | `admin` | |
| **PostgreSQL** | `yasadminuser` | `admin` | Port `5432` |

***

## Step 7 — Observability

YAS is fully instrumented with **OpenTelemetry**. Every service automatically exports **logs**, **traces**, and **metrics** through the OpenTelemetry Collector to the observability stack.

### Architecture Overview

```
YAS Microservices (Java 21 / Spring Boot 3)
          │
          │  OTLP gRPC :5555 / HTTP :6666
          ▼
  OpenTelemetry Collector
     ┌────┴──────────┐
     ▼               ▼
 Prometheus        Tempo
 (Metrics)        (Traces)
                          ▲
Loki ◄── Promtail (Logs)  │
  │                       │
  └─────── Grafana ────────┘
```

```bash
# List all configured contexts
kubectl config get-contexts

### 7a. Access Grafana

**Option 1 — Public GCE ingress:**

Open: **http://grafana.yas.local.com**

**Option 2 — Port-forward:**

```bash
kubectl port-forward svc/prometheus-grafana 3000:80 -n observability
```

Open: **http://localhost:3000** → Login: `admin` / `admin`

***

### 7b. View Logs — Loki

All application logs are collected by **Promtail** and stored in **Loki**.

**Steps in Grafana:**

1. Left sidebar → **Explore** (compass icon)
2. Datasource dropdown → select **Loki**
3. Set **Label filters**:
   - `namespace` = `yas`
   - `container` = service name (e.g. `product`, `order`, `cart`)
4. Click **Run query**

**Useful LogQL queries:**

```logql
# All logs from the yas namespace
{namespace="yas"}

# Errors only
{namespace="yas"} |= "ERROR"

# Logs for a specific service
{namespace="yas", container="order"}

# Find logs by trace ID (to correlate with Tempo)
{namespace="yas"} |= "traceId=<YOUR_TRACE_ID>"

# Logs from keycloak
{namespace="keycloak"}
```

**Stream logs live (CLI):**

```bash
# All services in yas namespace
kubectl logs -n yas -l app.kubernetes.io/part-of=yas -f

# Specific service
kubectl logs -n yas -l app.kubernetes.io/name=product -f
```

---

### 7c. View Traces — Tempo

Tempo stores distributed traces, letting you follow a single HTTP request across all microservices.

**Steps in Grafana:**

1. Left sidebar → **Explore**
2. Datasource dropdown → select **Tempo**
3. Search by **Service Name** (see table below)
4. Click a trace row to open the **full span timeline**
5. Click **Node graph** to see service call dependencies visually

**OTEL Service Name Reference:**

| K8s Deployment | OTEL Service Name |
|----------------|------------------|
| `storefront-bff` | `storefront-bff-service` |
| `backoffice-bff` | `backoffice-bff-service` |
| `product` | `product-service` |
| `order` | `order-service` |
| `cart` | `cart-service` |
| `customer` | `customer-service` |
| `inventory` | `inventory-service` |
| `payment` | `payment-service` |
| `media` | `media-service` |
| `tax` | `tax-service` |
| `promotion` | `promotion-service` |
| `rating` | `rate-service` |
| `location` | `location-service` |
| `sampledata` | `sampledata-service` |

**Loki → Tempo correlation:**
When viewing a log line in Loki that contains a `traceId`, Grafana shows a **Tempo** button. Click it to jump directly to the trace — no copy-paste needed.

---

### 7d. View Metrics — Prometheus

**Port-forward Prometheus UI (optional):**

```bash
kubectl port-forward svc/prometheus-kube-prometheus-prometheus 9090:9090 -n observability
```

Open: http://localhost:9090

**Useful PromQL queries in Grafana (Explore → Prometheus):**

```promql
# HTTP request rate per service (5-minute window)
rate(http_server_duration_milliseconds_count[5m])

# JVM heap memory used (by service)
jvm_memory_used_bytes{area="heap"}

# Active HTTP connections
http_server_active_requests

# 5xx error rate
rate(http_server_duration_milliseconds_count{http_status_code=~"5.."}[5m])

# Kafka consumer lag
kafka_consumer_group_lag

# GC pause time
rate(jvm_gc_pause_seconds_sum[5m])
```

---

### 7e. Pre-built Grafana Dashboards

1. Grafana → **Dashboards** (grid icon, left sidebar)
2. Browse folders: `yas-msa`, `Spring Boot`, `JVM`, `Kubernetes / Compute Resources`
3. Click a dashboard to explore panels for CPU, memory, request rates, error rates, and latency

***

## Verify Setup

```bash
# Check all pods across namespaces
kubectl get pods -n yas
kubectl get pods -n keycloak
kubectl get pods -n postgres
kubectl get pods -n kafka
kubectl get pods -n observability

# Check public ingress objects
kubectl get ingress -A

# List all namespaces
kubectl get namespaces
```

***

## Check / Switch Context

```bash
# List all configured contexts
kubectl config get-contexts

# Show the current active context
kubectl config current-context

# Switch back to the GKE cluster if needed
kubectl config use-context gke_decent-seeker-496610-k1_us-central1-a_yas-cluster
```

***

## Troubleshooting

### Cannot access service URLs in the browser

1. Confirm the ingress IPs match your `/etc/hosts` entries:
   ```bash
   kubectl get ingress -A
   ```
2. Test connectivity from terminal:
   ```bash
   curl -v http://storefront.yas.local.com/
   curl -v http://identity.yas.local.com/
   ```

### Pods stuck in `Pending` or `CrashLoopBackOff`

```bash
# Show events and error details
kubectl describe pod <pod-name> -n yas

# Show logs (and previous crash logs)
kubectl logs <pod-name> -n yas --previous
```

### Strimzi operator crashes with `No image for version 3.9.1`

This means the cluster operator image has been upgraded, but the Helm release still contains an older `STRIMZI_KAFKA_IMAGES` map.

Reset the Helm values when reinstalling or upgrading the operator:

```bash
helm upgrade --install kafka-operator oci://quay.io/strimzi-helm/strimzi-kafka-operator \
  --namespace kafka \
  --create-namespace \
  --version 0.45.2 \
  --reset-values
```

Then rerun:

```bash
./setup-cluster.sh
```

### Keycloak redirect errors on login

Verify `cluster-config.yaml` has the correct redirect URLs:

```yaml
keycloak:
  backofficeRedirectUrl: http://backoffice.yas.local.com
  storefrontRedirectUrl: http://storefront.yas.local.com
```

Re-run Keycloak setup if you changed this:

```bash
./setup-keycloak.sh
```

### Grafana shows "No data"

1. Verify observability pods are healthy:
   ```bash
   kubectl get pods -n observability
   ```
2. Check OpenTelemetry Collector logs:
   ```bash
   kubectl logs -n observability -l app.kubernetes.io/name=opentelemetry-collector -f
   ```
3. Check Prometheus scrape targets:
   ```bash
   kubectl port-forward svc/prometheus-kube-prometheus-prometheus 9090:9090 -n observability
   # Then open http://localhost:9090/targets
   ```

### Ingress IP changed

Re-run `kubectl get ingress -A` and update `/etc/hosts` if any public ingress `ADDRESS` changed.

### Teammate cannot connect to the cluster

Ask them to verify:

```bash
gcloud config get-value project
kubectl config current-context
kubectl get nodes
```

Expected values:

- project: `decent-seeker-496610-k1`
- context: `gke_decent-seeker-496610-k1_us-central1-a_yas-cluster`

If access still fails, the problem is usually IAM membership in the shared Google Cloud project, not the README steps.

***
