# Tổng kết quy trình CI/CD project YAS — tài liệu ôn tập vấn đáp

> Tài liệu này tổng hợp toàn bộ quy trình CI/CD đã triển khai cho project YAS, kèm lý do (the "why") của mỗi quyết định thiết kế. Dùng để ôn vấn đáp môn DevOps.

---

## 1. Bối cảnh & phân chia trách nhiệm

### 1.1. Dự án YAS là gì
YAS (Yet Another Shop) là e-commerce microservices gồm ~15 service (backoffice-bff, storefront-bff, *-ui, cart, customer, order, product, …). Mỗi service là Spring Boot/Next.js, đóng gói thành Docker image, deploy bằng Helm vào Kubernetes (GKE).

### 1.2. Ranh giới ownership giữa các thành viên

| Layer | Owner | Repo / Công cụ |
|---|---|---|
| Cluster setup (GKE, Postgres, Kafka, ES, Keycloak) | **Member 1** | `k8s/deploy/setup-*.sh` |
| Shared config (`yas-configuration`: ConfigMaps + Secrets) | **Member 1** | `k8s/deploy/deploy-yas-configuration.sh` |
| CI build & push Docker image | **Member 2** | `.github/workflows/*-ci.yaml` trong fork YAS |
| **CD pipeline** (Jenkins deploy + teardown) | **Bạn (Member CD)** | `jenkins/` + `k8s/charts/` |

**Lý do tách rõ**: tránh xung đột khi nhiều thành viên cùng đụng cluster. Nguyên tắc *least privilege* + *single responsibility* — mỗi người có RBAC phạm vi hẹp, sửa code trong scope mình quản.

### 1.3. Kiến trúc cluster

```
1 GKE cluster (yas-cluster, us-central1)
├── ns yas                  ← app YAS (CD của bạn)
├── ns keycloak             ← Keycloak auth (Member 1)
├── ns postgres             ← Postgres DB (Member 1)
├── ns kafka                ← Kafka + Strimzi (Member 1)
├── ns elasticsearch        ← ES (Member 1)
├── ns jenkins              ← Jenkins controller (bạn)
├── ns observability        ← Prometheus (Member 1)
├── ns cert-manager
└── ns kube-system          ← GKE managed
```

**Pod khác namespace gọi nhau qua FQDN** `<service>.<namespace>.svc.cluster.local`. Ví dụ pod ở `yas` gọi Postgres bằng `postgresql.postgres.svc.cluster.local`.

---

## 2. Continuous Integration (CI) — của Member 2

### 2.1. Vị trí
GitHub Actions chạy trên fork YAS (`https://github.com/HuynhLuan05/YAS.git`).

### 2.2. Mỗi service có 1 workflow riêng (vd `order-ci.yaml`)

```yaml
on:
  push:
    branches: [main]
    paths: ["order/**", ...]      ← path filter
jobs:
  Build:
    steps:
      - mvn clean install -pl order -am
      - Checkstyle / SonarCloud / OWASP / JaCoCo
      - Docker login
      - Build & push image:
          tag = ${{ github.sha }}   (immutable, per commit)
          tag = latest              (chỉ khi main)
```

### 2.3. Pattern monorepo + path filter — ý nghĩa

| Đặc điểm | Lý do |
|---|---|
| Path filter `paths: order/**` | Chỉ build khi service đó thực sự thay đổi → tiết kiệm CI minutes |
| Tag immutable bằng SHA | Mỗi commit có image riêng — reproducible, có thể rollback chính xác |
| Tag `latest` chỉ trên main | Tránh feature branch ghi đè `latest` của main (gây nhiễu CD) |
| Multi-step (test, security, coverage trước build) | "Fail fast" — không build image nếu code không pass quality gate |

### 2.4. Hệ quả pattern monorepo
Commit chỉ sửa 1 service → chỉ image của service đó có SHA mới. **Các service khác giữ SHA cũ.** Đây là điểm CD phải tính đến (xem mục 4.2).

