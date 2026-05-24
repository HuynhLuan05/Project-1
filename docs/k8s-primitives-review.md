# Kubernetes Primitives — phân biệt & cách tương tác

> Tài liệu ôn tập các khái niệm cơ bản: **Cluster, VM, Node, Namespace, Pod, Service, Deployment, Ingress, ConfigMap, Secret, PersistentVolume**. Mỗi khái niệm có định nghĩa, ví dụ thật từ project YAS, và sơ đồ tương tác.

---

## 1. Bức tranh tổng thể — phân cấp

```
GOOGLE CLOUD (cloud provider)
└── Project (decent-seeker-496610-k1)
    │
    └── CLUSTER (yas-cluster, region: us-central1)         ← Level 1: Kubernetes
        │
        ├── Control Plane (do GKE quản, ẩn với user)
        │     ├── API Server
        │     ├── Scheduler
        │     ├── Controller Manager
        │     └── etcd (database)
        │
        └── Worker Nodes (VM thật)                          ← Level 2: máy ảo
            ├── NODE 1: gke-yas-cluster-pool-...-ckqr (8 vCPU, 32 GB RAM, 50 GB disk)
            ├── NODE 2: ...
            └── NODE 3: ...
                │
                └── Trên mỗi node có Pods                    ← Level 3: container
                    ├── POD: backoffice-bff-xxx
                    │     └── Container: yas-backoffice-bff
                    │
                    ├── POD: postgresql-0
                    │     └── Container: postgres
                    │
                    └── POD: kube-dns-yyy (system pod)
                          └── Container: kube-dns

NAMESPACE                                                   ← Level "ngang", chia logic
└── yas, keycloak, postgres, jenkins, kube-system, ...
    (Mỗi namespace chứa Pods + Services + ConfigMaps + ... thuộc nó)
```

**Quan hệ chính**:
- **Cluster** chứa **Nodes** (VM).
- **Node** chạy nhiều **Pods**.
- **Pods** thuộc về **Namespaces** (logical group).
- **Service** expose **Pods** qua mạng (trong/ngoài cluster).
- **Deployment** quản lý lifecycle của **Pods**.

---

## 2. Cluster

### Định nghĩa
Một hệ thống Kubernetes hoàn chỉnh: control plane + worker nodes. Đơn vị quản trị cao nhất.

### Đặc điểm
- Có 1 API endpoint (vd `https://<cluster-ip>:443`) để client (kubectl, helm) gọi vào.
- Hoàn toàn độc lập với cluster khác: network, storage, RBAC riêng.
- Tạo bằng `gcloud container clusters create`, `kubeadm`, `kind`, `minikube`.

### Trong project YAS
3 cluster (xem `kubectl config get-contexts`):
| Cluster | Vị trí | Vai trò |
|---|---|---|
| `gke_decent-seeker-...-yas-cluster` (us-central1) | GKE | ⭐ Đang dùng cho project |
| `gke_steady-datum-...-yas-cluster` (australia-southeast1) | GKE | Cluster khác (teammate?) |
| `minikube` | Local máy dev | Test thử |

### Lệnh tương tác
```bash
kubectl config current-context           # Xem cluster active
kubectl config use-context <name>        # Đổi cluster
kubectl cluster-info                     # Info chi tiết
```

---

## 3. Node (≈ VM)

### Định nghĩa
Máy ảo (hoặc máy vật lý) chạy workload của K8s. Trên cloud, mỗi node là 1 VM. Trên minikube, node là 1 container Docker.

### Đặc điểm
- Có CPU, RAM, disk thật.
- Chạy **kubelet** (agent K8s) + **container runtime** (containerd/CRI-O).
- Có thể bị evict pod nếu hết resource (OOM, disk pressure).

### Trong project YAS
Cluster GKE có node pool `pool-32gb-disk50` (mỗi node 32GB RAM, 50GB disk). Xem:
```bash
kubectl get nodes
# NAME                                                STATUS   AGE
# gke-yas-cluster-pool-32gb-disk50-23f724db-ckqr     Ready    2d
# ...

kubectl describe node gke-yas-cluster-pool-32gb-disk50-23f724db-ckqr
# Allocatable: CPU 7.91, Memory 28Gi → đây là tài nguyên thực dùng được
# Allocated:   CPU 5.5, Memory 22Gi  → đang dùng bao nhiêu
```

