#!/bin/sh
set -eu

# 优先使用项目 venv（含 ruff/pytest），否则回退 PATH 中的 python3；FORGE_AGENT_PYTHON 始终优先。
agent_python=${FORGE_AGENT_PYTHON:-}
if [ -z "$agent_python" ]; then
  repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
  if [ -x "$repo_root/forge-agent/.venv/bin/python" ]; then
    agent_python="$repo_root/forge-agent/.venv/bin/python"
  else
    agent_python=python3
  fi
fi
export PYTHONPATH='forge-agent/src'

"$agent_python" -m forge_agent.contracts.validator packages/forge-contracts

