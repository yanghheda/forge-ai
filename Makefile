.DEFAULT_GOAL := help

.PHONY: help format-check lint test build infra-check infra-up infra-ready infra-down ci

help: ## 显示公开开发命令
	@awk 'BEGIN { FS = ":.*## " } /^[a-zA-Z0-9_-]+:.*## / { printf "  %-16s %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

format-check: ## 检查通用文本格式
	@./scripts/check-format.sh

lint: ## 检查仓库结构与治理规则
	@./scripts/check-repository.sh

test: ## 运行仓库基线与 forge-server 测试
	@./tests/repository-baseline.sh
	@./tests/infrastructure-compose.sh
	@./scripts/test-forge-server.sh

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

ci: format-check lint test build ## 运行全部阻断式质量门禁
