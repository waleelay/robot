import assert from 'node:assert/strict'
import test from 'node:test'
import {
  artifactProgressFor,
  artifactProgressLabel,
  artifactProgressPercent,
  indexArtifactProgress
} from '../src/views/bi/patrol/business/record/artifact-upload-progress.js'

test('大屏按执行记录和 fileId 关联上传进度', () => {
  const indexed = indexArtifactProgress({
    instances: [{
      workflowInstanceId: '10',
      summary: { status: 'UPLOADING', uploadPercent: 25 },
      artifacts: [{ fileId: 'file-1', phase: 'UPLOADING' }]
    }]
  })
  const instance = indexed['10']

  assert.equal(artifactProgressFor(instance, { fileId: 'file-1' }).phase, 'UPLOADING')
  assert.equal(artifactProgressPercent(instance.summary), 25)
  assert.equal(artifactProgressLabel(instance.summary.status), '上传中')
})

test('大屏以 mediaRef 匹配尚未绑定 fileId 的任务产物', () => {
  const instance = { artifacts: [{ mediaRef: 'video-1', phase: 'PENDING_BIND' }] }
  assert.equal(artifactProgressFor(instance, { mediaRef: 'video-1' }).phase, 'PENDING_BIND')
})