### VM vs Node — phân biệt
- **VM** là khái niệm IaaS (Compute Engine, EC2). Tồn tại độc lập, không cần K8s.
- **Node** là VM **đã được đăng ký vào cluster K8s**. Có kubelet, được API server quản.

Nói cách khác: mọi node đều là VM, nhưng không phải VM nào cũng là node.

### Lệnh tương tác
```bash
kubectl get nodes -o wide                # Liệt kê + thông tin
kubectl top nodes                        # CPU/RAM usage realtime
kubectl describe node <name>             # Pods đang chạy + resource allocation
kubectl cordon <node>                    # Mark node "không nhận pod mới"
kubectl drain <node>                     # Evict mọi pod để bảo trì
```

---

## 4. Namespace

### Định nghĩa
**Phân vùng logic** trong cluster. Không phải máy thật — chỉ là label/boundary để nhóm resource liên quan.

### Đặc điểm
- Mọi resource (Pod, Service, ConfigMap, Secret, Deployment, ...) thuộc về 1 namespace (trừ cluster-scoped resource như Node, ClusterRole).
- Cùng namespace: gọi nhau bằng short name (`postgres`).
- Khác namespace: gọi nhau bằng FQDN (`postgres.postgres.svc.cluster.local`).
- RBAC có thể giới hạn user/SA chỉ thao tác trong 1 namespace.

### Trong project YAS

```bash
kubectl get namespaces
```

| Namespace | Mục đích | Chứa gì |
|---|---|---|
| `yas` | App YAS | Pod backoffice-bff, order, customer, …, Service, Ingress |
| `keycloak` | Auth | Keycloak operator + Keycloak pod |
| `postgres` | Database | Postgres operator + postgresql-0 + pgadmin |
| `kafka` | Event bus | Strimzi operator + Kafka brokers + Zookeeper |
| `elasticsearch` | Search | ES cluster |
| `jenkins` | CI/CD controller | Jenkins controller pod + agent pods |
| `observability` | Monitoring | Prometheus + Grafana |
| `cert-manager` | TLS certs | cert-manager controllers |
| `kube-system` | Core K8s | kube-dns, kube-proxy, kube-scheduler |
| `default` | Mặc định nếu không chỉ định | (project này không dùng) |

### Vì sao chia namespace

- **Tách lifecycle**: teardown ns `yas` không đụng ns `postgres`. Cluster reset từng phần.
- **Tách RBAC**: user/SA chỉ thao tác trong namespace của mình.
- **Tách resource quota**: mỗi ns có CPU/RAM limit riêng.
- **Tách network policy**: chặn traffic giữa namespace nếu cần.
- **Logical clarity**: nhìn vào tên ns biết ngay nó làm gì.

### Lệnh tương tác
```bash
kubectl get pods -n yas                  # Pod trong ns yas
kubectl get pods -A                      # Pod tất cả ns
kubectl config set-context --current --namespace=yas   # Đổi default ns
kubectl create namespace foo             # Tạo ns mới
```

---

## 5. Pod

### Định nghĩa
**Đơn vị chạy nhỏ nhất** trong K8s. 1 hoặc nhiều container chạy chung 1 môi trường mạng + storage.

### Đặc điểm
- Mỗi pod có IP riêng (cluster-internal IP, vd `10.83.129.150`).
- Container trong cùng pod share IP + localhost + volumes.
- Pod **ephemeral** — chết là mất. Tạo lại được pod mới với IP khác.
- Pod không tự restart sau khi chết — cần Deployment/StatefulSet/DaemonSet quản lý.

### Trong project YAS

```bash
kubectl get pods -n yas
# NAME                              READY   STATUS    RESTARTS   AGE
# backoffice-bff-54dbbd77db-7lz4c   1/1     Running   0          5h
# customer-769976bb7b-jhd7j         1/1     Running   0          5h
# ...
```

Tên pod = `<deployment>-<replicaset-hash>-<random>`. Mỗi pod chứa 1 container (vd `yas-backoffice-bff`).

### Container vs Pod — phân biệt
- **Container**: process đóng gói (Docker image).
- **Pod**: wrapper K8s quản 1 hoặc nhiều container + chia sẻ network/volume.

