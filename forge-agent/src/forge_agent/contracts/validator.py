"""共享 Tool/Skill YAML 契约校验器。"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import yaml
from jsonschema import Draft202012Validator


class ContractValidationError(ValueError):
    """聚合展示可定位的契约错误。"""


def load_document(path: Path) -> Any:
    """读取 JSON 或 YAML；解析错误由调用方作为门禁失败输出。"""

    with path.open(encoding="utf-8") as source:
        if path.suffix == ".json":
            return json.load(source)
        return yaml.safe_load(source)


def validate_contracts(root: Path) -> None:
    """校验 Schema、自身契约以及 Skill 对 Tool 名称的引用。"""

    schemas = root / "schemas"
    tool_schema = load_document(schemas / "tool-contract.schema.json")
    skill_schema = load_document(schemas / "skill.schema.json")
    Draft202012Validator.check_schema(tool_schema)
    Draft202012Validator.check_schema(skill_schema)

    errors: list[str] = []
    tool_names: set[str] = set()
    for path in contract_files(root / "tools"):
        document = load_document(path)
        errors.extend(validation_errors(path, document, tool_schema))
        if isinstance(document, dict) and isinstance(document.get("name"), str):
            tool_names.add(document["name"])

    for path in contract_files(root / "skills"):
        document = load_document(path)
        errors.extend(validation_errors(path, document, skill_schema))
        if isinstance(document, dict):
            for tool_name in document.get("allowed_tools", []):
                if tool_name not in tool_names:
                    errors.append(f"{path}: allowed_tools 引用了不存在的 Tool '{tool_name}'")

    if errors:
        raise ContractValidationError("\n".join(errors))


def contract_files(directory: Path) -> list[Path]:
    """只接受明确的契约文件扩展名，并保持稳定遍历顺序。"""

    return sorted(path for path in directory.iterdir() if path.suffix in {".json", ".yaml", ".yml"})


def validation_errors(path: Path, document: Any, schema: dict[str, Any]) -> list[str]:
    """把 jsonschema 路径转换为便于 CI 定位的文本。"""

    validator = Draft202012Validator(schema)
    messages: list[str] = []
    for error in sorted(validator.iter_errors(document), key=lambda item: list(item.absolute_path)):
        field_path = ".".join(str(part) for part in error.absolute_path) or "<root>"
        messages.append(f"{path}: {field_path}: {error.message}")
    return messages


def main() -> int:
    """命令行入口；任何契约错误以非零状态阻断 CI。"""

    parser = argparse.ArgumentParser(description="校验 ForgeAI Tool/Skill 契约")
    parser.add_argument("root", type=Path)
    args = parser.parse_args()
    try:
        validate_contracts(args.root)
    except (ContractValidationError, OSError, ValueError, yaml.YAMLError) as exception:
        print(exception)
        return 1
    print("Tool/Skill 契约校验通过")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
