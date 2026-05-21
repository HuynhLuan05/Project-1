# Báo cáo phần CD — Member 3 (Jenkins CD Lead)

> Nội dung dưới đây điền vào các mục còn trống trong template `Report_DevOps_CD.pdf`. Phần CI (1.3, 2.4, 3.5) và Docker Hub (1.4, 2.5, 3.6) do thành viên khác phụ trách nên không nằm trong file này.
>
> Quy ước trình bày (theo trang 3–4 của template): Font Play, size 11, căn đều. Tiêu đề con đánh số như chương cha. Ảnh căn giữa, "Trong dòng với văn bản", có Figcaption size 11 in nghiêng.

---

# CHƯƠNG 1 — TỔNG QUAN NHIỆM VỤ

## 1.5  CD

**CD** có nhiệm vụ biến artifact Docker image — sản phẩm cuối cùng của bước CI và tích hợp Docker Hub — thành một deployment thực sự đang chạy trên cluster Kubernetes. Khác với CI vốn chỉ kiểm tra mã nguồn và đóng gói image, CD chịu trách nhiệm chọn image phù hợp cho từng service, đưa lên cluster đúng namespace, đồng thời cho phép developer chủ động triển khai một branch bất kỳ của một service để kiểm thử mà không ảnh hưởng các service còn lại.

- **Mục tiêu cần đạt** của phần CD là xây dựng một pipeline triển khai linh hoạt: developer có thể chỉ định branch cho từng service, hệ thống tự ánh xạ branch → commit SHA → image tag tương ứng đã được Docker Hub cấp phát ở bước trước, sau đó tự động chạy `helm upgrade` để cập nhật các service. Đồng thời, CD phải có cơ chế dọn dẹp môi trường để namespace có thể tái sử dụng cho lần triển khai kế tiếp.
- **Điều kiện đầu vào** của phần CD bao gồm: một cluster Kubernetes đã sẵn sàng (cài đặt cơ sở do Member 1 phụ trách), bộ Helm chart của hệ thống YAS trong thư mục `k8s/charts/` đã được cấu hình sẵn ảnh mặc định trỏ về `ghcr.io`, và đặc biệt là các image gắn tag theo `commit SHA` đã được push lên Docker Hub theo quy ước `docker.io/<DOCKERHUB_USERNAME>/yas-<service>:<sha>` (sản phẩm của phần tích hợp Docker Hub ở mục 1.4).

## 1.6  Triển khai Jenkins

Tích hợp **Jenkins** vào pipeline để đóng vai trò là control plane của khối CD. Jenkins được triển khai trực tiếp trên cluster Kubernetes bằng Helm chart chính thức `jenkins/jenkins`, toàn bộ cấu hình (plugin, agent, job, credential mapping) được khai báo dưới dạng code thông qua `values.yaml` và Configuration-as-Code (JCasC). Cách triển khai này giúp Jenkins có thể được tái dựng lại từ đầu chỉ bằng một lệnh, không phụ thuộc vào thao tác thủ công trên UI.

- **Mục tiêu cần đạt** của phần Jenkins là có một controller chạy ổn định trên cluster, được expose qua dịch vụ LoadBalancer để developer truy cập từ bên ngoài, đồng thời tự động khởi tạo sẵn hai job pipeline `developer_build` và `teardown` ngay từ lần khởi động đầu tiên. Toàn bộ plugin cần thiết (Kubernetes, Pipeline, Git, Job DSL, JCasC, Credentials Binding, Markup Formatter) phải được liệt kê trong `values.yaml` để không cần cài tay.
- **Điều kiện đầu vào** của phần này là một namespace `jenkins` riêng biệt, một bộ tài nguyên RBAC bao gồm ServiceAccount `yas-deployer`, Role trong namespace `yas` và ClusterRole đọc thông tin `nodes`. Cluster cần có storage class hỗ trợ PVC tối thiểu 20 GiB cho Jenkins home. Sau khi cài, người vận hành thêm credential `dockerhub-username` (Secret Text) vào Jenkins để pipeline biết owner image trên Docker Hub.

