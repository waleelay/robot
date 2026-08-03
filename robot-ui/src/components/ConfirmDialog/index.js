import Vue from 'vue'
import PrimaryConfirm from './PrimaryConfirm.vue'
import SecondaryConfirm from './SecondaryConfirm.vue'

function createConfirmApi(Component) {
  return function confirm(options = {}) {
    const instance = new (Vue.extend(Component))({
      el: document.createElement('div'),
      propsData: {
        draggable: options.draggable != null ? options.draggable : true
      }
    })
    document.body.appendChild(instance.$el)
    const destroy = () => {
      instance.$nextTick(() => {
        instance.$destroy()
        if (instance.$el && instance.$el.parentNode) {
          instance.$el.parentNode.removeChild(instance.$el)
        }
      })
    }
    return instance.open(options).then((result) => {
      destroy()
      return result
    }).catch((error) => {
      destroy()
      return Promise.reject(error)
    })
  }
}

export { PrimaryConfirm, SecondaryConfirm }

export default {
  install(VueCtor) {
    VueCtor.component('PrimaryConfirm', PrimaryConfirm)
    VueCtor.component('SecondaryConfirm', SecondaryConfirm)
    VueCtor.prototype.$primaryConfirm = createConfirmApi(PrimaryConfirm)
    VueCtor.prototype.$secondaryConfirm = createConfirmApi(SecondaryConfirm)
  }
}