Trong YAS, mỗi pod chỉ có 1 container (pattern phổ biến nhất). Pattern multi-container pod (sidecar) dùng cho proxy/logger/init.

### Lệnh tương tác
```bash
kubectl get pods -n yas -o wide          # Có IP + node
kubectl describe pod <name> -n yas       # Events, conditions, env vars
kubectl logs <name> -n yas               # Container log
kubectl logs <name> -n yas --previous    # Log của container đã chết
kubectl exec -it <name> -n yas -- bash   # Vào shell container
kubectl delete pod <name> -n yas         # Xóa (deployment sẽ tạo lại)
```

---

## 6. Deployment (và ReplicaSet)

### Định nghĩa
**Controller** đảm bảo có N pod giống nhau chạy. Quản lý lifecycle: scale, rolling update, rollback.

### Đặc điểm
- Khai báo spec (image, replicas, env, resources).
- K8s tạo ReplicaSet → ReplicaSet tạo Pods theo spec.
- Pod chết → ReplicaSet tự tạo pod mới.
- Update image → rolling update (tạo pod mới, xóa pod cũ từ từ).

### Hierarchy
```
Deployment (cấu hình mong muốn)
  └── ReplicaSet (1 phiên bản cụ thể của deployment)
        └── Pod 1, Pod 2, Pod 3 (instance thực)
```

Mỗi lần `helm upgrade` → tạo ReplicaSet mới → pod cũ chết, pod mới start.

### Trong project YAS
```bash
kubectl get deploy -n yas
# NAME                READY   UP-TO-DATE   AVAILABLE   AGE
# backoffice-bff      1/1     1            1           5h
# customer            1/1     1            1           5h

kubectl rollout history deploy/backoffice-bff -n yas
# REVISION  CHANGE-CAUSE
# 1         install
# 2         upgrade
# ...

kubectl rollout restart deploy/backoffice-bff -n yas    # Restart không đổi image
kubectl rollout undo deploy/backoffice-bff -n yas       # Rollback revision cũ
```

### Vì sao không tạo Pod trực tiếp

| Tạo Pod trực tiếp | Tạo qua Deployment |
|---|---|
| Pod chết → mất hẳn | Pod chết → tự tạo lại |
| Không scale được | Đổi `replicas: 3` tăng số instance |
| Update phải xóa + tạo lại | Rolling update, không downtime |
| Không có history | Có rollout history, undo được |

---

## 7. Service

### Định nghĩa
**Endpoint mạng ổn định** cho 1 nhóm pod. Pod IP thay đổi khi pod restart, nhưng Service IP **stable**.

### Cách hoạt động
1. Service có `selector: app=backoffice-bff`.
2. K8s tự tìm mọi pod có label match → đăng ký IP vào Endpoints/EndpointSlice.
3. Service IP (ClusterIP) hoạt động như load balancer L4 — request đến → forward về 1 pod backend ngẫu nhiên.

### Các loại Service

| Type | Mô tả | Trong YAS |
|---|---|---|
| **ClusterIP** | IP nội bộ cluster, chỉ pod trong cluster gọi được. Mặc định. | backoffice-bff, customer, order, ... |
| **NodePort** | Mở port trên mọi node (vd 30000-32767). Bên ngoài cluster gọi `<node-ip>:<port>`. | api-gateway-nodeport |
| **LoadBalancer** | Cloud provider cấp 1 IP public + LB (ELB/GCLB). | (qua Ingress) |
| **ExternalName** | Alias DNS, không có IP. Trả CNAME khi query DNS. | `postgres` (alias cho `postgresql.postgres.svc.cluster.local`) |
| **Headless** (clusterIP: None) | Không có IP. Query DNS trả về list pod IP. | Stateful (Postgres, Kafka brokers) |

### Trong project YAS
```bash
kubectl get svc -n yas
# NAME                   TYPE          CLUSTER-IP       PORT(S)
# backoffice-bff         ClusterIP     34.118.233.165   80/TCP,8090/TCP
# postgres               ExternalName  <none>           5432/TCP  (-> postgresql.postgres.svc...)
# api-gateway-nodeport   ClusterIP     34.118.233.20    80/TCP
```

### DNS resolution

