package recordingupload

import (
	"encoding/json"
	"os"
	"path/filepath"
	"sync"
	"time"
)

type Task struct {
	SourceFileID string    `json:"sourceFileId"`
	FilePath     string    `json:"filePath"`
	FileSize     int64     `json:"fileSize"`
	CreatedAt    time.Time `json:"createdAt"`
	FileID       string    `json:"fileId,omitempty"`
	UploadID     string    `json:"uploadId,omitempty"`
	Status       string    `json:"status"`
	Error        string    `json:"error,omitempty"`
	UpdatedAt    time.Time `json:"updatedAt"`
}

type Manifest struct {
	path  string
	Tasks map[string]*Task `json:"tasks"`
	mu    sync.Mutex
}

func LoadManifest(path string) *Manifest {
	manifest := &Manifest{path: path, Tasks: make(map[string]*Task)}
	data, err := os.ReadFile(path)
	if err == nil {
		_ = json.Unmarshal(data, manifest)
	}
	if manifest.Tasks == nil {
		manifest.Tasks = make(map[string]*Task)
	}
	return manifest
}

func (m *Manifest) update(task *Task) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.Tasks[task.SourceFileID] = task
	data, err := json.MarshalIndent(m, "", "  ")
	if err != nil {
		return err
	}
	if directory := filepath.Dir(m.path); directory != "." {
		if err := os.MkdirAll(directory, 0o755); err != nil {
			return err
		}
	}
	return os.WriteFile(m.path, data, 0o600)
}