### 2.5. Hạn chế hiện tại
- Workflow chỉ trigger trên `main` (`branches: [main]`). Feature branch không build image → muốn deploy feature phải merge main trước, hoặc Member 2 mở rộng `branches: ['feature/**']`.

---

## 3. Continuous Deployment (CD) — của bạn

### 3.1. Hai job Jenkins

| Job | Mục đích | Frequency |
|---|---|---|
| `developer_build` | Deploy/upgrade tất cả app release vào ns `yas` | Mỗi lần dev muốn test build mới |
| `teardown` | Gỡ tất cả app release để reset môi trường | Khi cần làm sạch trước demo / cuối ngày / debug |

Cả 2 job dùng cùng Jenkins agent pod `yas-deployer` (image custom có `helm`, `kubectl`, `jq`, `curl`).

### 3.2. Vị trí & cách Jenkins load

- File: `jenkins/jobs/developer_build/Jenkinsfile`, `jenkins/jobs/teardown/Jenkinsfile`.
- JCasC (`jenkins/values.yaml`) seed 2 pipeline job, mỗi job point tới Jenkinsfile từ branch `*/main` (đặc tả qua `pipelineJob { definition { cpsScm { ... branch('*/main') } } }`).
- Cài bằng `bash jenkins/install.sh` → `helm upgrade --install jenkins jenkins/jenkins -f values.yaml`.

### 3.3. RBAC cho agent pod

`jenkins/rbac.yaml` tạo:
- ServiceAccount `yas-deployer` trong ns `jenkins`.
- Role trong ns `yas` cho phép CRUD: pods, services, configmaps, secrets, deployments, ingresses, … (đủ cho `helm install/uninstall` các app chart).
- ClusterRole đọc nodes (để `Publish access URL` lấy node external IP fallback).

**Nguyên tắc**: cấp quyền tối thiểu cần để pipeline làm việc, không cấp thêm. Không có quyền vào ns `keycloak`/`postgres`/`kube-system` → không thể vô tình phá infra của Member 1.

---

## 4. `developer_build` pipeline — chi tiết

### 4.1. Cấu trúc stage

```
1. Checkout                       → clone Project-1 repo
2. Resolve SHAs                   → quyết định image tag cho mỗi service
3. Helm dependency build          → repackage subcharts từ source
4. Deploy (helm upgrade --install) → install 13 service theo thứ tự
5. Deploy swagger-ui              → install swagger-ui riêng (image public)
6. Publish access URL             → in URL + hosts line cho dev
```

### 4.2. Stage `Resolve SHAs` — design quan trọng nhất

**Mỗi service có parameter `BRANCH_<SVC>`** (mặc định `main`). Pipeline resolve image tag theo cách:

```groovy
if (branch == 'main') {
  // Query Docker Hub for latest 40-char SHA tag of this service
  sha = curl 'docker hub API .../tags?page_size=100&ordering=last_updated'
        | jq 'select name matches ^[0-9a-f]{40}$' | head -1
} else {
  // Resolve git HEAD of feature branch
  sha = git ls-remote refs/heads/<branch> | awk '{print $1}'
}
```

#### Vì sao thiết kế per-service như vậy

Bài toán: CI monorepo với path filter → mỗi service có thể đang ở SHA khác nhau. Pipeline cũ "resolve cùng 1 SHA = HEAD main cho mọi service" → ImagePullBackOff cho service không có image SHA mới.

Pipeline mới (Cách C đã chọn):
- `main` → query Docker Hub: lấy **SHA mới nhất từng được CI push**. Mỗi service tự lập biểu, không cần đồng bộ.
- Feature branch → git resolve. Dev chủ động set BRANCH_X = feature/Y để test image branch đó (CI phải build sẵn cho branch Y).

#### Trade-off
- (+) Robust: build luôn pass miễn image tồn tại trên registry.
- (+) Dev workflow tự nhiên: muốn test feature thì đặt BRANCH_X.
- (–) `main` không tracking HEAD-of-main → có thể lệch commit thực tế (nhưng đây đúng spec — "latest available", không phải "latest commit").

### 4.3. Stage `Helm dependency build` — fix bug subchart staleness