Pod trong ns `yas` gọi service:
```
http://backoffice-bff               → backoffice-bff.yas.svc.cluster.local
http://customer.yas                  → customer.yas.svc.cluster.local
http://postgresql.postgres           → postgresql.postgres.svc.cluster.local (cross-namespace)
http://keycloak-service.keycloak     → keycloak-service.keycloak.svc.cluster.local
```

DNS search list (xem trong `/etc/resolv.conf` của pod):
```
search yas.svc.cluster.local svc.cluster.local cluster.local
```

→ Short name resolve trong cùng ns trước, sau đó các cấp dưới. Cross-ns phải dùng `<svc>.<ns>` hoặc FQDN.

### Pod ↔ Service tương tác

```
[Pod A: customer]                    [Service backoffice-bff (ClusterIP 34.118.233.165)]
   │                                   │  selector: app=backoffice-bff
   │ GET http://backoffice-bff:80      │
   ├──────────────────────────────────▶│  (kube-proxy iptables/IPVS load balance)
   │                                   │
   │                                   ├──▶ [Pod: backoffice-bff-xxx-aaa]
   │                                   ├──▶ [Pod: backoffice-bff-xxx-bbb]   ← chọn 1 ngẫu nhiên
   │                                   └──▶ [Pod: backoffice-bff-xxx-ccc]
```

---

## 8. Ingress

### Định nghĩa
**Layer 7 (HTTP) router**, cho phép expose nhiều Service ra ngoài qua 1 IP duy nhất, dựa vào hostname + path.

### Khác với Service LoadBalancer
- Service LoadBalancer: 1 service = 1 IP public. 10 service cần 10 IP (tốn tiền).
- Ingress: 1 IP public → route theo Host header + path → nhiều backend Service.

### Trong project YAS

```bash
kubectl get ingress -n yas
# NAME           HOSTS                                                ADDRESS         PORTS
# yas-ingress    storefront.yas.local.com,backoffice.yas.local.com,   8.233.85.27    80
#                api.yas.local.com,identity.yas.local.com
```

Browser request `http://storefront.yas.local.com/` → DNS resolve (qua /etc/hosts) → IP `8.233.85.27` → GCE LB → Ingress controller → route theo Host header `storefront.yas.local.com` → forward đến Service `storefront-ui:3000`.

### Tương tác

```
[Browser dev máy]
   │ http://storefront.yas.local.com/
   │ (DNS: /etc/hosts trỏ về 8.233.85.27)
   ▼
[GCE Load Balancer (Ingress IP 8.233.85.27)]
   │
   ▼
[Ingress yas-ingress]
   │
   ├──Host=storefront.yas.local.com──▶ [Service storefront-ui:3000] ──▶ Pod storefront-ui
   ├──Host=backoffice.yas.local.com──▶ [Service backoffice-ui:3000] ──▶ Pod backoffice-ui
   ├──Host=api.yas.local.com /api/*──▶ [Service api-gateway-nodeport] ──▶ ...
   └──Host=identity.yas.local.com──▶ [Service keycloak-service] (cross-ns)
```

---

## 9. ConfigMap & Secret

### Định nghĩa
**Cấu hình tách rời** khỏi image. Mount vào pod như file hoặc inject thành env var.

### ConfigMap vs Secret

| | ConfigMap | Secret |
|---|---|---|
| Mục đích | Cấu hình non-sensitive | Password, token, cert |
| Lưu trữ | Plain text trong etcd | Base64 + có thể encrypt at rest |
| Truy cập | RBAC `configmaps` | RBAC `secrets` (thường chặt hơn) |
| Ví dụ trong YAS | `yas-configuration-configmap`, `backoffice-bff-extra-configmap` | `yas-postgresql-credentials-secret`, `yas-keycloak-credentials-secret` |

### Cách pod dùng

**Mount as volume**:
```yaml
volumes:
  - name: yas-config
    configMap:
      name: yas-configuration-configmap
volumeMounts:
  - mountPath: /opt/yas/config
    name: yas-config
```
→ File `application.yaml` xuất hiện trong pod ở `/opt/yas/config/application.yaml`.

**Inject env var**:
```yaml
envFrom:
  - secretRef:
      name: yas-postgresql-credentials-secret
```
→ Mỗi key trong secret thành 1 env var trong container.

### Trong project YAS