## 1.7  Pipeline `developer_build`

Pipeline `developer_build` hiện thực hoá **Yêu cầu 4** của đề bài: cho phép developer chọn branch cho từng service riêng lẻ, pipeline tự tìm commit SHA mới nhất của branch đó và triển khai đúng image tương ứng từ Docker Hub. Các service không được chọn sẽ giữ image mặc định, đảm bảo developer chỉ thay đổi đúng service mình quan tâm.

- **Mục tiêu cần đạt** của pipeline `developer_build` là cung cấp một giao diện "Build with Parameters" với một tham số `BRANCH_<SVC>` cho mỗi service trong tổng số 20 service triển khai được. Khi build kết thúc, trang kết quả của Jenkins phải hiển thị URL NodePort dạng có thể click trực tiếp để developer mở storefront, backoffice và Swagger UI mà không cần thao tác thêm trên cluster.
- **Điều kiện đầu vào** của pipeline này gồm: image cho mỗi commit SHA đã được Docker Hub host sẵn theo quy ước `docker.io/<user>/yas-<service>:<sha>` (output của Member 2); các Helm chart trong `k8s/charts/` hỗ trợ override `backend.image.repository`, `backend.image.tag` (hoặc `ui.image.*` cho service frontend); ingress của chart cho phép override host qua `--set <key>.ingress.host`. Ngoài ra cần một credential Jenkins tên `dockerhub-username` để pipeline biết owner image.

## 1.8  Pipeline `teardown`

Pipeline `teardown` hiện thực hoá **Yêu cầu 5** của đề bài: dọn dẹp toàn bộ release Helm trong namespace `yas` chỉ bằng một lần chạy. Pipeline đi kèm một thanh lan can `CLEAN_PVC` để developer có thể chủ động chọn giữ hay xoá dữ liệu Postgres/Kafka/Elasticsearch, tránh trường hợp vô tình xoá nhầm dữ liệu phục vụ kiểm thử.

- **Mục tiêu cần đạt** của pipeline `teardown` là có khả năng đưa namespace `yas` trở về trạng thái sạch — không còn release Helm nào, không còn pod chạy của các service YAS — để lần chạy `developer_build` kế tiếp có thể bắt đầu từ môi trường nhất quán. Khi tham số `CLEAN_PVC=true` được bật, các PersistentVolumeClaim sẽ bị xoá để reset toàn bộ dữ liệu của hệ thống.
- **Điều kiện đầu vào** của pipeline này là các service trong namespace `yas` đã được triển khai thông qua Helm (do `developer_build` thực hiện), nhờ vậy `helm list -n yas` sẽ liệt kê được đầy đủ release để gỡ. ServiceAccount `yas-deployer` cần có quyền `delete` trên pods, services, configmaps, secrets và persistentvolumeclaims trong namespace.

## 1.9  Custom Agent Image & RBAC

Để hai pipeline trên hoạt động được trong môi trường Kubernetes, cần một Jenkins agent có sẵn toàn bộ toolchain triển khai và một bộ RBAC vừa đủ để agent thao tác trên namespace `yas`. Đây là lớp hạ tầng nền dùng chung cho cả `developer_build` và `teardown`.

- **Mục tiêu cần đạt** là xây dựng một custom agent image (tên `yas-jenkins-agent`) chứa sẵn `kubectl`, `helm`, `git`, `jq`, `yq` với phiên bản cố định, đồng thời định nghĩa một pod template tên `yas-deployer` trong cấu hình Jenkins để các pipeline tham chiếu qua label. Về RBAC, mục tiêu là cấp đủ quyền CRUD cho ServiceAccount `yas-deployer` trong namespace `yas`, cộng thêm quyền đọc `nodes` ở phạm vi cluster để pipeline có thể lấy ExternalIP/InternalIP phục vụ in URL NodePort.
- **Điều kiện đầu vào** của phần này là một registry Docker Hub để chứa agent image, cluster cho phép tạo ServiceAccount, Role/RoleBinding và ClusterRole/ClusterRoleBinding. Vì cluster sử dụng node Intel, agent image cần được build với cờ `--platform linux/amd64`.

