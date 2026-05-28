package recordingupload

import (
	"context"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"sort"
	"time"

	"robot-media-client/internal/config"
)

type Runner struct {
	cfg      config.Config
	client   *Client
	manifest *Manifest
}

func NewRunner(cfg config.Config) *Runner {
	return &Runner{
		cfg:      cfg,
		client:   NewClient(cfg.MediaServiceURL, cfg.RobotID),
		manifest: LoadManifest(cfg.RecordingManifestPath),
	}
}

func (r *Runner) Run(ctx context.Context) {
	log.Printf("recording upload runner started robotId=%s mediaService=%s dir=%s manifest=%s loadedTasks=%d",
		r.cfg.RobotID,
		r.cfg.MediaServiceURL,
		r.cfg.RecordingDirectory,
		r.cfg.RecordingManifestPath,
		len(r.manifest.Tasks))
	ticker := time.NewTicker(r.cfg.UploadScanInterval)
	defer ticker.Stop()
	for {
		r.runOnce(ctx)
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
		}
	}
}

func (r *Runner) runOnce(ctx context.Context) {
	r.discover()
	r.enforceRetention()
	tasks := make([]*Task, 0, len(r.manifest.Tasks))
	for _, task := range r.manifest.Tasks {
		tasks = append(tasks, task)
	}
	sort.Slice(tasks, func(i, j int) bool { return tasks[i].CreatedAt.Before(tasks[j].CreatedAt) })
	for _, task := range tasks {
		if ctx.Err() != nil {
			return
		}
		if task.Status == "READY" || task.Status == "LOCAL_DELETED" {
			continue
		}
		if err := r.process(ctx, task); err != nil {
			task.Error = err.Error()
			task.UpdatedAt = time.Now()
			_ = r.manifest.update(task)
			log.Println("recording upload", task.FilePath, err)
		}
	}
}

func (r *Runner) discover() {
	files, _ := filepath.Glob(filepath.Join(r.cfg.RecordingDirectory, "*.mp4"))
	if len(files) == 0 {
		log.Printf("recording upload scan found no mp4 files dir=%s", r.cfg.RecordingDirectory)
	}
	for _, path := range files {
		info, err := os.Stat(path)
		if err != nil || info.Size() == 0 {
			continue
		}
		sourceID := fmt.Sprintf("%s/%s/%d/%d", r.cfg.RecordingDeviceID, info.Name(), info.Size(), info.ModTime().Unix())
		if _, exists := r.manifest.Tasks[sourceID]; exists {
			continue
		}
		log.Printf("recording upload discovered file=%s size=%d", path, info.Size())
		_ = r.manifest.update(&Task{
			SourceFileID: sourceID,
			FilePath:     path,
			FileSize:     info.Size(),
			CreatedAt:    info.ModTime(),
			Status:       "PENDING",
			UpdatedAt:    time.Now(),
		})
	}
}

func (r *Runner) process(ctx context.Context, task *Task) error {
	log.Printf("recording upload processing file=%s status=%s recordingId=%s uploadId=%s",
		task.FilePath,
		task.Status,
		task.RecordingID,
		task.UploadID)
	if task.RecordingID != "" && waitingForPlayback(task.Status) {
		response, err := r.client.status(ctx, task.RecordingID)
		if err != nil {
			return err
		}
		task.Status = response.Status
		task.Error = response.ErrorCode
		task.UpdatedAt = time.Now()
		return r.manifest.update(task)
	}
	file, err := os.Open(task.FilePath)
	if err != nil {
		return err
	}
	defer file.Close()
	info, err := file.Stat()
	if err != nil {
		return err
	}
	upload, err := r.client.createOrResume(ctx, createRequest{
		SourceFileID:      task.SourceFileID,
		DeviceID:          r.cfg.RecordingDeviceID,
		FileName:          info.Name(),
		ContentType:       "video/mp4",
		FileSize:          info.Size(),
		RecordedStartedAt: info.ModTime().UTC(),
	})
	if err != nil {
		return err
	}
	task.RecordingID = upload.RecordingID
	task.UploadID = upload.UploadID
	if !upload.UploadRequired {
		log.Printf("recording upload server says upload not required recordingId=%s status=%s",
			upload.RecordingID,
			upload.RecordingStatus)
		task.Status = upload.RecordingStatus
		task.UpdatedAt = time.Now()
		return r.manifest.update(task)
	}
	task.Status = "UPLOADING"
	_ = r.manifest.update(task)
	log.Printf("recording upload started recordingId=%s uploadId=%s parts=%d partSize=%d",
		upload.RecordingID,
		upload.UploadID,
		upload.PartCount,
		upload.PartSize)
	if err := uploadMissingParts(ctx, r.client, file, upload, r.cfg.UploadPartConcurrency); err != nil {
		return err
	}
	if _, err := r.client.complete(ctx, upload.UploadID); err != nil {
		return err
	}
	log.Printf("recording upload original file completed recordingId=%s", upload.RecordingID)
	task.Status = "VERIFYING"
	task.UpdatedAt = time.Now()
	return r.manifest.update(task)
}

func waitingForPlayback(status string) bool {
	return status == "VERIFYING" || status == "PROCESSING_PLAYBACK"
}
