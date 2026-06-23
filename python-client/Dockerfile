FROM python:3.12-alpine

WORKDIR /app
RUN apk add --no-cache ffmpeg ca-certificates opus gstreamer gst-plugins-base gst-plugins-good
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY robot_media_client ./robot_media_client

ENTRYPOINT ["python", "-m", "robot_media_client"]
