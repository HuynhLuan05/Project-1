## 📌 Cluster Information

| Item             | Value                          |
|------------------|-------------------------------|
| **Project ID**   | `steady-datum-496203-r2`      |
| **Cluster Name** | `yas-cluster`                 |
| **Region**       | `australia-southeast1`        |
| **Namespaces**   | `yas`, `dev`, `staging`       |

***

## Prerequisites

Make sure the following tools are installed on your machine before starting:

- [gcloud CLI](https://cloud.google.com/sdk/docs/install)
- [kubectl](https://kubernetes.io/docs/tasks/tools/)

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
# 1. Activate the service account using the JSON key file
gcloud auth activate-service-account --key-file=yas-team-key.json

# 2. Set the GCP project
gcloud config set project steady-datum-496203-r2

# 3. Fetch kubeconfig credentials for the GKE cluster
gcloud container clusters get-credentials yas-cluster \
  --region australia-southeast1 \
  --project steady-datum-496203-r2

# 4. Verify the connection
kubectl get nodes
kubectl get pods -n yas
```

### Expected Output

```
NAME                                       STATUS   ROLES    AGE
gke-yas-cluster-default-pool-xxxx-xxxx     Ready    <none>   ...

NAME                          READY   STATUS    RESTARTS
product-68b9c8c5d4-xxxxx      1/1     Running   0
...
```

***

## Step 4 — Add Entries to `/etc/hosts`

The cluster exposes services via **NodePort**. You need to map the GKE node's external IP to domain names so you can access services in your browser.

### Get the Node External IP

```bash
kubectl get nodes -o wide
# Look at the EXTERNAL-IP column
```

### Linux / WSL

```bash
sudo nano /etc/hosts
```

Add the following lines (replace `<NODE_EXTERNAL_IP>` with the IP from the command above):

```
<NODE_EXTERNAL_IP>  storefront.yas.local.com
<NODE_EXTERNAL_IP>  backoffice.yas.local.com
<NODE_EXTERNAL_IP>  api.yas.local.com
<NODE_EXTERNAL_IP>  identity.yas.local.com
```

### Windows

Open `C:\Windows\System32\drivers\etc\hosts` using **Notepad as Administrator**, then add the same lines above.

***

## Step 6 — Access the Services

Once `/etc/hosts` is configured, open your browser and navigate to:

| Service     | URL                                        | NodePort |
|-------------|---------------------------------------------|----------|
| Storefront  | `http://storefront.yas.local.com:30001`    | `30001`  |
| Backoffice  | `http://backoffice.yas.local.com:30002`    | `30002`  |
| API Gateway | `http://api.yas.local.com:30003`           | `30003`  |
| Identity    | `http://identity.yas.local.com:30004`      | `30004`  |

> Ask **Member 1** to confirm the exact NodePort numbers if the above are outdated.

***

## Verify Setup

```bash
# Check running pods
kubectl get pods -n yas

# Check available namespaces
kubectl get namespaces

# Check services and NodePorts
kubectl get svc -n yas | grep NodePort
```

***

## Check / Switch Context

```bash
# List all configured contexts
kubectl config get-contexts

# Show the current active context
kubectl config current-context

# Switch back to the GKE cluster if needed
kubectl config use-context gke_steady-datum-496203-r2_australia-southeast1_yas-cluster
```

***

