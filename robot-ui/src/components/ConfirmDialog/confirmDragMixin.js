import { bindConfirmDrag } from './confirmDrag'

export default {
  props: {
    // Global drag switch for ConfirmDialog; override via open({ draggable })
    draggable: {
      type: Boolean,
      default: true
    }
  },
  computed: {
    currentDraggable() {
      if (this.options && this.options.draggable != null) {
        return this.options.draggable
      }
      return this.draggable
    }
  },
  watch: {
    dialogVisible(visible) {
      if (visible) {
        this.$nextTick(() => this.setupConfirmDrag())
      } else {
        this.teardownConfirmDrag()
      }
    }
  },
  beforeDestroy() {
    this.teardownConfirmDrag()
  },
  methods: {
    setupConfirmDrag() {
      this.teardownConfirmDrag()
      if (!this.currentDraggable) return
      const wrapper = this.$el
      if (!wrapper) return
      this._unbindConfirmDrag = bindConfirmDrag(wrapper)
    },
    teardownConfirmDrag() {
      if (typeof this._unbindConfirmDrag === 'function') {
        this._unbindConfirmDrag()
      }
      this._unbindConfirmDrag = null
    }
  }
}
