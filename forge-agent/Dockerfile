FROM python:3.12.11-slim AS base

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app/src

WORKDIR /app

FROM base AS test
COPY requirements.lock ./
RUN pip install --no-cache-dir -r requirements.lock
COPY pyproject.toml ./
COPY src ./src
COPY tests ./tests
CMD ["pytest", "-q"]

FROM base AS runtime
RUN groupadd --system forge && useradd --system --gid forge --home-dir /app forge
COPY requirements.runtime.lock ./
RUN pip install --no-cache-dir -r requirements.runtime.lock
COPY --chown=forge:forge src ./src
USER forge
EXPOSE 8000
CMD ["uvicorn", "forge_agent.main:create_app", "--factory", "--host", "0.0.0.0", "--port", "8000"]
