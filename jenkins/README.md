# Jenkins CD on GKE — Member 3 deliverables

This directory implements **Req. 4 (`developer_build`)** and **Req. 5 (`teardown`)** of Project 02 — the CD half of the YAS CI/CD pipeline. Jenkins runs on the same GKE cluster Member 1 set up, drives Helm against the `yas` namespace, and pulls per-commit images from Docker Hub published by Member 2's CI.

```
jenkins/
├── README.md                            ← you are here
├── install.sh                           ← one-shot installer
├── values.yaml                          ← Helm values for jenkins/jenkins
├── agent.Dockerfile                     ← agent image (kubectl + helm + yq + jq)
├── rbac.yaml                            ← yas-deployer SA + Role in yas ns
└── jobs/
    ├── developer_build/Jenkinsfile      ← Req. 4
    └── teardown/Jenkinsfile             ← Req. 5
```

---

## Prerequisites

- GKE cluster `yas-cluster` reachable (`kubectl cluster-info` works).
- `yas` namespace already populated by Member 1 (`k8s/deploy/deploy-yas-applications.sh`). The pipelines reuse the existing Postgres / Kafka / Keycloak instead of standing them up themselves.
- A Docker Hub account (any user) for hosting the custom agent image. The same account does **not** have to be the one publishing service images — Member 2's `DOCKERHUB_USERNAME` is the one that matters at deploy time.
- `helm` 3.12+ and `kubectl` 1.28+ locally.

---

## Step 1 — Build & push the agent image

The agent pod needs `kubectl` and `helm`, which the stock `jenkins/inbound-agent` image lacks.

```bash
cd jenkins
# Replace the placeholder in values.yaml and tag accordingly.
docker build -t docker.io/<YOUR_DOCKERHUB_USER>/yas-jenkins-agent:1 -f agent.Dockerfile .
docker push    docker.io/<YOUR_DOCKERHUB_USER>/yas-jenkins-agent:1

# Update values.yaml -> additionalAgents.yasDeployer.image.repository
sed -i.bak "s|docker.io/REPLACE_ME/yas-jenkins-agent|docker.io/<YOUR_DOCKERHUB_USER>/yas-jenkins-agent|" values.yaml
```

---

## Step 2 — Install Jenkins + RBAC

```bash
./install.sh
```

This runs `kubectl apply -f rbac.yaml` and then `helm upgrade --install jenkins jenkins/jenkins -n jenkins -f values.yaml`. JCasC seeds both pipeline jobs on first boot.

When the LoadBalancer gets an external IP:

```bash
kubectl -n jenkins get svc jenkins
# NAME      TYPE           EXTERNAL-IP       PORT(S)
# jenkins   LoadBalancer   34.xxx.xxx.xxx    8080:3xxxx/TCP
```

Admin password:

```bash
kubectl -n jenkins exec -it jenkins-0 -c jenkins \
  -- cat /run/secrets/additional/chart-admin-password
```

---

## Step 3 — Add the `dockerhub-username` credential

The `developer_build` job reads the Docker Hub username via `withCredentials(string(credentialsId: 'dockerhub-username', ...))`. That username **must** match what Member 2's CI uses for `secrets.DOCKERHUB_USERNAME`, since the tag we resolve points at `docker.io/<that-username>/yas-<svc>:<sha>`.

In Jenkins UI: **Manage Jenkins → Credentials → System → Global → Add Credentials**
- Kind: `Secret text`
- ID: `dockerhub-username`
- Secret: the Docker Hub username

---

## Step 4 — Run `developer_build`

1. Open **developer_build → Build with Parameters**.
2. Default = `main` for every service (= chart default, ghcr.io `:latest`).
3. Override the services you want to test, e.g. `BRANCH_TAX = dev_tax_service`. The job:
   - resolves the HEAD SHA of `dev_tax_service` on the team fork
   - waits for nothing — Member 2's CI must already have pushed `<user>/yas-tax:<sha>`
   - `helm upgrade --install` every service into `yas` (overridden services to Docker Hub, the rest to ghcr.io defaults)
   - prints a clickable URL block in **Build description**

### Accessing the deployed app

The build description prints the **Ingress LoadBalancer IP** (from `ingress/yas-ingress` in `yas`) plus a one-line `/etc/hosts` patch. Append it on your laptop:

```
<INGRESS_LB_IP> storefront.yas.local.com backoffice.yas.local.com api.yas.local.com identity.yas.local.com
```

Then open (port **80** — BFF/UI are `ClusterIP` + Ingress, not NodePort):

| Service     | URL                                          |
|-------------|----------------------------------------------|
| Storefront  | `http://storefront.yas.local.com/`           |
| Backoffice  | `http://backoffice.yas.local.com/`           |
| API / Swagger | `http://api.yas.local.com/swagger-ui/` (same GCE LB IP; specs at `http://api.yas.local.com/<service>/v3/api-docs`) |

After deploy, run `helm upgrade swagger-ui` with `apiDocsBaseUrl=http://api.yas.local.com` (no `:30003`) and `kubectl apply -f k8s/deploy/yas-ingress-gce.yaml` so the GCE LB routes `/product`, `/media`, … and `/swagger-ui`. Open Swagger on **`api.yas.local.com`**, not the swagger Service’s separate LoadBalancer IP — otherwise the browser blocks cross-origin fetches (CORS).

Legacy NodePort docs (`30001`–`30003`) in `k8s/deploy/README_YAS.md` apply only when Member 1 exposes services as `NodePort`.

---

## Step 5 — Run `teardown`

**teardown → Build with Parameters**:
- `CLEAN_PVC` left unchecked: just `helm uninstall` every release. Postgres data, Kafka logs, etc. survive.
- `CLEAN_PVC = true`: also deletes every PVC in `yas`. Use sparingly — it wipes shared state.

---

## Notes / gotchas

- **`delivery` service is excluded.** As of writing it has no Helm chart in `k8s/charts/` and no CI workflow producing a Docker Hub image, so there is nothing for `developer_build` to deploy. Add the entry to the `SERVICES` map in `developer_build/Jenkinsfile` once the chart lands.
- **`payment-paypal` is excluded.** The `payment-paypal` Maven module is a library embedded in `payment` (PayPal `/init` and `/capture` live on the `payment` service). The published `ghcr.io/.../yas-payment-paypal` image is a plain JAR without a Spring Boot main class (`no main manifest attribute`), so deploying the standalone chart always crashes. Re-enable once the module ships a runnable Boot application.
- **`swagger-ui` is intentionally not parameterised** — its chart pulls the upstream `swaggerapi/swagger-ui` image, not a YAS-built one. Re-running `deploy-yas-applications.sh` once initially gets it standing; afterwards `helm upgrade` on the YAS services leaves it alone.
- **UI charts use `--set ui.image.*`**, not `backend.image.*`. The Jenkinsfile registry encodes this per-service.
- **No GitHub webhook**: Req. 6 (auto-deploy `main`/`v*.*.*`) is owned by Member 4 via ArgoCD (`k8s/argocd/{dev,staging}-app.yaml`). Adding a webhook here would double-deploy.
- **Ingress vs node IP**: the publish stage prefers `yas-ingress` LB IP. If Ingress has no `ADDRESS` yet, it falls back to the node IP — links still use port 80 hostnames; ensure Ingress is ready or use a VPN/bastion to reach internal IPs on private-node clusters.
- **First run on an empty namespace**: by design, `developer_build` with all defaults brings up the full app (20 releases) against the chart's ghcr.io baseline. Use this to re-create the namespace after `teardown`.
