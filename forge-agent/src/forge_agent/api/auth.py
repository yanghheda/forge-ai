"""内部服务 JWT 校验。"""

from __future__ import annotations

from typing import Annotated

import jwt
from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from forge_agent.settings import AgentSettings

bearer = HTTPBearer(auto_error=False)


def get_settings(request: Request) -> AgentSettings:
    """从应用状态读取启动时已验证的配置。"""

    return request.app.state.settings


def require_internal_credential(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer)],
    settings: Annotated[AgentSettings, Depends(get_settings)],
) -> None:
    """只接受 forge-server 签发给 forge-agent 的短时 HS256 JWT。"""

    if credentials is None or credentials.scheme.lower() != "bearer":
        raise invalid_credential()

    try:
        claims = jwt.decode(
            credentials.credentials,
            settings.internal_jwt_secret,
            algorithms=["HS256"],
            audience=settings.internal_jwt_audience,
            issuer=settings.internal_jwt_issuer,
            options={"require": ["iss", "sub", "aud", "iat", "exp"]},
        )
    except jwt.PyJWTError as exception:
        raise invalid_credential() from exception

    if claims.get("sub") != "forge-server":
        raise invalid_credential()


def invalid_credential() -> HTTPException:
    """返回稳定且不泄露令牌校验细节的认证错误。"""

    return HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="invalid internal credential",
        headers={"WWW-Authenticate": "Bearer"},
    )