---

# CHƯƠNG 2 — CÁC BƯỚC THỰC HIỆN CẤU HÌNH

## 2.6  CD

Khối CD của hệ thống YAS được tổ chức quanh Jenkins và Helm. Toàn bộ luồng có thể tóm tắt qua năm bước lớn: (1) build và push custom agent image phục vụ Jenkins; (2) cài đặt Jenkins controller trên cluster cùng RBAC tương ứng; (3) khai báo hai pipeline `developer_build` và `teardown` bằng JCasC để chúng tự được seed; (4) chạy `developer_build` để triển khai các service theo branch lựa chọn của developer; (5) chạy `teardown` để dọn sạch namespace khi cần.

*Sơ đồ tổng quan luồng CD: developer → Jenkins controller → agent `yas-deployer` → namespace `yas` trên cluster → NodePort URL.*

## 2.7  Build & push custom Jenkins agent image

**Bước 1. Viết Dockerfile cho agent**

File `jenkins/agent.Dockerfile` được kế thừa từ image gốc `jenkins/inbound-agent:latest-jdk21` (JDK 21 để khớp với phiên bản Java của Jenkins controller 2.555.2). Trên nền tảng đó, agent được cài thêm các công cụ với phiên bản cố định:

- **kubectl v1.30.0** để thao tác Kubernetes resources.
- **helm v3.15.2** để chạy `helm upgrade`/`helm uninstall`.
- **yq v4.44.2** để xử lý file YAML khi cần.
- **git, jq, curl, bash, gettext-base** để hỗ trợ các đoạn shell trong Jenkinsfile.

Image build cuối chạy ở user `jenkins` (non-root) đúng tiêu chuẩn của agent image gốc.

**Bước 2. Build và push image lên Docker Hub**

Vì cluster GKE Autopilot dùng node kiến trúc `amd64`, image phải được build với `--platform linux/amd64`, sau đó push lên Docker Hub theo tên `docker.io/<DOCKERHUB_USERNAME>/yas-jenkins-agent:<tag>`. Tag image được tăng dần (`:1`, `:2`, `:3`…) mỗi khi có thay đổi toolchain để dễ rollback.

*Hình: kết quả build và danh sách tag của `yas-jenkins-agent` trên Docker Hub.*

## 2.8  Cấu hình RBAC và cài đặt Jenkins bằng Helm

**Bước 1. Áp dụng RBAC**

File `jenkins/rbac.yaml` khai báo các tài nguyên sau:

- ServiceAccount `yas-deployer` trong namespace `jenkins` — đây là identity mà các Jenkins agent dùng để gọi API Kubernetes.
- Role trong namespace `yas` cấp đầy đủ quyền (get/list/watch/create/update/patch/delete) trên các nhóm tài nguyên: core (pods, services, configmaps, secrets, PVCs, events…), apps (deployments, replicasets, statefulsets, daemonsets), batch (jobs, cronjobs), networking (ingresses, networkpolicies), autoscaling (HPAs), policy (PDBs) và **monitoring.coreos.com** (ServiceMonitor, PodMonitor, PrometheusRule).
- RoleBinding gắn ServiceAccount `yas-deployer` với Role trên trong namespace `yas`.
- ClusterRole `yas-deployer-node-reader` chỉ chứa quyền đọc (`get`, `list`) trên `nodes` — cần thiết để pipeline đọc ExternalIP/InternalIP và in URL NodePort.
- ClusterRoleBinding gắn ServiceAccount với ClusterRole nói trên.

**Bước 2. Chuẩn bị `values.yaml`**

File `jenkins/values.yaml` cấu hình Helm chart chính thức `jenkins/jenkins`. Các khối quan trọng:

