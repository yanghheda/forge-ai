from __future__ import annotations

from pathlib import Path
from shutil import copytree

import pytest

from forge_agent.contracts.validator import ContractValidationError, validate_contracts

REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def test_repository_contracts_are_valid() -> None:
    validate_contracts(REPOSITORY_ROOT / "packages" / "forge-contracts")


def test_tool_without_risk_level_is_rejected(tmp_path: Path) -> None:
    fixture_root = tmp_path / "contracts"
    copytree(REPOSITORY_ROOT / "packages" / "forge-contracts", fixture_root)
    copytree(
        Path(__file__).parent / "fixtures" / "missing-risk" / "tools",
        fixture_root / "tools",
        dirs_exist_ok=True,
    )

    with pytest.raises(ContractValidationError, match="risk_level"):
        validate_contracts(fixture_root)


def test_skill_cannot_reference_unknown_tool(tmp_path: Path) -> None:
    fixture_root = tmp_path / "contracts"
    copytree(REPOSITORY_ROOT / "packages" / "forge-contracts", fixture_root)
    copytree(
        Path(__file__).parent / "fixtures" / "unknown-tool" / "skills",
        fixture_root / "skills",
        dirs_exist_ok=True,
    )

    with pytest.raises(ContractValidationError, match="不存在的 Tool"):
        validate_contracts(fixture_root)