```groovy
SERVICES.keySet().each { svc ->
  sh """
    rm -rf ${CHARTS_DIR}/${svc}/charts          ← XÓA tgz cũ
    rm -f  ${CHARTS_DIR}/${svc}/Chart.lock      ← XÓA lockfile
    helm dependency build ${CHARTS_DIR}/${svc}  ← repackage từ source
  """
}
```

#### Lý do bắt buộc xóa trước khi build

`helm dependency build` chỉ rebuild khi `Chart.lock` khác `Chart.yaml`. Vì subchart `backend version: 0.1.0` không bump khi sửa → digest match → giữ tgz cũ → silent deploy code cũ. **Bug khó debug**: source code update nhưng pipeline deploy version trước đó.

Cố tình wipe trước mỗi build → đảm bảo luôn dùng source mới nhất. Overhead ~30-60s cho 13 chart, không đáng kể so với 25 phút build.

#### Alternative đẹp hơn (đã đề xuất, không triển khai)
Bump `backend/Chart.yaml: version: 0.1.0 → 0.1.1` mỗi khi sửa subchart, theo SemVer. Nhưng cần discipline cao — dev phải nhớ bump. Project học → wipe-and-rebuild đơn giản & an toàn hơn.

### 4.4. Stage `Deploy` — Helm install order

```groovy
def order = [
  'backoffice-bff', 'storefront-bff', 'backoffice-ui', 'storefront-ui',
  'customer', 'product', 'media', 'inventory', 'location',
  'cart', 'order',
  'search', 'tax', 'sampledata',
]
order.each { svc ->
  helm upgrade --install ${svc} k8s/charts/${svc} \
    --namespace yas \
    --set <imageKey>.image.repository=docker.io/$DOCKERHUB_USER/<imageName> \
    --set <imageKey>.image.tag=<sha> \
    --set backend.ingress.host=<host> \
    --wait --timeout 25m
```

#### Vì sao có thứ tự
Mirror `deploy-yas-applications.sh` của Member 1: BFF/UI trước, microservice sau. Mặc dù Kubernetes tự handle dependency theo readiness probe, đặt order giúp log dễ đọc + giảm race condition khi cluster nhỏ resource.

#### Vì sao `--wait --timeout 25m`
- `--wait`: helm chờ pod Ready (qua readiness probe) trước khi tiếp service kế.
- `25m`: Spring Boot warm-up có thể chậm (startup probe `delay=30s period=10s failure=30` ≈ 5 phút). Cộng thêm DB migration (Liquibase), OIDC discovery → buffer rộng.
- Pipeline-level timeout 90 phút bao trùm cả 13 service worst-case.

#### Tại sao bỏ qua các service `payment*, promotion, rating, recommendation, webhook`
Out of scope sprint hiện tại. Comment trong code để dev sau biết thêm lại bằng cách nào.

### 4.5. Stage `Deploy swagger-ui` riêng
swagger-ui dùng chart độc lập (không subchart `backend`/`ui`), image public `swaggerapi/swagger-ui:v5.x` (không phải build từ fork team). Tách stage riêng:
```groovy
helm upgrade --install swagger-ui k8s/charts/swagger-ui \
  --namespace yas \
  --set ingress.host=api.yas.local.com \
  --wait --timeout 5m
```
Đơn giản hơn: không resolve SHA, không inject `--set image.repository/tag`.

### 4.6. Stage `Publish access URL`

```groovy
1. kubectl get ingress yas-ingress -n yas -o jsonpath ... → ingress IP
2. fallback: bất kỳ ingress nào trong yas có LB IP
3. fallback cuối: node external/internal IP
```
In ra hosts line: `<ip> storefront.yas.local.com backoffice.yas.local.com api.yas.local.com identity.yas.local.com` — dev copy vào `/etc/hosts` máy mình để browser truy cập.

#### Vì sao cần `/etc/hosts`
Cluster GKE không có public DNS cho `*.yas.local.com`. Mọi domain này chỉ resolve **trên máy dev** qua hosts file → request đi đến Ingress IP → Ingress route theo Host header. Pattern "private DNS qua hosts" rất thường gặp trong dev env.

