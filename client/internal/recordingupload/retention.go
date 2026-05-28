package recordingupload

import (
	"log"
	"os"
	"path/filepath"
	"sort"
	"syscall"
	"time"
)

func (r *Runner) enforceRetention() {
	tasks := make([]*Task, 0, len(r.manifest.Tasks))
	var cacheBytes int64
	for _, task := range r.manifest.Tasks {
		if info, err := os.Stat(task.FilePath); err == nil {
			task.FileSize = info.Size()
			cacheBytes += info.Size()
			tasks = append(tasks, task)
		}
	}
	sort.Slice(tasks, func(i, j int) bool {
		return tasks[i].CreatedAt.Before(tasks[j].CreatedAt)
	})
	needSpace := cacheBytes > r.cfg.LocalCacheMaxBytes || freeBytes(r.cfg.RecordingDirectory) < r.cfg.LocalMinFreeBytes
	for _, task := range tasks {
		if task.Status != "READY" {
			continue
		}
		if !needSpace && time.Since(task.UpdatedAt) < r.cfg.LocalRetentionAfterReady {
			continue
		}
		if err := os.Remove(task.FilePath); err != nil && !os.IsNotExist(err) {
			log.Println("recording local retention remove", task.FilePath, err)
			continue
		}
		task.Status = "LOCAL_DELETED"
		task.UpdatedAt = time.Now()
		_ = r.manifest.update(task)
		cacheBytes -= task.FileSize
		needSpace = cacheBytes > r.cfg.LocalCacheMaxBytes || freeBytes(r.cfg.RecordingDirectory) < r.cfg.LocalMinFreeBytes
		if !needSpace {
			return
		}
	}
}

func freeBytes(path string) int64 {
	var stat syscall.Statfs_t
	target := path
	if _, err := os.Stat(target); err != nil {
		target = filepath.Dir(path)
	}
	if err := syscall.Statfs(target, &stat); err != nil {
		return 0
	}
	return int64(stat.Bavail) * int64(stat.Bsize)
}
