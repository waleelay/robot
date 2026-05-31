package intercom

import (
	"context"
	"encoding/binary"
	"errors"
	"io"
	"log"
	"os"
	"os/exec"
	"strings"
	"sync"

	media "github.com/livekit/media-sdk"
	"github.com/livekit/protocol/livekit"
	"github.com/livekit/protocol/logger"
	lksdk "github.com/livekit/server-sdk-go/v2"
	lkmedia "github.com/livekit/server-sdk-go/v2/pkg/media"
	"github.com/pion/webrtc/v4"

	"robot-media-client/internal/config"
	"robot-media-client/internal/model"
)

const (
	audioSampleRate = 48000
	audioChannels   = 1
	pcmFrameSamples = 960
)

// Manager owns the LiveKit and device-audio lifecycle for one VideoSession Room.
type Manager interface {
	Start(ctx context.Context, command model.IntercomStartCommand) (string, string, error)
	Stop(sessionID string) error
	StopAll() error
}

type SDKManager struct {
	cfg      *config.Config
	sessions map[string]*session
	mu       sync.Mutex
}

// session 保存一次对讲所需的所有资源。
// close 必须同时停止 LiveKit Room、GStreamer 采集/播放进程和远端 PCM track。
type session struct {
	cancel        context.CancelFunc
	room          *lksdk.Room
	publishTrack  *lkmedia.PCMLocalTrack
	capture       *exec.Cmd
	playback      *exec.Cmd
	playbackInput *pcmPlaybackWriter
	remoteTracks  []*lkmedia.PCMRemoteTrack
	mu            sync.Mutex
}

func NewSDKManager(cfg config.Config) *SDKManager {
	return &SDKManager{cfg: &cfg, sessions: make(map[string]*session)}
}

func (m *SDKManager) Start(parent context.Context, command model.IntercomStartCommand) (string, string, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	// 同一 session 重复 start 时先清理旧音频桥，避免麦克风/扬声器设备被重复打开。
	_ = m.stopLocked(command.SessionID)

	ctx, cancel := context.WithCancel(parent)
	active := &session{cancel: cancel}
	// 先启动播放管道，是因为 LiveKit 回调订阅到操作员音频后会立即向 stdin 写 PCM。
	if err := m.startPlayback(ctx, active); err != nil {
		cancel()
		return "", "", err
	}

	callback := &lksdk.RoomCallback{ParticipantCallback: lksdk.ParticipantCallback{
		OnTrackSubscribed: func(track *webrtc.TrackRemote, publication *lksdk.RemoteTrackPublication, _ *lksdk.RemoteParticipant) {
			if track.Kind() != webrtc.RTPCodecTypeAudio || publication.Name() != "audio.operator.mic" {
				return
			}
			// 操作员麦克风 track 会被转成 48k 单声道 PCM，写入 GStreamer playback stdin。
			remoteTrack, err := lkmedia.NewPCMRemoteTrack(
				track,
				active.playbackInput,
				lkmedia.WithTargetSampleRate(audioSampleRate),
				lkmedia.WithTargetChannels(audioChannels))
			if err != nil {
				log.Println("intercom subscribe audio failed", err)
				return
			}
			active.mu.Lock()
			active.remoteTracks = append(active.remoteTracks, remoteTrack)
			active.mu.Unlock()
			log.Println("intercom subscribed operator audio", command.SessionID, publication.SID())
		},
	}}
	room, err := lksdk.ConnectToRoomWithToken(command.LiveKitURL, command.RobotToken, callback)
	if err != nil {
		active.close()
		return "", "", err
	}
	active.room = room

	// 机器人麦克风发布为固定名称 audio.robot.mic，后端会把 publication SID 回写到会话。
	publishTrack, err := lkmedia.NewPCMLocalTrack(audioSampleRate, audioChannels, logger.GetLogger())
	if err != nil {
		active.close()
		return "", "", err
	}
	active.publishTrack = publishTrack
	publication, err := room.LocalParticipant.PublishTrack(publishTrack, &lksdk.TrackPublicationOptions{
		Name:   "audio.robot.mic",
		Source: livekit.TrackSource_MICROPHONE,
	})
	if err != nil {
		active.close()
		return "", "", err
	}
	if err := m.startCapture(ctx, active); err != nil {
		active.close()
		return "", "", err
	}
	m.sessions[command.SessionID] = active
	log.Println("intercom connected", command.SessionID, command.RoomName)
	return publication.SID(), "audio.robot.mic", nil
}

func (m *SDKManager) Stop(sessionID string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.stopLocked(sessionID)
}

