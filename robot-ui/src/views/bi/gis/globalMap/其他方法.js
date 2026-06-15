// 矩形框选
// 添加框选功能（以原生方案为例）
let drawing = false;
let startLatLng = null;
let selectionRect = null;

this.map.on('mousedown', (e) => {
    if (e.originalEvent.button !== 0) return;
    this.map.dragging.disable();
    drawing = true;
    startLatLng = e.latlng;
    if (selectionRect) this.map.removeLayer(selectionRect);
    selectionRect = L.rectangle([startLatLng, startLatLng], {
        color: '#00ff00',
        weight: 2,
        fillOpacity: 0.1,
        className: 'selection-box'
    }).addTo(this.map);
});

this.map.on('mousemove', (e) => {
    if (!drawing) return;
    selectionRect.setBounds(L.latLngBounds(startLatLng, e.latlng));
});

this.map.on('mouseup', () => {
    if (!drawing) return;
    drawing = false;
    this.map.dragging.enable();
    
    if (selectionRect) {
        const bounds = selectionRect.getBounds();
        // 筛选范围内的 markers
        this.pointMarkers.forEach(marker => {
            if (bounds.contains(marker.getLatLng())) {
                marker.bindPopup('被框选中').openPopup();
            }
        });
    }
});


// 套索工具
// 在需要时启用套索模式
const lasso = L.lasso(map, {
    polygon: { color: '#ff7800', weight: 2 }, // 绘制线条样式
    intersect: false  // false=完全包含才选中, true=相交即选中
});

// 监听套索完成事件
this.map.on('lasso.finished', (event) => {
    const selectedLayers = event.layers; // 直接获取被选中的图层数组
    console.log('选中的要素:', selectedLayers);
    
    // 高亮选中的要素
    selectedLayers.forEach(layer => {
        layer.bindPopup('通过套索选中').openPopup();
    });
    
    lasso.disable(); // 关闭套索模式，恢复普通交互
});

// 通过按钮激活套索模式
lasso.enable();