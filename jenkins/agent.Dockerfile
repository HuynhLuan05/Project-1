FROM --platform=linux/amd64 jenkins/inbound-agent:latest-jdk21

USER root

ARG KUBECTL_VERSION=v1.30.0
ARG HELM_VERSION=v3.15.2
ARG YQ_VERSION=v4.44.2

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates git jq bash gettext-base \
 && rm -rf /var/lib/apt/lists/* \
 && curl -fsSL -o /usr/local/bin/kubectl \
      "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl" \
 && chmod +x /usr/local/bin/kubectl \
 && curl -fsSL "https://get.helm.sh/helm-${HELM_VERSION}-linux-amd64.tar.gz" \
      | tar -xz -C /tmp \
 && mv /tmp/linux-amd64/helm /usr/local/bin/helm \
 && rm -rf /tmp/linux-amd64 \
 && curl -fsSL -o /usr/local/bin/yq \
      "https://github.com/mikefarah/yq/releases/download/${YQ_VERSION}/yq_linux_amd64" \
 && chmod +x /usr/local/bin/yq \
 && kubectl version --client \
 && helm version \
 && yq --version

USER jenkins