- *Controller*: Service kiểu `LoadBalancer` trên port 8080, request 500m CPU / 1 GiB RAM, limit 1.5 CPU / 2 GiB RAM, JVM `-XX:MaxRAMPercentage=60` và tắt setup wizard. Startup probe được nâng lên 90 lần thử × 10 giây (≈ 15 phút) để chịu được pha scale-up node của GKE Autopilot.
- *Plugin*: liệt kê tường minh kubernetes, workflow-aggregator, workflow-job, git, github, github-branch-source, pipeline-utility-steps, configuration-as-code, job-dsl, description-setter, antisamy-markup-formatter, blueocean, credentials-binding.
- *Markup formatter*: `rawHtml` để cho phép pipeline ghi mô tả build chứa HTML — cần cho việc hiển thị URL NodePort có thể click.
- *JCasC seed*: dùng Job DSL khai báo sẵn hai pipeline `developer_build` và `teardown`, mỗi job trỏ về `Jenkinsfile` tương ứng trên branch `*/feat/jenkins-cd` của repo team.
- *Persistence*: PVC 20 GiB, storage class `standard-rwo`.
- *Agent pod template*: tên `yas-deployer`, label `yas-deployer`, image `docker.io/<user>/yas-jenkins-agent:<tag>`, dùng ServiceAccount `yas-deployer`, request 200m/512Mi, limit 1/1Gi, timeout kết nối 600 giây.

**Bước 3. Chạy `install.sh`**

Script `jenkins/install.sh` thực hiện tuần tự: (1) tạo namespace `jenkins` nếu chưa có; (2) `kubectl apply -f rbac.yaml`; (3) `helm repo add jenkins https://charts.jenkins.io`; (4) `helm upgrade --install jenkins jenkins/jenkins -n jenkins -f values.yaml`. Sau khi chạy xong, script in ra ExternalIP của LoadBalancer và lệnh lấy mật khẩu admin từ Secret.

**Bước 4. Thêm credential `dockerhub-username`**

Trong Jenkins UI, vào *Manage Jenkins → Credentials → System → Global*, thêm credential kiểu *Secret text* với ID `dockerhub-username` và giá trị là username Docker Hub đã dùng cho Member 2 CI. Pipeline sẽ đọc credential này để ghép URL image.

*Hình: trang chủ Jenkins sau khi cài, danh sách plugin trong "Manage Plugins", credential `dockerhub-username` trong tab Global credentials.*

## 2.9  Seed pipeline tự động qua JCasC

Khối `controller.JCasC.configScripts.jobs` trong `values.yaml` dùng Job DSL để khai báo hai pipeline ngay từ lần khởi động đầu tiên của Jenkins. Mỗi job được cấu hình:

- Tên job: `developer_build` và `teardown`.
- Định nghĩa pipeline bằng `pipelineJob`, kiểu `cpsScm` lấy script từ Git.
- Git remote: `https://github.com/HuynhLuan05/YAS.git`.
- Branch: `*/feat/jenkins-cd`.
- Script path: `jenkins/jobs/developer_build/Jenkinsfile` và `jenkins/jobs/teardown/Jenkinsfile`.
- Lightweight checkout: bật, để Jenkins chỉ tải Jenkinsfile thay vì clone toàn bộ repo.

Nhờ JCasC, nếu cần dựng lại Jenkins trên cluster mới chỉ cần chạy `install.sh`, không phải tạo job thủ công.

*Hình: dashboard Jenkins hiển thị hai job `developer_build` và `teardown` được seed tự động.*

## 2.10  Pipeline `developer_build` (Yêu cầu 4)

File `jenkins/jobs/developer_build/Jenkinsfile` định nghĩa pipeline declarative gồm 5 stage. Toàn bộ pipeline chạy trên agent có label `yas-deployer` (tham chiếu đến pod template trong `values.yaml`).

**Bước 1. Khai báo tham số cho từng service**

