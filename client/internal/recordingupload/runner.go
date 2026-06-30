package recordingupload

import (
	"context"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
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
		// 启动后立即扫一次，之后按配置间隔轮询，避免进程启动后还要等待一个周期。
		r.runOnce(ctx)
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
		}
	}
}

func (r *Runner) runOnce(ctx context.Context) {
	// discover 只负责把本地新文件登记进 manifest；process 才真正访问媒体服务。
	// manifest 是本地断点续传索引，进程重启后不会重复创建已知文件任务。
	r.discover()
	r.enforceRetention()
	tasks := make([]*Task, 0, len(r.manifest.Tasks))
	for _, task := range r.manifest.Tasks {
		tasks = append(tasks, task)
	}
	sort.Slice(tasks, func(i, j int) bool { return tasks[i].CreatedAt.Before(tasks[j].CreatedAt) })
	workerCount := r.cfg.UploadFileConcurrency
	if workerCount < 1 {
		workerCount = 1
	}
	sem := make(chan struct{}, workerCount)
	var wait sync.WaitGroup
	for _, task := range tasks {
		if ctx.Err() != nil {
			break
		}
		if task.Status == "READY" || task.Status == "LOCAL_DELETED" {
			continue
		}
		sem <- struct{}{}
		wait.Add(1)
		go func(task *Task) {
			defer wait.Done()
			defer func() { <-sem }()
			// 单个文件失败不影响后续文件；错误写回 manifest，下次扫描会继续尝试。
			if err := r.process(ctx, task); err != nil {
				task.Error = err.Error()
				task.UpdatedAt = time.Now()
				_ = r.manifest.update(task)
				log.Println("recording upload", task.FilePath, err)
			}
		}(task)
	}
	wait.Wait()
}

func (r *Runner) discover() {
	files, _ := filepath.Glob(filepath.Join(r.cfg.RecordingDirectory, "*"))
	if len(files) == 0 {
		log.Printf("recording upload scan found no files dir=%s", r.cfg.RecordingDirectory)
	}
	for _, path := range files {
		info, err := os.Stat(path)
		if err != nil || info.IsDir() || info.Size() == 0 || strings.HasPrefix(info.Name(), ".") {
			continue
		}
		// sourceID 由设备、文件名、大小、修改时间组成，用作服务端幂等键。
		// 同名文件被覆盖后 size/modTime 会变化，因此会被当作新源文件处理。
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
	log.Printf("file upload processing file=%s status=%s fileId=%s uploadId=%s",
		task.FilePath,
		task.Status,
		task.FileID,
		task.UploadID)
	if task.FileID != "" && waitingForPlayback(task.Status) {
		// 上传完成后服务端还要做校验、HLS 转码和缩略图生成；客户端只轮询状态。
		response, err := r.client.status(ctx, task.FileID)
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
		SourceFileID: task.SourceFileID,
		DeviceID:     r.cfg.RecordingDeviceID,
		FileType:     fileType(info.Name()),
		FileName:     info.Name(),
		ContentType:  contentType(info.Name()),
		FileSize:     info.Size(),
	})
	if err != nil {
		return err
	}
	task.FileID = upload.FileID
	task.UploadID = upload.UploadID
	if upload.UploadID == "" {
		// 服务端识别到该源文件已完成或正在后处理时，客户端无需再上传原文件。
		log.Printf("file upload server says upload not required fileId=%s status=%s",
			upload.FileID,
			upload.Status)
		task.Status = upload.Status
		task.UpdatedAt = time.Now()
		return r.manifest.update(task)
	}
	task.Status = "UPLOADING"
	_ = r.manifest.update(task)
	log.Printf("file upload started fileId=%s uploadId=%s parts=%d partSize=%d",
		upload.FileID,
		upload.UploadID,
		upload.PartCount,
		upload.PartSize)
	if err := uploadMissingParts(ctx, r.client, file, upload, r.cfg.UploadPartConcurrency, r.cfg.UploadPartURLBatchSize); err != nil {
		return err
	}
	// 所有缺失分片上传完成后由服务端 complete 合并对象；非视频 READY，视频进入 PROCESSING。
	if _, err := r.client.complete(ctx, upload.UploadID); err != nil {
		return err
	}
	log.Printf("file upload original file completed fileId=%s", upload.FileID)
	task.Status = "PROCESSING"
	task.UpdatedAt = time.Now()
	return r.manifest.update(task)
}

func waitingForPlayback(status string) bool {
	return status == "PROCESSING"
}

func fileType(name string) string {
	switch strings.ToLower(filepath.Ext(name)) {
	case ".mp4", ".mov", ".m4v":
		return "VIDEO"
	case ".jpg", ".jpeg", ".png", ".webp":
		return "IMAGE"
	case ".log", ".txt":
		return "LOG"
	case ".json", ".yaml", ".yml", ".toml", ".ini", ".conf":
		return "CONFIG"
	case ".map":
		return "MAP"
	case ".pdf", ".doc", ".docx", ".xls", ".xlsx":
		return "DOCUMENT"
	default:
		return "OTHER"
	}
}

func contentType(name string) string {
	switch strings.ToLower(filepath.Ext(name)) {
	case ".mp4", ".m4v":
		return "video/mp4"
	case ".mov":
		return "video/quicktime"
	case ".jpg", ".jpeg":
		return "image/jpeg"
	case ".png":
		return "image/png"
	case ".webp":
		return "image/webp"
	case ".json":
		return "application/json"
	case ".txt", ".log":
		return "text/plain"
	case ".pdf":
		return "application/pdf"
	default:
		return "application/octet-stream"
	}
}
