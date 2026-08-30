package fixedcamera

import (
	"context"
	"errors"
	"hash/fnv"
	"sync"
)

const (
	commandWorkerCount = 4
	commandQueueSize   = 32
)

var (
	errCommandQueueFull = errors.New("固定摄像头命令队列已满")
	errDuplicateCommand = errors.New("固定摄像头命令已处理")
)

type commandPriority int

const (
	normalCommand commandPriority = iota
	priorityCommand
)

type commandJob struct {
	ctx       context.Context
	sessionID string
	run       func(context.Context)
}

type commandShard struct {
	normal   chan commandJob
	priority chan commandJob
}

// commandDispatcher 只解决 Gateway 内部命令有界并发：同一会话固定落入同一分片串行执行，
// stop/restart 使用优先队列；不引入持久化队列或第二套业务状态机。
type commandDispatcher struct {
	ctx     context.Context
	cancel  context.CancelFunc
	shards  []commandShard
	mu      sync.Mutex
	active  map[string]context.CancelFunc
	waiters sync.WaitGroup
}

func newCommandDispatcher(parent context.Context) *commandDispatcher {
	ctx, cancel := context.WithCancel(parent)
	dispatcher := &commandDispatcher{
		ctx:    ctx,
		cancel: cancel,
		shards: make([]commandShard, commandWorkerCount),
		active: make(map[string]context.CancelFunc),
	}
	for index := range dispatcher.shards {
		dispatcher.shards[index] = commandShard{
			normal:   make(chan commandJob, commandQueueSize),
			priority: make(chan commandJob, commandQueueSize),
		}
		dispatcher.waiters.Add(1)
		go dispatcher.run(&dispatcher.shards[index])
	}
	return dispatcher
}

func (d *commandDispatcher) submit(priority commandPriority, job commandJob) error {
	shard := &d.shards[commandShardIndex(job.sessionID)]
	target := shard.normal
	if priority == priorityCommand {
		target = shard.priority
	}
	select {
	case target <- job:
		if priority == priorityCommand {
			d.cancelSession(job.sessionID)
		}
		return nil
	default:
		return errCommandQueueFull
	}
}

func (d *commandDispatcher) close() {
	d.cancel()
	d.waiters.Wait()
}

func (d *commandDispatcher) cancelAll() {
	d.mu.Lock()
	defer d.mu.Unlock()
	for _, cancel := range d.active {
		cancel()
	}
}

func (d *commandDispatcher) cancelSession(sessionID string) {
	d.mu.Lock()
	cancel := d.active[sessionID]
	d.mu.Unlock()
	if cancel != nil {
		cancel()
	}
}

func (d *commandDispatcher) run(shard *commandShard) {
	defer d.waiters.Done()
	for {
		var job commandJob
		var ok bool
		select {
		case <-d.ctx.Done():
			return
		case job, ok = <-shard.priority:
			if !ok {
				return
			}
		default:
			select {
			case <-d.ctx.Done():
				return
			case job, ok = <-shard.priority:
				if !ok {
					return
				}
			case job, ok = <-shard.normal:
				if !ok {
					return
				}
			}
		}
		d.execute(job)
	}
}

func (d *commandDispatcher) execute(job commandJob) {
	if job.ctx.Err() != nil || d.ctx.Err() != nil {
		return
	}
	ctx, cancel := context.WithCancel(job.ctx)
	d.mu.Lock()
	d.active[job.sessionID] = cancel
	d.mu.Unlock()
	defer func() {
		cancel()
		d.mu.Lock()
		delete(d.active, job.sessionID)
		d.mu.Unlock()
	}()
	job.run(ctx)
}

func commandShardIndex(sessionID string) int {
	hash := fnv.New32a()
	_, _ = hash.Write([]byte(sessionID))
	return int(hash.Sum32() % commandWorkerCount)
}
