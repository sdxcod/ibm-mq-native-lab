#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./scripts/init-lab.sh
kubectl apply -f k8s/namespace.yaml
kubectl -n ibm-mq-lab create secret generic mq-lab-passwords   --from-file=mqAppPassword=.secrets/mqAppPassword   --from-file=mqAdminPassword=.secrets/mqAdminPassword   --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -k k8s
kubectl -n ibm-mq-lab rollout status statefulset/mq --timeout=600s
printf 'MQ ready. Load the local agent image, then apply k8s/demo-job.yaml.
'
