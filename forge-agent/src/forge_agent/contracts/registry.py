"""运行时 Tool/Skill 契约 Registry；与 CI 校验共用 packages/forge-contracts 事实来源。"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml


class ContractRegistryError(ValueError):
    """契约文件缺失或内容非法时启动即失败，不带病运行。"""


@dataclass(frozen=True)
class ToolContract:
    """Agent 运行时需要的 Tool 契约投影。"""

    name: str
    version: int
    description: str
    input_schema: dict[str, Any]
    risk_level: str
    backend_path: str
    timeout_ms: int

    @property
    def medium_risk(self) -> bool:
        """MEDIUM 风险 Tool 受 Run 确认策略约束。"""

        return self.risk_level == "MEDIUM"


@dataclass(frozen=True)
class SkillContract:
    """Agent 运行时需要的 Skill 契约投影。"""

    name: str
    allowed_tools: tuple[str, ...]
    max_tool_calls: int


class ToolContractRegistry:
    """启动时一次性加载契约并提供查询；不提供运行时热更新。"""

    def __init__(self, root: Path) -> None:
        self._tools: dict[str, ToolContract] = {}
        self._skills: dict[str, SkillContract] = {}
        self._load_tools(root / "tools")
        self._load_skills(root / "skills")
        self._validate_skill_references()

    def find_tool(self, name: str) -> ToolContract | None:
        """按契约名称查找 Tool；未注册返回 None 由调用方决定拒绝。"""

        return self._tools.get(name)

    def find_skill(self, skill: str) -> SkillContract | None:
        """按 Skill 名查找（统一小写）；Manifest 中为 PRODUCT/UX 大写枚举。"""

        return self._skills.get(skill.lower())

    def effective_tool_names(self, skill: str) -> list[str]:
        """Skill 契约允许的 Tool 列表；未知 Skill 视为无任何工具。"""

        contract = self.find_skill(skill)
        return list(contract.allowed_tools) if contract else []

    def _load_tools(self, directory: Path) -> None:
        if not directory.is_dir():
            raise ContractRegistryError(f"tool contracts directory missing: {directory}")
        for path in sorted(directory.glob("*.yaml")):
            document = self._read_document(path)
            name = document.get("name")
            mapping = document.get("backend_mapping", {})
            if not isinstance(name, str) or not isinstance(mapping, dict):
                raise ContractRegistryError(f"invalid tool contract structure: {path}")
            if name in self._tools:
                raise ContractRegistryError(f"duplicate tool contract: {name}")
            self._tools[name] = ToolContract(
                name=name,
                version=int(document.get("version", 0)),
                description=str(document.get("description", "")),
                input_schema=dict(document.get("input_schema", {})),
                risk_level=str(document.get("risk_level", "")),
                backend_path=str(mapping.get("path", "")),
                timeout_ms=int(document.get("timeout_ms", 0)),
            )

    def _load_skills(self, directory: Path) -> None:
        if not directory.is_dir():
            raise ContractRegistryError(f"skill contracts directory missing: {directory}")
        for path in sorted(directory.glob("*.yaml")):
            document = self._read_document(path)
            name = document.get("name")
            if not isinstance(name, str):
                raise ContractRegistryError(f"invalid skill contract structure: {path}")
            if name.lower() in self._skills:
                raise ContractRegistryError(f"duplicate skill contract: {name}")
            limits = document.get("limits", {})
            self._skills[name.lower()] = SkillContract(
                name=name,
                allowed_tools=tuple(document.get("allowed_tools", [])),
                max_tool_calls=int(limits.get("max_tool_calls", 0))
                if isinstance(limits, dict)
                else 0,
            )

    def _validate_skill_references(self) -> None:
        for skill in self._skills.values():
            for tool_name in skill.allowed_tools:
                if tool_name not in self._tools:
                    raise ContractRegistryError(
                        f"skill '{skill.name}' allows unregistered tool '{tool_name}'"
                    )

    @staticmethod
    def _read_document(path: Path) -> dict[str, Any]:
        try:
            with path.open(encoding="utf-8") as source:
                document = yaml.safe_load(source)
        except yaml.YAMLError as exception:
            raise ContractRegistryError(f"invalid contract yaml: {path}") from exception
        if not isinstance(document, dict):
            raise ContractRegistryError(f"contract is not a mapping: {path}")
        return document
