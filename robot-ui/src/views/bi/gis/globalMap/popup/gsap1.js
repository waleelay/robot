import gsap from "gsap";

export default {
  data() {
    return {
      visible: false,
      position: {
        left: 0,
        top: 0
      },
      // 缓存尺寸，避免频繁读取 DOM
      cubeWidth: 100,
      cubeHeight: 100,
    };
  },
  computed: {
    positionStyle() {
      return {
        left: this.position.left + 'px',
        top: this.position.top + 'px'
      };
    }
  },
  mounted() {
    this.currentEl = this.$refs.containerRef;
    this.updateCubeSize();
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
    // 更新缓存的元素尺寸
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
      const left = Math.max(0, window.innerWidth - this.cubeWidth - 110);
      const top = 110;
      return { left, top };
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
    flyFromPoint(clientX, clientY, location) {
      if (clientX === undefined || clientY === undefined) return;

      // 1. 更新方块尺寸（确保响应式变化）
      this.updateCubeSize();

      // 2. 计算起始位置：点击点右侧 80px，垂直方向与点击点对齐（中心对齐）
      const startCenterX = clientX + 80;
      const startCenterY = clientY;
      let startLeft = startCenterX - this.cubeWidth / 2;
      let startTop = startCenterY - this.cubeHeight / 2;

      // 边界修正：确保起始位置完全在视口内（避免方块部分超出边缘）
      const maxLeft = window.innerWidth - this.cubeWidth;
      const maxTop = window.innerHeight - this.cubeHeight;

      if (this.$route.name === 'biIndex') {
        startLeft = location.x;
        startTop = location.y;
      } else {
        startLeft = Math.min(Math.max(0, startLeft), maxLeft);
        startTop = Math.min(Math.max(0, startTop), maxTop);
      }

      // 3. 获取目标位置
      const targetPos = this.$route.name === 'biIndex'
        ? {
            left: location.translateX,
            top: location.translateY,
          }
        : this.getTargetPosition();

      // 4. 让方块可见
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
      this.position.left = startLeft;
      this.position.top = startTop;

      // 6. 强制浏览器重绘，确保瞬移已经生效
      void this.currentEl.offsetHeight;

      // 7. 执行组合动画
      gsap.to(this.currentEl, {
        left: targetPos.left,
        top: targetPos.top,
        scale: 1,
        opacity: 1,
        'backdrop-filter': 'blur(15px)',
        duration: 0.65,
        ease: "back.out(0.6)",
        onUpdate: () => {
          const currentLeft = parseFloat(this.currentEl.style.left);
          const currentTop = parseFloat(this.currentEl.style.top);
          if (!isNaN(currentLeft)) this.position.left = currentLeft;
          if (!isNaN(currentTop)) this.position.top = currentTop;
        },
        onComplete: () => {
          // 动画完成，精确归位到右上角（防止浮点误差）
          const finalPos = this.$route.name === 'biIndex'
            ? {
                left: location.translateX,
                top: location.translateY,
              }
            : this.getTargetPosition();
          gsap.set(this.currentEl, {
            left: finalPos.left,
            top: finalPos.top,
            scale: 1,
            opacity: 1,
            'backdrop-filter': 'blur(15px)'
          });
          this.position.left = finalPos.left;
          this.position.top = finalPos.top;
          if (!this.visible) this.visible = true;
        }
      });
    },

    // 全局点击处理器（鼠标）
    handleGlobalClick(event, visible) {
      if (!visible) {
        this.setSelectedRobotId('');
        this.setHiddenPositionToTarget();
        return;
      }
      let clientX = event.clientX;
      let clientY = event.clientY;
      if (clientX === undefined || clientY === undefined) return;
      clientX = Math.min(Math.max(0, clientX), window.innerWidth);
      clientY = Math.min(Math.max(0, clientY), window.innerHeight);

      const eleParent = event.target.closest('.custom-point');
      const transform = window.getComputedStyle(eleParent).transform;
      let location = {};
      if (transform !== 'none') {
        const parts = transform.match(/matrix\((.+)\)/);
        if (parts) {
          const nums = parts[1].split(',').map(parseFloat);
          const width = eleParent.offsetWidth;
          const height = eleParent.offsetHeight;
          location = {
            x: clientX,
            y: clientY + height / 2 - this.cubeHeight,
            translateX: nums[4] + width / 2,
            translateY: nums[5] - height / 2 - this.cubeHeight + 37,
          };
        }
      }
      this.flyFromPoint(clientX, clientY, location);
    },

    // ========== 增强的自适应处理 ==========
    onResize() {
      // 更新尺寸缓存
      this.updateCubeSize();

      if (!this.currentEl) return;

      // 判断当前是否有正在运行的动画
      const isTweening = gsap.isTweening(this.currentEl);

      // 获取新的目标位置（根据最新窗口尺寸）
      const newTarget = this.getTargetPosition();

      if (this.visible) {
        // 如果方块可见，中断当前动画（如果有）并直接定位到新目标
        if (isTweening) {
          gsap.killTweensOf(this.currentEl);
        }
        gsap.set(this.currentEl, {
          left: newTarget.left,
          top: newTarget.top,
          scale: 1,
          opacity: 1,
          'backdrop-filter': 'blur(15px)'
        });
        this.position.left = newTarget.left;
        this.position.top = newTarget.top;
      } else {
        // 隐藏状态也更新基准位置（透明度保持0）
        gsap.set(this.currentEl, {
          left: newTarget.left,
          top: newTarget.top,
          scale: 1,
          opacity: 0,
          'backdrop-filter': 'unset'
        });
        this.position.left = newTarget.left;
        this.position.top = newTarget.top;
      }
    },

    onOrientationChange() {
      // 延迟执行，确保旋转后尺寸稳定
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
    if (this.currentEl) {
      gsap.killTweensOf(this.currentEl);
    }
  },
};