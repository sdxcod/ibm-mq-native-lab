#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .secrets
chmod 700 .secrets
for secret in mqAppPassword mqAdminPassword; do
  if [[ ! -f ".secrets/$secret" ]]; then
    # Hex is intentionally shell/htpasswd friendly. Write no trailing newline.
    value=$(openssl rand -hex 24)
    printf '%s' "$value" > ".secrets/$secret"
  fi
  # Bind-mounted Docker secrets must be readable by MQ UID 1001 and app UID 10001.
  # The enclosing directory is private on the host and is excluded from git/context.
  chmod 644 ".secrets/$secret"
done
printf 'Local secrets ready. Existing passwords preserved.
'