Pipeline khai báo bản đồ `SERVICES` chứa 20 service triển khai được, mỗi entry có hai thông tin: `imageKey` (giá trị `backend` hoặc `ui` tuỳ kiểu chart) và `imageName` (tên image Docker Hub). Sau đó, khối `parameters` sinh ra 20 tham số kiểu `string` đặt tên `BRANCH_<SVC>` (ví dụ `BRANCH_TAX`, `BRANCH_CUSTOMER`), mặc định là `main`. Developer chọn "Build with Parameters" và chỉ cần thay đổi branch của service mình muốn deploy.

**Bước 2. Stage *Resolve SHAs* — ánh xạ branch → commit SHA**

Pipeline duyệt từng service. Với service có tham số khác `main`, pipeline chạy:

```
git ls-remote https://github.com/HuynhLuan05/YAS.git refs/heads/<branch>
```

Lấy cột đầu tiên trong kết quả làm commit SHA. Nếu không tìm thấy SHA, pipeline dừng lại với lỗi để developer biết tên branch sai. Service nào để `main` thì tag được gán giá trị `null` — nghĩa là sẽ dùng image mặc định ghi sẵn trong Helm chart (ghcr.io).

Kết thúc stage, một bảng tổng hợp service/branch/tag được in ra log, đồng thời file `resolved-tags.json` được lưu làm artifact của build để truy vết về sau. Biến môi trường `_TAGS_JSON` được set để các stage sau dùng lại.

**Bước 3. Stage *Helm dependency build***

Vì các chart YAS có dependency, pipeline chạy `helm dependency build k8s/charts/<svc>` cho từng service trước khi triển khai. Lệnh được nối thêm `|| true` để các chart không có dependency vẫn pass.

**Bước 4. Stage *Deploy*  — triển khai bằng `helm upgrade --install`**

Pipeline duyệt service theo thứ tự ưu tiên: các BFF trước, các UI tiếp theo, các backend cuối cùng — đảm bảo các thành phần phụ thuộc được sẵn sàng trước. Với mỗi service:

- Đọc `imageKey`, `imageName` từ `SERVICES` và `sha` từ `_TAGS_JSON`.
- Nếu `sha` rỗng hoặc bằng chuỗi `"null"`: bỏ qua các cờ override image, dùng giá trị mặc định trong `values.yaml` của chart. Việc xử lý chuỗi `"null"` xuất phát từ thực tế `writeJSON`/`readJSON` của Jenkins biểu diễn `null` thành chuỗi này.
- Nếu có `sha`: thêm `--set ${imageKey}.image.repository=docker.io/${DOCKERHUB_USER}/${imageName}` và `--set ${imageKey}.image.tag=${sha}`.
- Thêm `--set ${imageKey}.ingress.host=<host>` với `host` được chọn theo service: `storefront.yas.local.com` cho storefront-bff/UI, `backoffice.yas.local.com` cho backoffice-bff/UI, `api.yas.local.com` cho các service còn lại.
- Chạy:
  ```
  helm upgrade --install <svc> k8s/charts/<svc> \
       --namespace yas --create-namespace \
       <image_flags> <host_flag> --wait --timeout 10m
  ```

Cờ `--wait --timeout 10m` đảm bảo pipeline chỉ chuyển sang service kế tiếp khi pod đã sẵn sàng.

**Bước 5. Stage *Publish access URL* — hiển thị NodePort URL có thể click**

Stage cuối thực hiện:

- Truy vấn `kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="ExternalIP")].address}'` để lấy ExternalIP.
- Nếu cluster không expose ExternalIP (như cluster private), fallback sang `InternalIP`.
- In ra dòng `/etc/hosts` để developer copy: `<nodeIP> storefront.yas.local.com backoffice.yas.local.com api.yas.local.com identity.yas.local.com`.
- Gán `currentBuild.description` là một đoạn HTML chứa 3 liên kết:
  - `http://storefront.yas.local.com:30001`
  - `http://backoffice.yas.local.com:30002`
  - `http://api.yas.local.com:30003/swagger-ui/index.html`