`yas-configuration` Helm release tạo:
- `yas-configuration-configmap` — config chung Spring Boot.
- `*-extra-configmap` — config riêng từng service (vd `backoffice-bff-extra-configmap` chứa OIDC URL).
- `yas-postgresql-credentials-secret` — DB user/pass.
- `yas-keycloak-credentials-secret` — Keycloak client secret.

Mỗi pod backend mount cả 3 → đọc URL DB, OIDC, password từ đó.

### Reloader pattern
Stakater Reloader (`yas-reloader` pod) watch ConfigMap/Secret. Khi nội dung thay đổi → annotation pod thay đổi → Deployment rolling restart → pod đọc config mới.

---

## 10. PersistentVolume (PV) & PersistentVolumeClaim (PVC)

### Định nghĩa
- **PV**: tài nguyên storage trong cluster (vd 50GB disk GCE).
- **PVC**: yêu cầu storage từ pod ("cho tôi 10GB").
- K8s bind PVC vào PV phù hợp → pod mount vào.

### Trong YAS
- Postgres pod (`postgresql-0`) có PVC để giữ data persistent. Pod chết → data còn → pod mới mount lại.
- Kafka broker tương tự.
- Pod backend YAS (stateless) không có PVC — data chỉ lưu memory + DB ngoài.

### Lệnh
```bash
kubectl get pvc -n postgres
# NAME                              STATUS   VOLUME           CAPACITY
# pgdata-postgresql-postgresql-0    Bound    pvc-abc123       50Gi

kubectl get pv
```

### Trong teardown
Parameter `CLEAN_PVC=true` xóa tất cả PVC trong ns `yas` → mất data. Mặc định `false` để bảo vệ.

---

## 11. Sơ đồ tương tác tổng hợp — Request flow trong YAS

Lấy ví dụ: dev mở browser truy cập `http://backoffice.yas.local.com/admin/users`.

```
1. Browser
   │ /etc/hosts: backoffice.yas.local.com → 8.233.85.27
   ▼
2. Internet → GCE Load Balancer IP 8.233.85.27
   │
   ▼
3. CLUSTER: yas-cluster (us-central1)
   │
   ├─ NODE: gke-yas-cluster-pool-...-ckqr (8 vCPU, 32 GB)
   │   │
   │   ├─ POD: nginx-ingress-controller-xxx (ns ingress-nginx)
   │   │   │  Hoặc GCE Ingress controller (managed)
   │   │   │
   │   │   ▼ Match Ingress rule:
   │   │     host=backoffice.yas.local.com, path=/admin → svc backoffice-ui:3000
   │   │
   │   ├─ POD: backoffice-ui-xxx (ns yas)
   │   │   IP: 10.83.129.100
   │   │   container: nextjs (port 3000)
   │   │   │
   │   │   │ Server-side render → cần data → gọi API
   │   │   ▼ GET http://backoffice-bff/api/users
   │   │
   │   ├─ DNS lookup: backoffice-bff → backoffice-bff.yas.svc.cluster.local
   │   │              → ClusterIP 34.118.233.165
   │   │
   │   ├─ POD: backoffice-bff-xxx (ns yas)
   │   │   IP: 10.83.129.222
   │   │   container: spring-boot (port 80)
   │   │   │
   │   │   ├─ mount ConfigMap yas-configuration-configmap → đọc app config
   │   │   ├─ mount ConfigMap backoffice-bff-extra-configmap → OIDC URL
   │   │   ├─ envFrom Secret yas-keycloak-credentials-secret → client secret
   │   │   │
   │   │   │ Cần validate JWT → gọi Keycloak
   │   │   ▼ GET http://keycloak-service.keycloak.svc.cluster.local/realms/Yas/.well-known/...
   │   │
   │   └─ (cross-namespace) ns keycloak: POD keycloak-0
   │
   ├─ NODE 2: ...
   └─ NODE 3: ...
```

---

## 12. Bảng tổng hợp: Cách thức tương tác

