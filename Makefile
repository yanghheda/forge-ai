.DEFAULT_GOAL := help

.PHONY: help format format-check lint test build contracts-check agent-test infra-check infra-up infra-ready infra-down host-infra-up host-infra-ready host-infra-down test-apps-up test-apps-ready test-apps-down apps-up apps-ready apps-down hardening-test hardening-performance hardening-faults smoke ci

help: ## 显示公开开发命令
	@awk 'BEGIN { FS = ":.*## " } /^[a-zA-Z0-9_-]+:.*## / { printf "  %-16s %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

format: ## 使用 Prettier 格式化 forge-web 源码与配置
	@cd forge-web && npm run format

format-check: ## 检查通用文本格式
	@./scripts/check-format.sh
	@cd forge-web && npm run format:check

lint: ## 检查仓库结构与治理规则
	@./scripts/check-repository.sh
	@cd forge-web && npm run lint
	@cd forge-web && npm run lint:boundaries
	@./scripts/test-forge-agent.sh

test: ## 运行仓库基线与 forge-server 测试
	@./tests/repository-baseline.sh
	@./tests/infrastructure-compose.sh
	@./scripts/test-forge-server.sh
	@./scripts/test-forge-web.sh

contracts-check: ## 校验 Tool 与 Skill 契约
	@./scripts/check-contracts.sh

agent-test: ## 运行 forge-agent 检查与测试
	@./scripts/test-forge-agent.sh

build: ## 构建当前阶段可交付的应用
	@./scripts/check-build-baseline.sh

infra-check: ## 校验本机基础设施 Compose 契约
	@./tests/infrastructure-compose.sh

infra-up: ## 启动本机 MySQL、Redis 与 Qdrant
	@docker compose --env-file deploy/.env -f deploy/compose.yml up -d --wait

infra-ready: ## 查看本机基础设施容器健康状态
	@docker compose --env-file deploy/.env -f deploy/compose.yml ps

infra-down: ## 停止基础设施并保留 MySQL/Qdrant 数据卷
	@docker compose --env-file deploy/.env -f deploy/compose.yml down

host-infra-up: ## 为宿主机应用启动并暴露本机基础设施
	@docker compose --env-file deploy/.env -f deploy/compose.yml -f deploy/compose.host-dev.yml up -d --wait

host-infra-ready: ## 查看宿主机开发基础设施健康状态与端口
	@docker compose --env-file deploy/.env -f deploy/compose.yml -f deploy/compose.host-dev.yml ps

host-infra-down: ## 停止宿主机开发基础设施并保留数据卷
	@docker compose --env-file deploy/.env -f deploy/compose.yml -f deploy/compose.host-dev.yml down

test-apps-up: ## 构建并启动独立测试环境，向本机映射三项数据服务
	@docker compose --profile applications --env-file deploy/.env -f deploy/compose.yml -f deploy/compose-test.yml up -d --build --wait

test-apps-ready: ## 查看测试环境应用、基础设施与端口
	@docker compose --profile applications --env-file deploy/.env -f deploy/compose.yml -f deploy/compose-test.yml ps

test-apps-down: ## 停止测试环境并保留测试数据卷
	@docker compose --profile applications --env-file deploy/.env -f deploy/compose.yml -f deploy/compose-test.yml down

apps-up: ## 使用正式配置构建并启动三应用与基础设施
	@docker compose --profile applications --env-file deploy/.env -f deploy/compose.yml up -d --build --wait

apps-ready: ## 查看正式环境三应用与基础设施健康状态
	@docker compose --profile applications --env-file deploy/.env -f deploy/compose.yml ps

apps-down: ## 停止正式环境三应用与基础设施并保留数据卷
	@docker compose --profile applications --env-file deploy/.env -f deploy/compose.yml down

hardening-test: ## 运行会话 33 安全、可靠性与泄密负向门禁
	@./scripts/test-session33-hardening.sh

hardening-performance: ## 使用 10k Work Item 验证真实分页 P95 与索引
	@./scripts/run-session33-performance.sh

hardening-faults: ## 逐项执行 DB、Redis、Qdrant 与 Agent 故障演练
	@./scripts/run-session33-fault-drills.sh

smoke: ## 运行三应用 Compose Smoke 并自动停止服务
	@./tests/three-app-smoke.sh

ci: format-check lint contracts-check test build ## 运行全部阻断式质量门禁