---

## 5. `teardown` pipeline — chi tiết

### 5.1. Parameters

| Param | Default | Vai trò |
|---|---|---|
| `KEEP_RELEASES` | `yas-configuration` | Whitelist release **không** bị gỡ. Default bảo vệ release infra của Member 1. |
| `CLEAN_PVC` | `false` | Có xóa PersistentVolumeClaim trong ns yas không (destructive). |

### 5.2. Stage flow

```
1. List existing releases     → audit log
2. Preflight: unstick stuck   → xóa Helm Secret của release pending-*/uninstalling
3. helm uninstall app releases → loop helm uninstall (skip KEEP_RELEASES)
4. Optional: delete PVCs      → nếu CLEAN_PVC=true
5. (post always) Verify       → in remaining releases + pods
```

### 5.3. Tại sao có `KEEP_RELEASES`

`yas-configuration` Helm release sinh các ConfigMaps + Secrets (`yas-configuration-configmap`, `*-extra-configmap`, `yas-*-credentials-secret`) mà **mọi backend pod mount qua envFrom/volumeMounts**. Nếu teardown xóa release này:
- Lần `developer_build` kế tiếp pod kẹt `ContainerCreating` mãi vì mount fail.
- Phải nhờ Member 1 chạy lại `deploy-yas-configuration.sh`.

→ Teardown phải biết ranh giới ownership: chỉ động vào release `developer_build` install, không đụng release Member 1 setup. KEEP_RELEASES là cơ chế khai báo ranh giới đó.

### 5.4. Tại sao có Preflight stage

Nếu lần teardown trước bị abort giữa chừng (timeout, Ctrl-C, Jenkins job stop) → release ở trạng thái `pending-upgrade` / `uninstalling` → khóa cứng mọi `helm` operation kế tiếp.

Preflight tự động phục hồi:
```bash
stuck=$(helm list -a --filter 'pending|uninstalling' -q)
for r in $stuck; do
  kubectl delete secret -l "owner=helm,name=$r"   # xóa Helm state secret
done
```
→ Helm "quên" release → stage uninstall sau xử lý sạch sẽ. Pipeline self-healing.

### 5.5. Tại sao retry với `--no-hooks`

```bash
if ! helm uninstall "$r" -n yas --timeout 25m; then
  helm uninstall "$r" -n yas --no-hooks --ignore-not-found --timeout 25m
fi
```

Một số chart có pre-delete/post-delete hook (vd cleanup Kafka topic). Hook fail → uninstall fail. `--no-hooks` bỏ qua → vẫn xóa được resource chính.

### 5.6. Tại sao verify chuyển vào `post { always }`

Trước đây Verify là 1 stage riêng → bị `skipped due to earlier failure(s)` khi uninstall lỗi. Chuyển vào `post { always }` → **luôn chạy**, kể cả khi pipeline fail → cho phép dev xem ngay "còn lại gì" mà không cần `kubectl get` thủ công.

### 5.7. Tại sao `disableConcurrentBuilds()`

2 build teardown chạy song song sẽ đánh nhau với helm (release lock). `disableConcurrentBuilds()` ép queue → an toàn.

---

## 6. Tích hợp Jenkinsfile với GitHub

### 6.1. JCasC pin branch trong `jenkins/values.yaml`

```groovy
pipelineJob('developer_build') {
  definition {
    cpsScm {
      scm {
        git {
          remote { url('https://github.com/HuynhLuan05/YAS.git') }
          branch('*/main')          ← Jenkinsfile load từ branch nào
        }
      }
      scriptPath('jenkins/jobs/developer_build/Jenkinsfile')
    }
  }
}
```

→ Mỗi lần bấm Build, Jenkins clone repo ở `main`, đọc Jenkinsfile, chạy. Bất kỳ commit mới vào main → build kế tiếp tự pickup.

### 6.2. Vì sao trỏ `main` thay vì branch dev

