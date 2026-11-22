#!/usr/bin/env bash
set -euo pipefail

destination=${1:?Provide a destination directory for the verified Kubernetes tools.}
mkdir -p "$destination"
cd "$destination"

curl -fsSL --retry 3 https://github.com/kubernetes-sigs/kind/releases/download/v0.33.0/kind-linux-amd64 -o kind
curl -fsSL --retry 3 https://dl.k8s.io/release/v1.36.4/bin/linux/amd64/kubectl -o kubectl
curl -fsSL --retry 3 https://get.helm.sh/helm-v4.3.2-linux-amd64.tar.gz -o helm.tar.gz
curl -fsSL --retry 3 https://traefik.github.io/charts/traefik/traefik-42.4.1.tgz -o traefik.tgz

sha256sum --check <<'SUMS'
aee6151561422756b764a4ae28e7f44cda5af5a9eead3cc9985112b1de8d8e0d  kind
8b8f088da2dab964f853b38464033b1be15ede2839eca751482357c45abdd05a  kubectl
d1c0278d46beab79582d7e50f5b3dc206944ba8e577cde618513fd07daa53983  helm.tar.gz
80ea7246b74d5bcd1f86ac598feca6ba516a110dc0ad9e95dfda28d933b4acdb  traefik.tgz
SUMS
tar -xzf helm.tar.gz --strip-components=1 linux-amd64/helm
chmod +x kind kubectl helm
