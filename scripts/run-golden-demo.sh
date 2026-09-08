#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

"${repo_root}/scripts/load-golden-demo.sh"
cd "${repo_root}/forge-web"
npm run test:e2e