`main` là source of truth đã pass review. Pipeline production phải dùng version stable. Branch dev được dùng tạm trong lúc develop pipeline (vd `feat/i-cd`), sau khi merge xong update JCasC trỏ `main`.

### 6.3. Branch protection trên main

GitHub rule require PR review trước khi push main. Quy trình:
1. Tạo branch `feat/<topic>`.
2. Commit + push.
3. Tạo PR vào main, đợi review.
4. Merge.
5. Jenkins build kế tiếp pickup.

**Lý do**: chống push thẳng main, mọi thay đổi đều có history & review. Đây là pattern trunk-based development.

---

## 7. Sự cố đã gặp & cách giải quyết — bài học rút ra

### 7.1. `timestamps()` trong `options` báo lỗi compile

**Triệu chứng**: `Invalid option type "timestamps"`.
**Nguyên nhân**: Plugin Timestamper chưa cài trên Jenkins controller.
**Fix**: Bỏ `timestamps()` (hoặc nhờ Member 1 cài plugin).
**Bài học**: Pipeline phụ thuộc plugin → check plugin trước khi viết.

### 7.2. Teardown gỡ luôn `yas-configuration` → developer_build sau fail

**Triệu chứng**: Pod kẹt `ContainerCreating`, log `MountVolume.SetUp failed: configmap "yas-configuration-configmap" not found`.
**Nguyên nhân**: Teardown ban đầu `helm uninstall` mọi release → gỡ luôn release infra của Member 1.
**Fix**: Thêm parameter `KEEP_RELEASES=yas-configuration`, loại trừ khỏi loop uninstall.
**Bài học**: CD pipeline phải tôn trọng ranh giới ownership với infra layer.

### 7.3. Helm release kẹt `pending-upgrade` / `uninstalling` sau timeout

**Triệu chứng**: `helm upgrade` báo lỗi release đang busy, không gỡ được.
**Nguyên nhân**: Lần thao tác trước bị abort, để lại state dở.
**Fix**: Preflight stage trong teardown xóa Helm Secret để "force-clear" release.
**Bài học**: Pipeline production phải idempotent + self-healing.

### 7.4. Subchart staleness — backend tgz cũ không update

**Triệu chứng**: Sửa `k8s/charts/backend/values.yaml` thêm `sslmode=require`, nhưng pod vẫn dùng `sslmode=disable`. Connect Postgres fail.
**Nguyên nhân**: `helm dependency build` không repackage vì `Chart.lock` digest match → giữ tgz đã package từ phiên bản cũ.
**Fix**: Wipe `charts/` + `Chart.lock` trước mỗi `helm dependency build` trong pipeline.
**Bài học**: Helm subchart cache là pitfall phổ biến. Pipeline CI/CD phải đảm bảo dependency luôn fresh.

### 7.5. Postgres OOMKilled

**Triệu chứng**: `postgresql-0 0/1 OOMKilled, RESTARTS=20`. Mọi pod backend connect fail (`Connection refused`).
**Nguyên nhân**: Memory limit Postgres quá thấp, hết RAM bị kernel kill.
**Fix**: Member 1 bump memory limit trong Keycloak CR.
**Bài học**: Infra issue không thể fix từ pipeline CD. Cần monitoring + alerting cho infra metric.

### 7.6. Keycloak `KC_HOSTNAME` hardcode → OIDC issuer mismatch

**Triệu chứng**: Pod backend crash với `Issuer "http://identity.yas.local.com/realms/Yas" did not match requested issuer`.
**Nguyên nhân**: Keycloak CR set `spec.hostname.hostname=http://identity.yas.local.com` → Keycloak luôn trả issuer cố định, không dynamic theo caller.
**Fix**: Member 1 xóa hostname hardcode → Keycloak dynamic theo caller URL.
**Bài học**: Drift giữa config Keycloak ↔ config app khó debug. Pod cũ chạy nhờ cache JVM, pod mới mới lộ lỗi. CI/CD phơi bày drift sớm.

### 7.7. Bug `KEEP_RELEASES: parameter not set`

