from __future__ import annotations

import pytest
from pydantic import ValidationError

from forge_agent.settings import AgentSettings


def test_internal_jwt_secret_is_required() -> None:
    with pytest.raises(ValidationError):
        AgentSettings(_env_file=None)


def test_internal_jwt_secret_must_be_at_least_32_bytes() -> None:
    with pytest.raises(ValidationError):
        AgentSettings(internal_jwt_secret="too-short", _env_file=None)


def test_settings_do_not_define_business_database_or_gitlab_credentials() -> None:
    assert "mysql" not in AgentSettings.model_fields
    assert "database_url" not in AgentSettings.model_fields
    assert "gitlab_token" not in AgentSettings.model_fields