Vì plugin `antisamy-markup-formatter` đã được cấu hình `markupFormatter: rawHtml` trong `values.yaml`, Jenkins hiển thị các link này dạng có thể click trực tiếp trên trang build — đáp ứng đúng yêu cầu trực quan của Req. 4.

*Hình minh hoạ cho mục 2.10:*
- *Trang "Build with Parameters" hiển thị 20 tham số `BRANCH_<SVC>`.*
- *Log stage Resolve SHAs hiển thị bảng service / branch / tag.*
- *Log stage Deploy với lệnh `helm upgrade --install` đầy đủ cờ.*
- *Trang Build Result với mô tả HTML chứa 3 link NodePort có thể click.*

## 2.11  Pipeline `teardown` (Yêu cầu 5)

File `jenkins/jobs/teardown/Jenkinsfile` định nghĩa pipeline gọn 4 stage, cũng chạy trên agent `yas-deployer`, timeout tổng 20 phút.

**Bước 1. Khai báo tham số `CLEAN_PVC`**

Tham số duy nhất kiểu boolean, mặc định `false`. Khi để mặc định, pipeline chỉ gỡ release Helm và giữ nguyên PVC; khi bật `true`, pipeline xoá luôn các PVC trong namespace để reset hoàn toàn dữ liệu.

**Bước 2. Stage *List existing releases***

Pipeline in `helm list -n yas` để ghi lại tình trạng namespace trước khi gỡ — hữu ích cho việc kiểm tra sau này.

**Bước 3. Stage *helm uninstall all***

Pipeline lấy danh sách tên release qua `helm list -n yas -q`. Nếu danh sách rỗng, stage thoát thành công. Nếu có release, pipeline chạy:

```
helm list -n yas -q | xargs -r -n1 helm uninstall -n yas
```

Lệnh `xargs -r -n1` chạy `helm uninstall` cho từng release một, đảm bảo lỗi của một release không khiến các release còn lại bị bỏ qua.

**Bước 4. Stage *Optional: delete PVCs***

Stage này được bọc trong khối `when { expression { params.CLEAN_PVC } }` nên chỉ chạy khi tham số được bật. Bên trong, pipeline gọi:

```
kubectl -n yas delete pvc --all --wait=true
```

để xoá toàn bộ PVC của namespace.

**Bước 5. Stage *Verify***

Stage cuối in lại `helm list -n yas` và `kubectl get pods -n yas` để xác nhận namespace đã sạch.

*Hình minh hoạ cho mục 2.11:*
- *Trang "Build with Parameters" hiển thị checkbox `CLEAN_PVC`.*
- *Log của stage Uninstall hiển thị nhiều dòng `release "xxx" uninstalled`.*
- *Log stage Verify với `helm list` rỗng và `kubectl get pods` không còn pod nào.*

---

# CHƯƠNG 3 — KẾT QUẢ

## 3.7  Triển khai Jenkins

Sau khi hoàn thiện phần triển khai Jenkins, cluster đã có một controller chạy ổn định trên namespace `jenkins`, được expose qua LoadBalancer và có thể truy cập bằng địa chỉ ExternalIP do GKE cấp phát. Toàn bộ cấu hình (plugin, JCasC, pod template, persistence) đều nằm trong code dưới dạng `values.yaml` và `rbac.yaml`, nên có thể tái dựng Jenkins từ đầu chỉ bằng lệnh `install.sh`.

**Kết quả quan trọng nhất** của phần này là Jenkins được "infrastructure as code" hoá hoàn toàn: từ plugin đến pipeline đều được khai báo trong repo. Nhờ vậy, các phần tử của khối CD trở nên có thể tái lập, có thể review qua pull request và không phụ thuộc vào trí nhớ hay thao tác thủ công của bất kỳ thành viên nào.

Các kết quả chính **đạt được** của phần Jenkins bao gồm:
- Cài đặt Jenkins controller trên cluster Kubernetes bằng Helm chart chính thức.
- Build và push thành công custom agent image `yas-jenkins-agent` với toolchain cố định.
- Khai báo RBAC tối thiểu cần thiết cho ServiceAccount `yas-deployer`.
- Seed sẵn hai pipeline `developer_build` và `teardown` qua JCasC + Job DSL.
- Tích hợp credential `dockerhub-username` để pipeline có thể ghép URL image Docker Hub.