**Triệu chứng**: Stage teardown fail với exit code 2.
**Nguyên nhân**: `params.KEEP_RELEASES` không auto-export vào env của `sh ''...''`. `set -u` bắt biến chưa set → fail.
**Fix**: Khai báo trong `environment { KEEP_RELEASES = "${params.KEEP_RELEASES}" }`.
**Bài học**: Jenkins Groovy ≠ Bash. Param từ pipeline phải export thủ công nếu muốn dùng trong shell.

---

## 8. Best practices đã áp dụng

| Practice | Implementation | Mục đích |
|---|---|---|
| **Parameterized pipeline** | `BRANCH_<SVC>`, `KEEP_RELEASES`, `CLEAN_PVC` | Linh hoạt, không hardcode |
| **Idempotency** | Preflight unstick, KEEP_RELEASES, fresh dep build | Chạy bao nhiêu lần kết quả vẫn đúng |
| **Self-healing** | Auto-clear stuck releases, retry --no-hooks | Pipeline phục hồi sau abort |
| **Fail-fast** | `helm --wait --timeout`, abort khi không resolve được SHA | Lỗi lộ sớm, không treo |
| **Audit log** | List release trước/sau, archive `resolved-tags.json` | Trace được mỗi build deploy gì |
| **Least privilege** | RBAC chỉ cấp quyền cần thiết trong ns yas | Pipeline không thể phá infra |
| **Branch protection** | Main require PR + review | Code merge có kiểm soát |
| **Source of truth** | JCasC trong git, không config UI | Reproducible Jenkins setup |
| **Separation of concerns** | CI build image / CD deploy / Infra setup tách biệt | Mỗi người làm việc của mình |
| **Documentation** | Comment trong Jenkinsfile + file này | Onboarding dễ |

---

## 9. Câu hỏi vấn đáp mẫu & câu trả lời

### Q1. CI/CD là gì, khác nhau ra sao?

- **CI (Continuous Integration)**: Mỗi commit được tự động build + test → đảm bảo code merge vào main luôn pass. Output: artifact (image, jar) sẵn sàng deploy.
- **CD (Continuous Delivery/Deployment)**: Tự động (hoặc 1-click) deploy artifact đó vào môi trường (dev/staging/prod).

Trong project: CI = GitHub Actions build Docker image. CD = Jenkins helm upgrade.

### Q2. Vì sao tách CI và CD thành 2 hệ thống (GitHub Actions vs Jenkins)?

- GitHub Actions tích hợp sẵn với GitHub, scale tốt, free cho public repo → hợp build/test.
- Jenkins chạy bên trong cluster GKE → có RBAC + network access tới K8s API, deploy không cần expose API public → hợp CD.
- Tách biệt cũng đúng best practice: CI fast & ephemeral, CD slow & stateful.

### Q3. Vì sao Jenkins chạy trên Kubernetes (pod) thay vì VM?

- Tự scale agent (mỗi build 1 pod).
- ServiceAccount của agent có RBAC trực tiếp trên cluster → không cần kubeconfig file (rủi ro lộ).
- Cùng cluster với app → network local, gọi K8s API qua `kubernetes.default.svc`.
- Reproducible setup qua Helm chart.

### Q4. Per-service SHA resolution giải quyết vấn đề gì?

Monorepo CI với path filter → mỗi service có SHA mới khác nhau (chỉ service nào bị sửa mới có image mới). Nếu pipeline cũ resolve cùng 1 SHA cho mọi service → ImagePullBackOff cho service không có image SHA mới. Per-service resolve qua Docker Hub API → mỗi service tự lấy "latest available", không bị block.

### Q5. KEEP_RELEASES có vai trò gì?

Cơ chế khai báo "release nào ngoài scope teardown". Bảo vệ `yas-configuration` (release infra của Member 1) không bị gỡ. Nếu gỡ → ConfigMap/Secret mất → pod backend kẹt ContainerCreating ở lần build kế tiếp.

### Q6. Subchart staleness — tại sao lại có và cách phòng?

