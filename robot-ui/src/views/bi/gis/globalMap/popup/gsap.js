import gsap from "gsap";
export default {
  data() {
    return {
      visible: false,
      position: {
        left: 0,
        top: 0
      }
    }
  },
  computed: {
    positionStyle() {
      return {
        left: this.position.left + 'px',
        top: this.position.top + 'px'
      }
    }
  },
  mounted() {
    this.currentEl = this.$refs.containerRef;
    this.updateCubeSize();
    // 将方块初始隐藏位置设置到右上角（避免首次显示时闪烁）
    this.setHiddenPositionToTarget();
    
    // 绑定全局事件
    // window.addEventListener('click', this.handleGlobalClick);
    window.addEventListener('resize', this.onResize);
    window.addEventListener('orientationchange', this.onOrientationChange);
    
    // 禁用右键和拖拽
    document.body.addEventListener('contextmenu', (e) => e.preventDefault());
    document.addEventListener('dragstart', (e) => e.preventDefault());
    
    console.log('✅ 动画已就绪：点击任意位置，方块将从点击点平滑飞向右上角');
},
  methods: {
    // 目标尺寸
    updateCubeSize() {
      if (this.currentEl) {
        this.cubeWidth = this.currentEl.offsetWidth;
        this.cubeHeight = this.currentEl.offsetHeight;
      } else {
        this.cubeWidth = 100;
        this.cubeHeight = 100;
      }
    },
    // 获取右上角目标位置（距离边缘110px）
    getTargetPosition() {
      return {
        left: Math.max(0, window.innerWidth - this.cubeWidth - 110),
        top: 110
      };
    },
    // 将隐藏状态下的方块放到右上角（无动画）
    setHiddenPositionToTarget() {
        if (!this.currentEl) return;
        const target = this.getTargetPosition();
        gsap.set(this.currentEl, {
          left: target.left,
          top: target.top,
          scale: 1,
          opacity: 0,
          'backdrop-filter': 'unset'
        });
        this.position.left = target.left;
        this.position.top = target.top;
    },
    
    // 核心：从鼠标点击处开始，平滑移动到右上角
    flyFromPoint(clientX, clientY) {
      // 边界保护
      if (clientX === undefined || clientY === undefined) return;
      
      // 1. 更新方块尺寸（确保响应式变化）
      this.updateCubeSize();
      
      // 2. 计算起始位置：点击点右侧 80px，垂直方向与点击点对齐（中心对齐）
      // let startLeft = clientX - this.cubeWidth / 2;
      // let startTop = clientY - this.cubeHeight / 2;
      const startCenterX = clientX + 80;
      const startCenterY = clientY;
      // 起始左上角坐标 = 中心点 - 半宽/半高
      let startLeft = startCenterX - this.cubeWidth / 2;
      let startTop = startCenterY - this.cubeHeight / 2;
      // 边界修正：确保起始位置完全在视口内（避免方块部分超出边缘）
      const maxLeft = window.innerWidth - this.cubeWidth;
      const maxTop = window.innerHeight - this.cubeHeight;
      startLeft = Math.min(Math.max(0, startLeft), maxLeft);
      startTop = Math.min(Math.max(0, startTop), maxTop);
      
      // 3. 获取目标位置
      const targetPos = this.getTargetPosition();
      
      // 4. 让方块可见（GSAP会控制透明度，但需要让元素显示出来）
      if (!this.visible) {
        this.visible = true;
      }
      
      // 杀掉任何正在进行的动画，避免冲突
      gsap.killTweensOf(this.currentEl);
      
      // 5. 瞬移到起始位置（鼠标点击处）
      gsap.set(this.currentEl, {
        left: startLeft,
        top: startTop,
        scale: 0.2,
        opacity: 0,
        'backdrop-filter': 'unset',
        immediateRender: true
      });
      // 同步 Vue 数据
      this.position.left = startLeft;
      this.position.top = startTop;
      
      // 6. 强制浏览器重绘，确保瞬移已经生效（这一点对于连续点击很重要）
      void this.currentEl.offsetHeight;
      
      // 7. 执行组合动画：移动到右上角 + scale 从0.2变到1（由小变大） + opacity 从0变到1（淡入）
      //    使用弹性缓动，让缩放和移动都很生动，透明度线性渐变让出现更柔和
      gsap.to(this.currentEl, {
        left: targetPos.left,
        top: targetPos.top,
        scale: 1,               // 恢复到原始大小
        opacity: 1,             // 完全不透明
        'backdrop-filter': 'blur(15px)',
        duration: 0.65,          // 动画时长0.65秒，让渐变更从容
        ease: "back.out(0.6)",   // 带有轻微回弹的缓动，移动更生动
        onUpdate: () => {
          // 实时同步 Vue 数据（便于 resize 等场景）
          const currentLeft = parseFloat(this.currentEl.style.left);
          const currentTop = parseFloat(this.currentEl.style.top);
          if (!isNaN(currentLeft)) this.position.left = currentLeft;
          if (!isNaN(currentTop)) this.position.top = currentTop;
        },
        onComplete: () => {
          // 动画完成，精确归位到右上角（防止浮点误差）
          const finalPos = this.getTargetPosition();
          gsap.set(this.currentEl, {
            left: finalPos.left,
            top: finalPos.top,
            scale: 1,
            opacity: 1,
            'backdrop-filter': 'blur(15px)'
          });
          this.position.left = finalPos.left;
          this.position.top = finalPos.top;
          // 确保可见
          if (!this.visible) this.visible = true;
        }
      });
    },
    
    // 全局点击处理器（鼠标）
    handleGlobalClick(event, visible) {
      if (!visible) {
        this.setSelectedRobotId('')
        this.setHiddenPositionToTarget();
        return
      }
      let clientX = event.clientX;
      let clientY = event.clientY;
      if (clientX === undefined || clientY === undefined) return;
      // 边界限幅
      clientX = Math.min(Math.max(0, clientX), window.innerWidth);
      clientY = Math.min(Math.max(0, clientY), window.innerHeight);
      this.flyFromPoint(clientX, clientY);
    },
    // 窗口大小改变时，如果方块可见则重新定位（无动画）
    onResize() {
      this.updateCubeSize();
      if (this.visible && this.currentEl) {
        gsap.killTweensOf(this.currentEl);
        const newTarget = this.getTargetPosition();
        gsap.set(this.currentEl, {
          left: newTarget.left,
          top: newTarget.top,
          scale: 1,
          opacity: 1,
          'backdrop-filter': 'blur(15px)'
        });
        this.position.left = newTarget.left;
        this.position.top = newTarget.top;
      } else if (!this.visible) {
        // 隐藏状态也更新基准位置
        const hiddenTarget = this.getTargetPosition();
        if (this.currentEl) {
          gsap.set(this.currentEl, {
            left: hiddenTarget.left,
            top: hiddenTarget.top,
            scale: 1,
            opacity: 0,
            'backdrop-filter': 'unset'
          });
        }
        this.position.left = hiddenTarget.left;
        this.position.top = hiddenTarget.top;
      }
    },
    onOrientationChange() {
      setTimeout(() => {
        this.updateCubeSize();
        this.onResize();
      }, 60);
    }
  },
  beforeDestroy() {
    // window.removeEventListener('click', this.handleGlobalClick);
    window.removeEventListener('resize', this.onResize);
    window.removeEventListener('orientationchange', this.onOrientationChange);
    if (this.currentEl) gsap.killTweensOf(this.currentEl);
  },
}