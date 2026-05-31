package recordingupload

import (
	"context"
	"fmt"
	"io"
	"os"
	"sync"
)

func uploadMissingParts(ctx context.Context, client *Client, file *os.File, upload uploadResponse, concurrency, urlBatchSize int) error {
	uploaded := make(map[int]bool, len(upload.UploadedParts))
	for _, part := range upload.UploadedParts {
		uploaded[part.PartNumber] = true
	}
	if urlBatchSize < 1 {
		urlBatchSize = 1
	}
	jobs := make(chan uploadPartJob)
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
			for job := range jobs {
				offset := int64(job.partNumber-1) * upload.PartSize
				size := upload.PartSize
				if remaining := fileSize(file) - offset; remaining < size {
					size = remaining
				}
				err := client.putPart(ctx, job.url, ioSection(file, offset, size), size)
				if err != nil {
					select {
					case errors <- fmt.Errorf("part %d: %w", job.partNumber, err):
					default:
					}
					return
				}
			}
		}()
	}
	partNumbers := make([]int, 0, urlBatchSize)
	flush := func() error {
		if len(partNumbers) == 0 {
			return nil
		}
		urls, err := client.partURLs(ctx, upload.UploadID, partNumbers)
		if err != nil {
			return err
		}
		for _, partNumber := range partNumbers {
			select {
			case jobs <- uploadPartJob{partNumber: partNumber, url: urls[partNumber]}:
			case err := <-errors:
				return err
			case <-ctx.Done():
				return ctx.Err()
			}
		}
		partNumbers = partNumbers[:0]
		return nil
	}
	for partNumber := 1; partNumber <= upload.PartCount; partNumber++ {
		if uploaded[partNumber] {
			continue
		}
		partNumbers = append(partNumbers, partNumber)
		if len(partNumbers) < urlBatchSize {
			continue
		}
		if err := flush(); err != nil {
			close(jobs)
			wait.Wait()
			return err
		}
	}
	if err := flush(); err != nil {
		close(jobs)
		wait.Wait()
		return err
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

type uploadPartJob struct {
	partNumber int
	url        string
}

func fileSize(file *os.File) int64 {
	info, _ := file.Stat()
	return info.Size()
}

func ioSection(file *os.File, offset, size int64) *io.SectionReader {
	return io.NewSectionReader(file, offset, size)
}
