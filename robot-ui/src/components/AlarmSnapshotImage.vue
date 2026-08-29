<template>
  <div class="w100 h100 flx-center">
    <img v-if="src" :src="src" :alt="alt" class="w100 h100" style="object-fit: cover;">
    <slot v-else />
  </div>
</template>

<script>
import { alarmSnapshotFileId } from '@/utils/alarm-snapshot';
import { getCachedFileObjectUrl } from '@/utils/file-object-url-cache';

export default {
  name: 'AlarmSnapshotImage',
  props: {
    item: {
      type: Object,
      default: () => ({})
    },
    alt: {
      type: String,
      default: ''
    }
  },
  data() {
    return {
      src: '',
      observer: null,
      loadSeq: 0
    };
  },
  computed: {
    fileId() {
      return alarmSnapshotFileId(this.item?.snapshotUrl, 'visible', this.item);
    }
  },
  watch: {
    fileId: {
      handler() {
        this.src = '';
        this.observe();
      },
      immediate: true
    }
  },
  beforeDestroy() {
    this.disconnect();
    this.loadSeq += 1;
  },
  methods: {
    observe() {
      this.disconnect();
      if (!this.fileId) return;
      this.$nextTick(() => {
        if (!this.fileId) return;
        if (typeof IntersectionObserver === 'undefined') {
          this.load();
          return;
        }
        this.observer = new IntersectionObserver(entries => {
          if (!entries.some(entry => entry.isIntersecting)) return;
          this.disconnect();
          this.load();
        }, { rootMargin: '100px 0px' });
        this.observer.observe(this.$el);
      });
    },
    disconnect() {
      if (this.observer) this.observer.disconnect();
      this.observer = null;
    },
    async load() {
      const fileId = this.fileId;
      const seq = ++this.loadSeq;
      try {
        const src = await getCachedFileObjectUrl(fileId);
        if (seq === this.loadSeq && fileId === this.fileId) this.src = src;
      } catch (error) {
        // 无权限或文件不存在时保持占位，不影响告警列表和详情操作。
      }
    }
  }
};
</script>
