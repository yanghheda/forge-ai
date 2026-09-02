.DEFAULT_GOAL := help

.PHONY: help format-check lint test build ci

help: ## 显示公开开发命令
	@awk 'BEGIN { FS = ":.*## " } /^[a-zA-Z0-9_-]+:.*## / { printf "  %-16s %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

format-check: ## 检查通用文本格式
	@./scripts/check-format.sh

lint: ## 检查仓库结构与治理规则
	@./scripts/check-repository.sh

test: ## 运行仓库基线与 forge-server 测试
	@./tests/repository-baseline.sh
	@./scripts/test-forge-server.sh

build: ## 构建当前阶段可交付的应用
	@./scripts/check-build-baseline.sh

ci: format-check lint test build ## 运行全部阻断式质量门禁
