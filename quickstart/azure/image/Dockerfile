# A tiny, single-purpose image for the Wayfinder Azure quick start: it writes and
# reads a blob using the managed identity Wayfinder attaches to it, and serves the
# result as JSON over HTTP. Nothing here is Container-Instances-specific — it's a
# plain Linux container.
FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py .

EXPOSE 80
CMD ["python", "app.py"]
