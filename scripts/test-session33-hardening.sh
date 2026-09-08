#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

"${repository_root}/tests/secret-leak-scan.sh"
"${repository_root}/scripts/check-secret-leaks.sh"

"${repository_root}/forge-server/mvnw" --no-transfer-progress \
  -f "${repository_root}/forge-server/pom.xml" \
  -Dtest=ApiWebTest,CsrfIntegrationTest,WorkspaceProjectScopeIntegrationTest,DocumentRagIntegrationTest,InternalToolIntegrationTest,AgentRunIntegrationTest,WebhookPersistenceIntegrationTest,WorkItemIntegrationTest,RedisReadinessFailureIntegrationTest,GitLabUrlPolicyTest,GitLabHttpClientTest,QdrantVectorIndexClientTest,PrometheusEndpointTest,ReliabilityMetricsTest \
  test

cd "${repository_root}/forge-web"
npm test -- --run src/lib/api/transport.test.ts src/features/agent-run/model/run-reducer.test.ts