**Khó khăn lớn nhất** trong phần này là điều chỉnh startup probe của Jenkins: thiết lập mặc định của Helm chart không đủ thời gian cho cluster GKE Autopilot scale node, dẫn đến pod bị restart liên tục trong lần triển khai đầu. Đã nâng lên 90 lần thử × 10 giây để chịu được pha scale-up.

**Một lưu ý quan trọng** là agent image phải build với cờ `--platform linux/amd64` vì các node GKE Autopilot trong đợt triển khai dùng kiến trúc Intel; nếu build trên máy Apple Silicon mà quên cờ này, agent sẽ rơi vào loop `exec format error`.

## 3.8  Pipeline `developer_build`

Sau khi hoàn thiện pipeline `developer_build`, developer có thể vào Jenkins, chọn "Build with Parameters", chỉ định branch cho một hoặc nhiều service rồi bấm Build. Pipeline tự động giải SHA, ghép URL image Docker Hub, chạy `helm upgrade --install` cho từng service theo đúng thứ tự BFF → UI → backend, và cuối cùng hiển thị URL NodePort dạng click-able ngay trên trang build. Các service không được chọn sẽ giữ image mặc định, nên thay đổi của developer là cô lập, không gây side-effect.

**Kết quả quan trọng nhất** của pipeline này là cơ chế parameterized per-service: developer không phải triển khai lại cả hệ thống chỉ để kiểm thử một service, mà có thể trộn nhiều branch của nhiều service trong cùng một lần build. Điều này đáp ứng chính xác Yêu cầu 4 và rút ngắn vòng lặp kiểm thử của team backend xuống còn vài phút.

Các kết quả chính **đạt được** của pipeline `developer_build` bao gồm:
- Cấu hình thành công 20 tham số `BRANCH_<SVC>` cho từng service triển khai được.
- Resolve commit SHA từ branch qua `git ls-remote` chính xác và có kiểm soát lỗi.
- Triển khai từng service bằng `helm upgrade --install` với image override hoặc image mặc định.
- Hiển thị URL NodePort dạng HTML có thể click trên trang build.
- Sinh artifact `resolved-tags.json` để truy vết branch → SHA → image của mỗi lần triển khai.

**Khó khăn lớn nhất** trong phần này là xử lý sự khác biệt cấu trúc giữa các chart: một số service dùng key `backend.image.*`, một số khác (storefront, backoffice) lại dùng `ui.image.*`. Đã giải quyết bằng cách lưu `imageKey` riêng cho từng service trong bản đồ `SERVICES`, để pipeline ghép đúng cờ `--set` tương ứng.

**Một lưu ý quan trọng** là phải tách rõ trường hợp `branch == main` (không có cờ override, dùng image mặc định ghcr.io trong chart) với trường hợp branch khác (override sang Docker Hub theo SHA). Trong quá trình thử nghiệm, chuỗi `"null"` do `writeJSON`/`readJSON` tạo ra ban đầu bị nhầm thành tag hợp lệ, dẫn đến `helm` đi tìm image `:null`; lỗi này đã được xử lý ở commit `c23e9ed0`.

## 3.9  Pipeline `teardown`

Sau khi hoàn thiện pipeline `teardown`, chỉ cần một lần bấm Build là toàn bộ release Helm trong namespace `yas` được gỡ. Tham số `CLEAN_PVC` đóng vai trò thanh lan can, mặc định không xoá PVC để tránh mất dữ liệu của Postgres/Kafka/Elasticsearch khi developer chỉ muốn reset application layer.

**Kết quả quan trọng nhất** của pipeline này là namespace `yas` có thể trở về trạng thái sạch một cách nhất quán, sẵn sàng cho lần `developer_build` kế tiếp mà không cần thao tác `kubectl` thủ công.