| Từ | Đến | Cơ chế | Ví dụ |
|---|---|---|---|
| Pod | Pod cùng ns | `<service>` short name → DNS → ClusterIP → kube-proxy → pod | customer → backoffice-bff |
| Pod | Pod khác ns | `<service>.<ns>` FQDN | yas/customer → postgres/postgresql |
| Pod | Service external | URL public (cần network egress) | yas/sampledata → openai.azure.com |
| Browser dev | Pod | /etc/hosts → Ingress LB IP → Ingress controller → Service → Pod | dev → storefront-ui |
| kubectl/helm | Cluster | API Server (TLS) | bạn → `kubectl get pods` |
| Jenkins agent | Cluster | ServiceAccount JWT token → API Server | yas-deployer → helm upgrade |
| CI (GitHub Actions) | Docker Hub | username/password (secret) → Docker registry API | push image |
| CD (Jenkins) | Docker Hub | pull anonymous (image public) hoặc imagePullSecret | helm install → pod pull image |

---

## 13. Cheat sheet — lệnh hay dùng

### Cluster level
```bash
kubectl cluster-info                     # API endpoint, ns
kubectl get nodes -o wide                # node + IP + status
kubectl top nodes                        # CPU/RAM usage
```

### Namespace
```bash
kubectl get ns
kubectl get all -n yas                   # mọi resource trong ns
```

### Pod
```bash
kubectl get pods -n yas -o wide
kubectl describe pod <pod> -n yas
kubectl logs <pod> -n yas
kubectl logs <pod> -n yas --previous     # log container đã chết
kubectl logs <pod> -n yas -c <container> # multi-container pod
kubectl logs -f <pod>                    # follow
kubectl exec -it <pod> -n yas -- sh
kubectl port-forward <pod> 8080:80 -n yas
```

### Service & networking
```bash
kubectl get svc -n yas
kubectl get endpoints <svc> -n yas       # IP các pod đang serve
kubectl get ingress -n yas
kubectl describe ingress <name> -n yas
```

### Deployment
```bash
kubectl get deploy -n yas
kubectl rollout status deploy/<name> -n yas
kubectl rollout history deploy/<name> -n yas
kubectl rollout restart deploy/<name> -n yas
kubectl rollout undo deploy/<name> -n yas --to-revision=2
kubectl scale deploy/<name> --replicas=3 -n yas
```

### ConfigMap & Secret
```bash
kubectl get cm -n yas
kubectl get cm <name> -n yas -o yaml
kubectl get secret -n yas
kubectl get secret <name> -n yas -o jsonpath='{.data.password}' | base64 -d
```

### Debug network
```bash
# Test DNS resolution từ pod
kubectl -n yas exec deploy/backoffice-bff -- nslookup postgresql.postgres

# Test TCP từ pod
kubectl -n yas run -i --rm --restart=Never tmp --image=busybox -- \
  nc -zv postgresql.postgres 5432

# Curl từ pod
kubectl -n yas run -i --rm --restart=Never tmp --image=curlimages/curl -- \
  curl -sI http://backoffice-bff/actuator/health
```

---

## 14. Câu hỏi vấn đáp mẫu

### Q1. Sự khác biệt giữa Pod và Container?
**Container**: 1 process đóng gói (Docker). **Pod**: wrapper K8s gói 1+ container, share network/storage. Trong YAS, mỗi pod chỉ có 1 container.

### Q2. Pod IP có ổn định không? Nếu không, dùng gì để truy cập pod?
Không. Pod IP đổi mỗi khi pod restart. Dùng **Service** — IP của Service ổn định, route đến pod backend qua selector.

### Q3. Khi nào dùng ClusterIP, NodePort, LoadBalancer, ExternalName?
- **ClusterIP** (default): internal communication, không expose ra ngoài.
- **NodePort**: expose qua port trên mọi node, dev/test.
- **LoadBalancer**: cloud cấp IP public + LB. Tốn tiền nếu nhiều.
- **ExternalName**: alias DNS, không có IP — dùng cho cross-namespace alias hoặc trỏ tới service ngoài cluster.

### Q4. Vì sao Pod ở ns `yas` resolve được `postgresql.postgres`?
- K8s DNS service (`kube-dns` / `CoreDNS`) trả về CNAME/A record cho mọi Service trong mọi namespace theo pattern `<svc>.<ns>.svc.cluster.local`.
- Pod query `postgresql.postgres` → DNS search list expand → resolve thành `postgresql.postgres.svc.cluster.local` → ClusterIP của Service postgres ở ns postgres.

### Q5. Namespace có cô lập network không?
**Mặc định không**. Mọi pod trong cluster ping/curl được nhau bất kể namespace, miễn biết FQDN. Để cô lập cần `NetworkPolicy`.

