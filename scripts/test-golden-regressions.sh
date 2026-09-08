#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "${repo_root}/forge-server"
./mvnw -q \
    -Dtest='RequirementTransitionIntegrationTest,DevelopmentQaGuardTest,PrecheckRuleRegistryTest,AgentRunIntegrationTest,InternalToolIntegrationTest,WebhookPersistenceIntegrationTest' \
    test

cd "${repo_root}"
./scripts/test-forge-agent.sh

cd "${repo_root}/forge-web"
npm test -- src/features/agent-run/model/run-reducer.test.ts
