/**
 * ConfirmDialog drag helpers.
 * Prefer custom title bar (.top), fallback to .el-dialog__header.
 */
export function getConfirmDragHandle(wrapperEl) {
  if (!wrapperEl) return null
  return (
    wrapperEl.querySelector('.primary-confirm-container .box .top') ||
    wrapperEl.querySelector('.custom-modal-container .box .top') ||
    wrapperEl.querySelector('.el-dialog__header')
  )
}

export function bindConfirmDrag(wrapperEl) {
  const handleEl = getConfirmDragHandle(wrapperEl)
  const dragDom = wrapperEl && wrapperEl.querySelector('.el-dialog')
  if (!handleEl || !dragDom) return () => {}

  handleEl.style.cursor = 'move'
  const sty = dragDom.currentStyle || window.getComputedStyle(dragDom, null)

  const onMouseDown = (e) => {
    if (e.target.closest && e.target.closest('.close, .el-dialog__headerbtn, button, .el-button')) {
      return
    }

    const disX = e.clientX - handleEl.offsetLeft
    const disY = e.clientY - handleEl.offsetTop
    const screenWidth = document.body.clientWidth
    const screenHeight = document.documentElement.clientHeight
    const dragDomWidth = dragDom.offsetWidth
    const dragDomHeight = dragDom.offsetHeight
    const minDragDomLeft = dragDom.offsetLeft
    const maxDragDomLeft = screenWidth - dragDom.offsetLeft - dragDomWidth
    const minDragDomTop = dragDom.offsetTop
    const maxDragDomTop = screenHeight - dragDom.offsetTop - dragDomHeight

    let styL
    let styT
    if ((sty.left || '').includes('%')) {
      styL = +document.body.clientWidth * (+sty.left.replace(/%/g, '') / 100)
      styT = +document.body.clientHeight * (+(sty.top || '0').replace(/%/g, '') / 100)
    } else {
      styL = +(sty.left || '0').replace(/px/g, '') || 0
      styT = +(sty.top || '0').replace(/px/g, '') || 0
    }

    if (!dragDom.style.position || dragDom.style.position === 'static') {
      dragDom.style.position = 'relative'
    }

    document.onmousemove = function(ev) {
      let l = ev.clientX - disX
      let t = ev.clientY - disY

      if (-l > minDragDomLeft) {
        l = -minDragDomLeft
      } else if (l > maxDragDomLeft) {
        l = maxDragDomLeft
      }
      if (-t > minDragDomTop) {
        t = -minDragDomTop
      } else if (t > maxDragDomTop) {
        t = maxDragDomTop
      }

      dragDom.style.left = (l + styL) + 'px'
      dragDom.style.top = (t + styT) + 'px'
    }

    document.onmouseup = function() {
      document.onmousemove = null
      document.onmouseup = null
    }
  }

  handleEl.addEventListener('mousedown', onMouseDown)
  return () => {
    handleEl.removeEventListener('mousedown', onMouseDown)
    handleEl.style.cursor = ''
  }
}
