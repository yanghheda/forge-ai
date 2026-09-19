FROM python:3.12.11-slim AS base

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app/src

WORKDIR /app

FROM base AS test
COPY forge-agent/requirements.lock ./
RUN pip install --no-cache-dir -r requirements.lock
COPY forge-agent/pyproject.toml ./
COPY forge-agent/src ./src
COPY forge-agent/tests ./tests
COPY packages/forge-contracts /packages/forge-contracts
CMD ["pytest", "-q"]

FROM base AS runtime
RUN groupadd --system forge \
    && useradd --system --gid forge --home-dir /app forge \
    && mkdir -p /app/data \
    && chown forge:forge /app/data
COPY forge-agent/requirements.runtime.lock ./
RUN pip install --no-cache-dir -r requirements.runtime.lock
COPY --chown=forge:forge forge-agent/src ./src
COPY --chown=forge:forge packages/forge-contracts /app/contracts
USER forge
EXPOSE 8000
CMD ["uvicorn", "forge_agent.main:create_app", "--factory", "--host", "0.0.0.0", "--port", "8000"]