`helm dependency build` chỉ rebuild khi `Chart.yaml` thay đổi (so với `Chart.lock`). Sửa source subchart mà không bump version → digest match → giữ tgz cũ.

**Phòng**: 
- Bump version mỗi khi sửa (SemVer best practice).
- Hoặc wipe `charts/` + `Chart.lock` trước build (cách project này dùng).

### Q7. Idempotency của teardown nghĩa là gì?

Chạy nhiều lần liên tiếp đều ra cùng kết quả, không gây lỗi. Cụ thể:
- Lần 1: gỡ 13 release.
- Lần 2: không có gì để gỡ → SUCCESS (không fail vì "release not found").

Implementation: `helm uninstall --ignore-not-found`, kiểm tra empty list trước loop.

### Q8. Vì sao `--wait` quan trọng trong helm install?

Không có `--wait`: helm install trả về ngay khi resource được create (chưa Ready). Pipeline coi như SUCCESS → stage sau chạy → có thể call service chưa sẵn sàng.

Có `--wait`: helm chờ tất cả pod pass readiness probe trước khi return. Pipeline đảm bảo môi trường thật sự sẵn sàng.

### Q9. Drift configuration là gì? Project này gặp ở đâu?

Drift: cấu hình runtime của cluster lệch so với cấu hình trong git/code.

Project gặp:
- ConfigMap `yas-configuration` từng có URL internal đúng → có người `helm upgrade` ad-hoc với default values → CM bị overwrite URL public sai → pod cũ vẫn chạy nhờ cache, pod mới crash.

**Phòng**: 
- Đóng băng cách install (chỉ qua script chuẩn).
- GitOps (ArgoCD/Flux) — git là single source of truth.

### Q10. Tại sao pod ở ns `yas` resolve được Postgres ở ns `postgres`?

Kubernetes DNS: mỗi Service được expose qua `<svc>.<ns>.svc.cluster.local`. Pod ở `yas` query DNS với FQDN → CoreDNS/kube-dns trả ClusterIP → connect.

Pod query short name (`postgres`) → DNS search list:
```
search yas.svc.cluster.local svc.cluster.local cluster.local
```
→ Resolve `postgres.yas.svc.cluster.local` trước. Nếu ns `yas` có Service `postgres` ExternalName trỏ tới ns postgres → cũng OK.

### Q11. RBAC trong project được thiết kế ra sao?

- ServiceAccount `yas-deployer` ở ns `jenkins`.
- Role chỉ trong ns `yas` (CRUD app resources).
- ClusterRole nhỏ cho `nodes` (read).

→ Agent pod **chỉ làm được trong ns yas** + đọc nodes. Không vào được `keycloak`, `postgres`, `kube-system`. An toàn nếu agent bị compromise.

### Q12. Pipeline có self-healing như nào?

- **Stuck release**: Preflight stage xóa Helm Secret để Helm "quên" release kẹt.
- **Uninstall fail**: Retry với `--no-hooks --ignore-not-found`.
- **Subchart staleness**: Wipe `charts/` mỗi build.
- **Image không tồn tại** (BRANCH=main): Query Docker Hub lấy SHA latest available.

→ Lần build sau lỗi vẫn có cơ hội tự phục hồi mà không cần thao tác tay.

### Q13. Khi nào dùng `--reuse-values`, khi nào dùng `--reset-values`?

- `--reuse-values`: merge values cũ với `--set` mới. Dùng khi muốn giữ config trước, chỉ override 1 vài key.
- `--reset-values`: bỏ values cũ, chỉ dùng chart default + `--set` mới. Dùng khi muốn rollback config tới state chuẩn.

Project: pipeline luôn `helm upgrade --install` với đầy đủ `--set` cần thiết → mặc định Helm dùng new values, không cần explicit flag.

### Q14. Trade-off giữa "always refresh dep" vs "version bump"?

| Cách | Pros | Cons |
|---|---|---|
| Always refresh (wipe charts/) | Đơn giản, dev không nhớ bump | Mỗi build chậm thêm 30-60s |
| Bump version SemVer | Đúng best practice, có history | Cần discipline team |

