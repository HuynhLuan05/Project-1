# GKE Cluster Setup Guide for Team Members

This guide explains how to connect to the shared GKE cluster (`yas-cluster`) on Google Cloud Platform for local development and deployment.

---

## Prerequisites

Make sure the following tools are installed on your machine before proceeding.

| Tool | Purpose | Install Command |
|------|---------|----------------|
| `gcloud` CLI | Authenticate and manage GCP resources | [Download here](https://cloud.google.com/sdk/docs/install) |
| `kubectl` | Interact with Kubernetes cluster | `gcloud components install kubectl` |
| `gke-gcloud-auth-plugin` | Required for GKE authentication | See below |

### Install `gke-gcloud-auth-plugin`

**Ubuntu/Debian:**
```bash
sudo apt-get install google-cloud-sdk-gke-gcloud-auth-plugin
```

**macOS (Homebrew):**
```bash
brew install --cask google-cloud-sdk
gcloud components install gke-gcloud-auth-plugin
```

**Windows:**
```powershell
gcloud components install gke-gcloud-auth-plugin
```

---

## Step-by-Step Setup

### Step 1: Login to Google Cloud

```bash
gcloud auth login
```

A browser window will open. Login with your **Google account that has been added to the project**.

> ⚠️ Make sure the project owner has added your Google account to the GCP project IAM before proceeding.

---

### Step 2: Set the Active Project

```bash
gcloud config set project decent-seeker-496610-k1
```

---

### Step 3: Get Cluster Credentials

This command configures `kubectl` to point to the shared cluster:

```bash
gcloud container clusters get-credentials yas-cluster \
  --zone us-central1-a \
  --project decent-seeker-496610-k1
```

---

### Step 4: Verify Connection

```bash
kubectl get nodes
```

Expected output:
```
NAME                                         STATUS   ROLES    AGE   VERSION
gke-yas-cluster-default-pool-xxxxxxxx-xxxx   Ready    <none>   Xd    v1.35.x-gke.xxxxxx
```

---

### Step 5: Check Running Pods

```bash
# Check pods in the main namespace
kubectl get pods -n yas

# Check pods in kafka namespace
kubectl get pods -n kafka
```

---

## Cluster Information

| Property | Value |
|----------|-------|
| **Cluster Name** | `yas-cluster` |
| **Project ID** | `decent-seeker-496610-k1` |
| **Zone** | `us-central1-a` |
| **Mode** | Standard (not Autopilot) |
| **Kubernetes Version** | v1.35.3-gke.1389000 |

---

## Namespaces

| Namespace | Purpose |
|-----------|---------|
| `yas` | Main application services |
| `kafka` | Kafka broker & Strimzi operator |
| `kube-system` | Kubernetes system components |

---

## Common `kubectl` Commands

```bash
# List all namespaces
kubectl get namespaces

# Get all pods in a namespace
kubectl get pods -n <namespace>

# Watch pods in real-time
kubectl get pods -n yas -w

# View logs of a pod
kubectl logs <pod-name> -n yas

# Describe a pod (useful for debugging)
kubectl describe pod <pod-name> -n yas

# Apply a manifest file
kubectl apply -f <filename>.yaml

# Delete a resource
kubectl delete -f <filename>.yaml
```

---

## Troubleshooting

### `i/o timeout` or `context deadline exceeded` warning

This is a harmless warning from the `kubectl` cache. If `kubectl get nodes` still shows nodes as `Ready`, the cluster is working fine. Run the following to refresh credentials:

```bash
gcloud container clusters get-credentials yas-cluster \
  --zone us-central1-a \
  --project decent-seeker-496610-k1
```

---

### `PERMISSION_DENIED` error

Your Google account has not been granted access to the project. Ask the **project owner** to add your email in:

> GCP Console → IAM & Admin → IAM → **Grant Access** → Role: `Editor` or `Kubernetes Engine Developer`

---

### `gke-gcloud-auth-plugin` not found

```bash
gcloud components install gke-gcloud-auth-plugin
export USE_GKE_GCLOUD_AUTH_PLUGIN=True
```

Add the export line to your `~/.bashrc` or `~/.zshrc` to make it permanent:

```bash
echo 'export USE_GKE_GCLOUD_AUTH_PLUGIN=True' >> ~/.bashrc
source ~/.bashrc
```

---

## ⚠️ Cost Reminder

The cluster costs money while running. When the team is **done working**, notify the project owner to delete or scale down the cluster to avoid burning through the $300 free credit.

```bash
# Project owner only — delete cluster when done
gcloud container clusters delete yas-cluster \
  --zone us-central1-a \
  --project decent-seeker-496610-k1
```

---

## Quick Setup Script (Copy & Paste)

```bash
# Run this once to set up kubectl on your machine
gcloud auth login
gcloud config set project decent-seeker-496610-k1
gcloud container clusters get-credentials yas-cluster \
  --zone us-central1-a \
  --project decent-seeker-496610-k1
kubectl get nodes
kubectl get pods -n yas
```
