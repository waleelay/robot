package recordingupload

import (
	"context"
	"fmt"
	"io"
	"os"
	"sync"
)

func uploadMissingParts(ctx context.Context, client *Client, file *os.File, upload uploadResponse, concurrency int) error {
	uploaded := make(map[int]bool, len(upload.UploadedParts))
	for _, part := range upload.UploadedParts {
		uploaded[part.PartNumber] = true
	}
	jobs := make(chan int)
	errors := make(chan error, 1)
	workerCount := concurrency
	if workerCount < 1 {
		workerCount = 1
	}
	var wait sync.WaitGroup
	for index := 0; index < workerCount; index++ {
		wait.Add(1)
		go func() {
			defer wait.Done()
			for partNumber := range jobs {
				url, err := client.partURL(ctx, upload.UploadID, partNumber)
				if err == nil {
					offset := int64(partNumber-1) * upload.PartSize
					size := upload.PartSize
					if remaining := fileSize(file) - offset; remaining < size {
						size = remaining
					}
					err = client.putPart(ctx, url, ioSection(file, offset, size), size)
				}
				if err != nil {
					select {
					case errors <- fmt.Errorf("part %d: %w", partNumber, err):
					default:
					}
					return
				}
			}
		}()
	}
	for partNumber := 1; partNumber <= upload.PartCount; partNumber++ {
		if uploaded[partNumber] {
			continue
		}
		select {
		case jobs <- partNumber:
		case err := <-errors:
			close(jobs)
			wait.Wait()
			return err
		case <-ctx.Done():
			close(jobs)
			wait.Wait()
			return ctx.Err()
		}
	}
	close(jobs)
	wait.Wait()
	select {
	case err := <-errors:
		return err
	default:
		return nil
	}
}

func fileSize(file *os.File) int64 {
	info, _ := file.Stat()
	return info.Size()
}

func ioSection(file *os.File, offset, size int64) *io.SectionReader {
	return io.NewSectionReader(file, offset, size)
}
