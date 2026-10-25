# ==============================================================================
# CampusPulse AI Microservice — Dockerfile
# Python 3.11 Slim with Local Scikit-Learn Model
# ==============================================================================

FROM python:3.11-slim

WORKDIR /app

# Set Python environment variables (no bytecode on disk, unbuffered logs)
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PORT=8000

# Install dependencies first for optimal Docker layer caching
COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt

# Copy microservice source code, training script, dataset, and models directory
COPY app/ ./app/
COPY data/ ./data/
COPY training/ ./training/
COPY models/ ./models/

# Generate model artifact if not present (ensures self-contained container startup)
RUN python -m training.train

# Create unprivileged system user and assign ownership
RUN groupadd -r -g 10001 appgroup && \
    useradd -r -u 10001 -g appgroup -s /bin/false appuser && \
    chown -R appuser:appgroup /app

# Switch to unprivileged user
USER appuser

# Expose microservice HTTP port
EXPOSE 8000

# Container healthcheck
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD python -c "import os, urllib.request; p = os.environ.get('PORT', '8000'); urllib.request.urlopen(f'http://127.0.0.1:{p}/health')" || exit 1

# Start FastAPI microservice via Uvicorn on 0.0.0.0 honoring platform $PORT
CMD ["sh", "-c", "exec uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000}"]
