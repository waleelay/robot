<template>
  <div style="display: none;"></div>
</template>

<script>
import { executeAlarm } from '../../../../../api/media'

export default {
  name: 'WarningExecuteError',
  data() {
    return {
      loading: false,
      alarmId: ''
    }
  },
  methods: {
    async open(alarmId) {
      if (this.loading) return
      this.loading = false
      this.alarmId = alarmId
      try {
        await this.$secondaryConfirm({
          title: '\u8bef\u62a5',
          message: '\u662f\u5426\u786e\u8ba4\u4e3a\u8bef\u62a5',
          confirmText: '\u786e\u8ba4',
          cancelText: '\u53d6\u6d88',
          onConfirm: () => this.executeWarning()
        })
      } catch (error) {
        // user cancel
      }
    },
    async executeWarning() {
      if (this.loading === true) {
        return false
      }
      this.loading = true
      try {
        await executeAlarm({ alarmId: this.alarmId, disposalStatus: 'FALSE_ALARM' })
        this.$message.success('\u6210\u529f\u5207\u6362\u544a\u8b66\u72b6\u6001')
        this.$emit('close')
      } catch (error) {
        throw error
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
