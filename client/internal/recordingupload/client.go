package recordingupload

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

type Client struct {
	baseURL string
	robotID string
	http    *http.Client
}

type createRequest struct {
	SourceFileID string `json:"sourceFileId"`
	DeviceID     string `json:"deviceId"`
	FileType     string `json:"fileType"`
	FileName     string `json:"fileName"`
	ContentType  string `json:"contentType"`
	FileSize     int64  `json:"fileSize"`
}

type uploadResponse struct {
	FileID        string     `json:"fileId"`
	UploadID      string     `json:"uploadId"`
	Status        string     `json:"status"`
	PartSize      int64      `json:"partSize"`
	PartCount     int        `json:"partCount"`
	UploadedParts []partInfo `json:"uploadedParts"`
}

type partInfo struct {
	PartNumber int `json:"partNumber"`
}

type partURLsResponse struct {
	Parts []struct {
		PartNumber int    `json:"partNumber"`
		UploadURL  string `json:"uploadUrl"`
	} `json:"parts"`
}

type statusResponse struct {
	Status    string `json:"status"`
	ErrorCode string `json:"errorCode"`
}

func NewClient(baseURL, robotID string) *Client {
	return &Client{
		baseURL: strings.TrimRight(baseURL, "/"),
		robotID: robotID,
		http:    &http.Client{Timeout: 30 * time.Second},
	}
}

func (c *Client) createOrResume(ctx context.Context, request createRequest) (uploadResponse, error) {
	var response uploadResponse
	err := c.json(ctx, http.MethodPost, "/api/media/files/multipart-uploads", request, &response)
	return response, err
}

func (c *Client) partURLs(ctx context.Context, uploadID string, partNumbers []int) (map[int]string, error) {
	var response partURLsResponse
	err := c.json(ctx, http.MethodPost, "/api/media/files/multipart-uploads/"+uploadID+"/part-urls",
		map[string]any{"partNumbers": partNumbers}, &response)
	if err != nil {
		return nil, err
	}
	urls := make(map[int]string, len(response.Parts))
	for _, part := range response.Parts {
		urls[part.PartNumber] = part.UploadURL
	}
	for _, partNumber := range partNumbers {
		if urls[partNumber] == "" {
			return nil, fmt.Errorf("part URL missing for part %d", partNumber)
		}
	}
	return urls, nil
}

func (c *Client) putPart(ctx context.Context, url string, reader io.Reader, size int64) error {
	request, err := http.NewRequestWithContext(ctx, http.MethodPut, url, reader)
	if err != nil {
		return err
	}
	request.ContentLength = size
	response, err := c.http.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return fmt.Errorf("part upload returned %s", response.Status)
	}
	return nil
}

func (c *Client) complete(ctx context.Context, uploadID string) (statusResponse, error) {
	var response statusResponse
	err := c.json(ctx, http.MethodPost, "/api/media/files/multipart-uploads/"+uploadID+"/complete", nil, &response)
	return response, err
}

func (c *Client) status(ctx context.Context, fileID string) (statusResponse, error) {
	var response statusResponse
	err := c.json(ctx, http.MethodGet, "/api/media/files/"+fileID+"/status", nil, &response)
	return response, err
}

func (c *Client) json(ctx context.Context, method, path string, body any, target any) error {
	var reader io.Reader
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return err
		}
		reader = bytes.NewReader(data)
	}
	request, err := http.NewRequestWithContext(ctx, method, c.baseURL+path, reader)
	if err != nil {
		return err
	}
	request.Header.Set("X-Robot-Id", c.robotID)
	if body != nil {
		request.Header.Set("Content-Type", "application/json")
	}
	response, err := c.http.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		message, _ := io.ReadAll(response.Body)
		return fmt.Errorf("%s: %s", response.Status, strings.TrimSpace(string(message)))
	}
	return json.NewDecoder(response.Body).Decode(target)
}