### Q6. Helm release nằm ở namespace nào? 1 chart cài được nhiều ns không?
- Helm release lưu state trong **Secret** với label `owner=helm,name=<release>`, namespace = namespace user chỉ định với `--namespace`.
- 1 chart có thể cài nhiều lần ở nhiều ns hoặc nhiều tên — mỗi lần là 1 release riêng.

### Q7. Pod chết thì xảy ra gì với Deployment?
Deployment → ReplicaSet detect pod count < replicas → tạo pod mới (với spec cũ). Tên pod mới (suffix random), IP mới, nhưng Service tự update endpoint → user không thấy gián đoạn (nếu replicas>1).

### Q8. Phân biệt resource Cluster-scoped vs Namespace-scoped?
- **Cluster-scoped**: Node, Namespace, PersistentVolume, ClusterRole, ClusterRoleBinding, StorageClass. Không có namespace.
- **Namespace-scoped**: Pod, Service, Deployment, ConfigMap, Secret, Role, RoleBinding, Ingress, PVC. Thuộc 1 namespace.

`kubectl api-resources --namespaced=true` / `--namespaced=false` để liệt kê.

### Q9. ConfigMap thay đổi, pod đang chạy có tự dùng config mới không?
- **Env var** từ ConfigMap: KHÔNG. Cần restart pod để re-read.
- **Volume mount**: tự update (kubelet sync file mỗi ~60s), nhưng app phải re-read file (đa số Spring Boot không tự reload).

→ Pattern phổ biến: dùng **Reloader** (Stakater) — watch CM, tự rolling restart Deployment khi CM đổi.

### Q10. PVC bị xóa thì PV xảy ra gì?
Phụ thuộc `reclaimPolicy` của PV:
- `Retain`: PV giữ lại data, status `Released`, admin phải dọn thủ công.
- `Delete`: PV + storage backend xóa luôn (data mất).
- `Recycle` (deprecated): wipe data, để dùng lại.

GKE mặc định Delete cho dynamically provisioned PV.

### Q11. Tại sao pod cần `terminationGracePeriodSeconds`?
Khi xóa pod, K8s:
1. Gửi SIGTERM cho container.
2. Đợi `terminationGracePeriodSeconds` (default 30s) để container shutdown sạch.
3. Sau đó SIGKILL.

App có thể bắt SIGTERM để: drain request, đóng kết nối, flush buffer trước khi chết. Quan trọng cho stateful service (DB, queue).

### Q12. Kubelet, kube-proxy, kube-scheduler, kube-controller-manager — vai trò?
- **kubelet**: agent trên mỗi node, quản pod lifecycle.
- **kube-proxy**: trên mỗi node, setup iptables/IPVS rules cho Service.
- **kube-scheduler**: trên control plane, quyết định pod chạy ở node nào.
- **kube-controller-manager**: chạy controller (Deployment, ReplicaSet, Node, ...) — reconcile state.

User không thao tác trực tiếp với chúng, chỉ giao tiếp qua **API Server**.

---

## 15. Tổng kết — quy tắc nhớ

| Khái niệm | Mức | Ghi nhớ |
|---|---|---|
| **Cluster** | Toàn cảnh | "Một hệ K8s độc lập" |
| **Node** | Vật lý | "VM thực, chứa pod" |
| **Namespace** | Logic | "Folder phân loại" |
| **Pod** | Vật lý nhỏ nhất | "Container + network share" |
| **Deployment** | Quản pod | "Spec ổn định, pod ephemeral" |
| **Service** | Mạng | "IP stable cho group pod" |
| **Ingress** | L7 router | "1 IP, nhiều host/path" |
| **ConfigMap** | Cấu hình | "Tách config khỏi image" |
| **Secret** | Cấu hình bí mật | "ConfigMap có chú ý security" |
| **PV/PVC** | Storage | "Disk gắn vào pod" |

**Mantra**: Cluster chứa Node; Node chạy Pod; Pod thuộc Namespace; Service expose Pod; Ingress route HTTP; Config/Secret bơm config vào Pod; PVC cho storage.

---

*Tài liệu này bổ sung cho `cicd-pipeline-review.md`. Đọc cùng để ôn cả "primitives K8s" và "quy trình CI/CD áp dụng các primitives đó".*