Project học → chọn "always refresh" vì priority là đảm bảo hoạt động đúng > performance.

### Q15. Multi-branch CI hỗ trợ làm sao?

Sửa workflow `on.push.branches`:
```yaml
branches:
  - main
  - 'feature/**'           ← glob pattern
```

Cộng với:
- Concurrency cancel-in-progress (tiết kiệm CI minutes).
- Multi-tag image (SHA + branch name + latest).

---

## 10. Sơ đồ tổng quan

```
┌────────────────────────────────────────────────────────────────┐
│ Developer pushes code                                          │
│         ↓                                                       │
│  GitHub Actions (CI) trên YAS fork                             │
│   - Build & test mỗi service đổi (path filter)                 │
│   - Push image: docker.io/<user>/yas-<svc>:<sha>               │
│         ↓                                                       │
│  Docker Hub (registry)                                          │
└────────────────────────────────────────────────────────────────┘
                                ↓
┌────────────────────────────────────────────────────────────────┐
│ Developer triggers Jenkins                                     │
│         ↓                                                       │
│  Jenkins developer_build (CD)                                  │
│   - Resolve SHA per service                                    │
│   - helm dependency build (fresh)                              │
│   - helm upgrade --install ×13 services                        │
│   - Publish access URL                                         │
│         ↓                                                       │
│  Kubernetes ns 'yas'                                            │
│   - Pods Running, Services exposed                              │
│   - Ingress yas-ingress route theo Host header                  │
└────────────────────────────────────────────────────────────────┘
                                ↓
┌────────────────────────────────────────────────────────────────┐
│ Dev access qua /etc/hosts:                                     │
│   storefront.yas.local.com / backoffice... / api... / identity│
│                                                                 │
│ Khi cần reset:                                                  │
│  Jenkins teardown → helm uninstall (skip yas-configuration)    │
│  → developer_build lại được ngay, không cần Member 1 setup lại │
└────────────────────────────────────────────────────────────────┘
```

---

## 11. Tổng kết — những gì đã đạt được

1. ✅ Pipeline `developer_build` tự động deploy 13 service + swagger-ui vào ns yas.
2. ✅ Pipeline `teardown` reset môi trường an toàn (preserve infra release).
3. ✅ Chu kỳ teardown ↔ build ổn định, có thể lặp lại vô số lần.
4. ✅ Per-service branch parameter hỗ trợ test feature branch riêng lẻ.
5. ✅ Self-healing với stuck releases + subchart staleness.
6. ✅ Audit log đầy đủ + access URL in ra cho dev.
7. ✅ RBAC tối thiểu, tách trách nhiệm rõ với Member 1 (infra) và Member 2 (CI).
8. ✅ JCasC source-controlled, Jenkins reproducible.

---

## 12. Hướng phát triển tiếp theo (production-grade)

| Hạng mục | Hiện tại | Production-grade |
|---|---|---|
| Trigger | Manual qua Jenkins UI | Webhook GitHub → auto build on merge main |
| Multi-environment | Chỉ ns `yas` | dev/staging/prod, mỗi ns 1 namespace + value overrides |
| GitOps | Helm push từ Jenkins | ArgoCD/Flux pull, git = source of truth |
| Secret management | Plain K8s Secret + Member 1 manual | Sealed Secrets / External Secrets (Vault, AWS Secrets Manager) |
| Image scanning | OWASP trong CI | Trivy/Snyk scan trước push, gate trên CVE critical |
| Observability | Prometheus chạy nhưng chưa wire | Grafana dashboard + alerting (Slack/PagerDuty) |
| Rollback | Manual `helm rollback` | Automated rollback khi healthcheck fail |
| Canary deploy | Không có | Argo Rollouts / Flagger với % traffic shift |
| Test ở pipeline | Không có | Smoke test sau deploy (Postman / k6) trước "mark as production" |

---

*Tài liệu này là kết quả thực hành & trao đổi vấn đáp về quy trình CI/CD đã triển khai trong project YAS. Dùng để ôn tập trước vấn đáp môn DevOps.*