func (m *SDKManager) StopAll() error {
	m.mu.Lock()
	defer m.mu.Unlock()
	for sessionID := range m.sessions {
		_ = m.stopLocked(sessionID)
	}
	return nil
}

func (m *SDKManager) stopLocked(sessionID string) error {
	active := m.sessions[sessionID]
	if active == nil {
		return nil
	}
	delete(m.sessions, sessionID)
	active.close()
	return nil
}

func (m *SDKManager) startCapture(ctx context.Context, active *session) error {
	args := strings.Fields(m.cfg.AudioCapturePipeline)
	if len(args) == 0 {
		return errors.New("AUDIO_CAPTURE_PIPELINE is empty")
	}
	// capture pipeline 输出 raw PCM 到 stdout，copyCapturePCM 再写入 LiveKit PCMLocalTrack。
	cmd := exec.CommandContext(ctx, m.cfg.GSTLaunchPath, append([]string{"-q"}, args...)...)
	output, err := cmd.StdoutPipe()
	if err != nil {
		return err
	}
	cmd.Stderr = os.Stderr
	if err := cmd.Start(); err != nil {
		return err
	}
	active.capture = cmd
	go waitForPipeline(ctx, "capture", cmd)
	go copyCapturePCM(output, active.publishTrack)
	return nil
}

func (m *SDKManager) startPlayback(ctx context.Context, active *session) error {
	args := strings.Fields(m.cfg.AudioPlaybackPipeline)
	if len(args) == 0 {
		return errors.New("AUDIO_PLAYBACK_PIPELINE is empty")
	}
	// playback pipeline 从 stdin 读取 raw PCM，并播放到机器人扬声器。
	cmd := exec.CommandContext(ctx, m.cfg.GSTLaunchPath, append([]string{"-q"}, args...)...)
	input, err := cmd.StdinPipe()
	if err != nil {
		return err
	}
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	if err := cmd.Start(); err != nil {
		return err
	}
	active.playback = cmd
	active.playbackInput = &pcmPlaybackWriter{output: input}
	go waitForPipeline(ctx, "playback", cmd)
	return nil
}

func waitForPipeline(ctx context.Context, direction string, cmd *exec.Cmd) {
	if err := cmd.Wait(); err != nil && ctx.Err() == nil {
		log.Println("intercom "+direction+" pipeline stopped", err)
	}
}

func copyCapturePCM(input io.Reader, track *lkmedia.PCMLocalTrack) {
	// LiveKit media SDK 以 20ms PCM frame 写入：48000Hz * 20ms = 960 samples。
	frame := make([]byte, pcmFrameSamples*audioChannels*2)
	for {
		if _, err := io.ReadFull(input, frame); err != nil {
			if !errors.Is(err, io.EOF) && !errors.Is(err, io.ErrUnexpectedEOF) {
				log.Println("intercom capture stopped", err)
			}
			return
		}
		sample := make(media.PCM16Sample, len(frame)/2)
		for i := range sample {
			sample[i] = int16(binary.LittleEndian.Uint16(frame[i*2:]))
		}
		if err := track.WriteSample(sample); err != nil {
			return
		}
	}
}

func (s *session) close() {
	// 取消 context 会终止 exec.CommandContext 启动的 GStreamer 进程。
	s.cancel()
	s.mu.Lock()
	for _, track := range s.remoteTracks {
		track.Close()
	}
	s.remoteTracks = nil
	s.mu.Unlock()
	if s.publishTrack != nil {
		s.publishTrack.ClearQueue()
		s.publishTrack.Close()
	}
	if s.playbackInput != nil {
		_ = s.playbackInput.Close()
	}
	if s.room != nil {
		s.room.Disconnect()
	}
}

type pcmPlaybackWriter struct {
	output io.WriteCloser
	closed bool
	mu     sync.Mutex
}

func (w *pcmPlaybackWriter) WriteSample(sample media.PCM16Sample) error {
	w.mu.Lock()
	defer w.mu.Unlock()
	if w.closed {
		return errors.New("playback writer is closed")
	}
	// GStreamer playback pipeline 期望 little-endian 16-bit PCM。
	buffer := make([]byte, len(sample)*2)
	for i, value := range sample {
		binary.LittleEndian.PutUint16(buffer[i*2:], uint16(value))
	}
	_, err := w.output.Write(buffer)
	return err
}

func (w *pcmPlaybackWriter) Close() error {
	w.mu.Lock()
	defer w.mu.Unlock()
	if w.closed {
		return nil
	}
	w.closed = true
	return w.output.Close()
}