Các kết quả chính **đạt được** của pipeline `teardown` bao gồm:
- Liệt kê và gỡ tự động toàn bộ release Helm trong namespace `yas`.
- Tuỳ chọn xoá PVC qua tham số `CLEAN_PVC` để reset dữ liệu khi cần.
- Stage Verify xác nhận namespace sạch sau khi teardown.

**Khó khăn lớn nhất** trong phần này là phải đảm bảo *mọi* deployment trong namespace `yas` đều được tạo qua Helm. Nếu một service vô tình được tạo bằng `kubectl apply` thuần (không có metadata Helm), `helm list` sẽ không thấy và teardown sẽ bỏ sót. Đã thống nhất với Member 1 và Member 4 rằng mọi thao tác trên namespace `yas` đều đi qua Helm hoặc ArgoCD (ArgoCD cũng dùng Helm template phía sau).

## 3.10  Khó khăn chung và lưu ý quan trọng của khối CD

Khối CD do Member 3 phụ trách đã trải qua một số khó khăn ngoài phạm vi từng pipeline riêng lẻ; phần này tổng hợp lại để các bạn vận hành sau lưu ý.

**Khó khăn về RBAC.** Trong lần triển khai đầu, các chart YAS có khai báo `ServiceMonitor` (thuộc CRD của Prometheus Operator) khiến Helm trả về lỗi `forbidden`. Đã bổ sung quyền cho nhóm tài nguyên `monitoring.coreos.com` vào Role của `yas-deployer` (commit `c23e9ed0`) để pipeline tạo được `ServiceMonitor`, `PodMonitor`, `PrometheusRule`.

**Khó khăn về plugin Jenkins.** Phiên bản đầu của Jenkinsfile có dùng các option `timestamps()` và `ansiColor('xterm')`, nhưng vì các plugin tương ứng không có trong danh sách `installPlugins` của `values.yaml` nên build fail ngay ở giai đoạn parse pipeline. Đã loại bỏ các option này (commit `3114903d`).

**Khó khăn về resolve SHA.** Phiên bản đầu của stage *Resolve SHAs* chạy `git ls-remote` ngầm định trên repo chứa pipeline, dẫn đến SHA trả về không khớp với image trên Docker Hub (vốn được build từ repo YAS gốc). Đã sửa rõ tham số URL thành `https://github.com/HuynhLuan05/YAS.git` (commit `c674ada7`).

**Lưu ý về phạm vi với Member 4.** Member 3 cố tình **không** cấu hình GitHub webhook trong Jenkins. Yêu cầu auto-deploy theo branch `main` và release tag `v*.*.*` được giao cho Member 4 hiện thực hoá bằng ArgoCD Application; nếu Jenkins cũng đăng ký webhook thì hệ thống sẽ deploy trùng và có thể tranh chấp trạng thái namespace. Ranh giới rõ ràng: Jenkins phục vụ developer build theo branch tuỳ chọn; ArgoCD phục vụ auto-sync trên main và staging.

## 3.11  Tổng kết phần CD

Sau khi hoàn thiện toàn bộ khối CD của Member 3, hệ thống YAS đã có một pipeline triển khai end-to-end: lấy đầu vào là image Docker Hub theo `commit SHA` (output mục 3.6), cho phép developer triển khai theo branch tuỳ chọn cho từng service qua `developer_build`, hiển thị URL NodePort có thể click ngay trên trang build, và dọn dẹp môi trường qua `teardown` khi không còn nhu cầu kiểm thử.

Khối CD này, cùng với khối CI và tích hợp Docker Hub đã trình bày ở các mục trước, tạo thành một pipeline DevOps liền mạch từ commit mã nguồn cho đến môi trường chạy thực trên cluster Kubernetes. Đây cũng là tiền đề để Member 4 mở rộng tiếp khả năng auto-sync nâng cao bằng ArgoCD và service mesh bằng Istio trong phần điểm cộng của đề bài.
