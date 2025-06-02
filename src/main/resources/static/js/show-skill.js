// 技能图表模块 - 使用命名空间避免冲突
window.SkillChartModule = (function() {
    'use strict';
    
    var _skillEchartLoaded = false;
    var skillChart = null;
    var categoryData = [];
    var detailsData = {};
    var currentView = 'category';
    var selectedCategory = null;
    var totalRescuers = 0;
    
    function initSkillChart() {
        // 初始化图表实例
        skillChart = echarts.init(document.getElementById('skillChart'));
        
        // 获取DOM元素
        var chartTitle = document.getElementById('skill-chartTitle');
        var centerLogoContainer = document.getElementById('skill-centerLogoContainer');
        var totalRescuersSpan = document.getElementById('skill-totalRescuers');
        
        // 监听Logo点击事件（用于返回）
        centerLogoContainer.addEventListener('click', function() {
            if (currentView === 'detail') {
                showCategoryView();
            }
        });
        
        // 监听窗口大小变化
        window.addEventListener('resize', function() {
            if (skillChart) {
                skillChart.resize();
            }
        });
        
        // 加载数据
        fetchSkillDistribution();
        
        // 获取技能分布数据
        function fetchSkillDistribution() {
            fetch('/mapshow/skill-distribution')
                .then(response => response.json())
                .then(data => {
                    // 保存总人数
                    totalRescuers = data.totalRescuers;
                    totalRescuersSpan.textContent = totalRescuers;
                    
                    // 处理类别数据
                    categoryData = Object.entries(data.categoryCount).map(([name, value]) => ({
                        name: name,
                        value: value
                    }));
                    
                    // 保存详情数据
                    detailsData = data.categoryDetails;
                    
                    // 显示类别视图
                    showCategoryView();
                })
                .catch(error => {
                    console.error('Error fetching skill distribution:', error);
                });
        }
        
        // 显示类别视图
        function showCategoryView() {
            currentView = 'category';
            chartTitle.textContent = '搜救队员技能分布';
            
            // 移除返回模式的样式
            centerLogoContainer.classList.remove('back-mode');
            
            // 找出最大值用于归一化
            const maxValue = Math.max(...categoryData.map(item => item.value));
            
            // 设置颜色列表 - 使用更明亮的颜色
            const colorList = [
                '#5470c6', '#91cc75', '#fac858', '#ee6666', 
                '#73c0de', '#3ba272', '#fc8452', '#9a60b4'
            ];
            
            // 使用玫瑰图（南丁格尔图）
            var option = {
                tooltip: {
                    trigger: 'item',
                    formatter: '{b}: {c}人 ({d}%)'
                },
                // 在 showCategoryView() 函数中，将原来的 legend 配置替换为：
                legend: {
                    type: 'scroll',
                    orient: 'vertical',     // 垂直方向
                    left: 'left',           // 左对齐
                    bottom: '5%',           // 距离底部5%
                    width: '35%',           // 图例区域宽度
                    itemGap: 8,             // 图例项之间的间距
                    itemWidth: 12,          // 图例标记的宽度
                    itemHeight: 8,          // 图例标记的高度
                    textStyle: {
                        color: '#fff',      // 白色文字
                        fontSize: 11,       // 字体大小
                        lineHeight: 16      // 行高
                    },
                    pageTextStyle: {
                        color: '#fff'       // 翻页按钮文字颜色
                    },
                    pageIconColor: '#fff',  // 翻页图标颜色
                    pageIconInactiveColor: 'rgba(255,255,255,0.3)', // 不可用翻页图标颜色
                    data: categoryData.map(item => item.name),
                    // 强制换行显示
                    formatter: function(name) {
                        // 如果名称太长，自动换行
                        if (name.length > 4) {
                            return name.substring(0, 4) + '\n' + name.substring(4);
                        }
                        return name;
                    }
                },
                series: [
                    {
                        name: '技能类别',
                        type: 'pie',
                        radius: ['35%', '70%'],  // 增大内圈半径，为Logo留出更多空间
                        center: ['50%', '50%'],
                        roseType: 'area', // 使用南丁格尔图
                        itemStyle: {
                            borderRadius: 5,
                            borderColor: '#fff',
                            borderWidth: 2
                        },
                        label: {
                            formatter: '{b}: {c}人',
                            fontSize: 12
                        },
                        data: categoryData.map((item, index) => ({
                            name: item.name,
                            value: item.value,
                            itemStyle: {
                                color: colorList[index % colorList.length]
                            }
                        }))
                    }
                ]
            };
            
            skillChart.setOption(option, true);
            
            // 添加点击事件
            skillChart.off('click');
            skillChart.on('click', function(params) {
                selectedCategory = params.name;
                showDetailView(selectedCategory);
            });
        }
        
        // 显示详情视图
        function showDetailView(category) {
            currentView = 'detail';
            chartTitle.textContent = category + ' - 详细技能分布';
            
            // 添加返回模式的样式
            centerLogoContainer.classList.add('back-mode');
            
            // 获取选中类别的详细数据
            var detailData = detailsData[category] || [];
            
            // 找出最大值用于归一化
            const maxValue = Math.max(...detailData.map(item => item.value));
            
            // 设置颜色列表 - 使用统一色系但不同色调
            // 根据所选类别选择基础色调
            const baseColorIndex = categoryData.findIndex(item => item.name === category) % 8;
            const baseColor = [
                '#5470c6', '#91cc75', '#fac858', '#ee6666', 
                '#73c0de', '#3ba272', '#fc8452', '#9a60b4'
            ][baseColorIndex];
            
            // 获取详情数据的颜色
            const detailColors = generateColorShades(baseColor, detailData.length || 1);
            
            // 使用玫瑰图（南丁格尔图）
            var option = {
                tooltip: {
                    trigger: 'item',
                    formatter: '{b}: {c}人 ({d}%)'
                },
                // 在 showDetailView() 函数中，将原来的 legend 配置替换为：
                legend: {
                    type: 'scroll',
                    orient: 'vertical',     // 垂直方向
                    left: 'left',           // 左对齐
                    bottom: '5%',           // 距离底部5%
                    width: '35%',           // 图例区域宽度
                    itemGap: 8,             // 图例项之间的间距
                    itemWidth: 12,          // 图例标记的宽度
                    itemHeight: 8,          // 图例标记的高度
                    textStyle: {
                        color: '#fff',      // 白色文字
                        fontSize: 11,       // 字体大小
                        lineHeight: 16      // 行高
                    },
                    pageTextStyle: {
                        color: '#fff'       // 翻页按钮文字颜色
                    },
                    pageIconColor: '#fff',  // 翻页图标颜色
                    pageIconInactiveColor: 'rgba(255,255,255,0.3)', // 不可用翻页图标颜色
                    data: detailData.map(item => item.name),
                    // 强制换行显示
                    formatter: function(name) {
                        // 如果名称太长，自动换行
                        if (name.length > 4) {
                            return name.substring(0, 4) + '\n' + name.substring(4);
                        }
                        return name;
                    }
                },
                series: [
                    {
                        name: category,
                        type: 'pie',
                        radius: ['35%', '70%'],  // 增大内圈半径，为返回按钮留出更多空间
                        center: ['50%', '50%'],
                        roseType: 'area', // 使用南丁格尔图
                        itemStyle: {
                            borderRadius: 5,
                            borderColor: '#fff',
                            borderWidth: 2
                        },
                        label: {
                            formatter: '{b}: {c}人',
                            fontSize: 12
                        },
                        data: detailData.length > 0 ? detailData.map((item, index) => ({
                            name: item.name,
                            value: item.value,
                            itemStyle: {
                                color: detailColors[index % detailColors.length]
                            }
                        })) : [{ 
                            name: '暂无数据', 
                            value: 1,
                            itemStyle: {
                                color: '#cccccc'
                            }
                        }]
                    }
                ]
            };
            
            skillChart.setOption(option, true);
        }
        
        // 生成色调变化
        function generateColorShades(baseColor, count) {
            // 简单实现：从基础色调稍微变化一些
            const colors = [];
            const baseHsl = hexToHSL(baseColor);
            
            for (let i = 0; i < count; i++) {
                const h = (baseHsl.h + (i * 10) % 40 - 20) % 360;
                const s = Math.min(Math.max(baseHsl.s + (i % 3 - 1) * 5, 30), 90);
                const l = Math.min(Math.max(baseHsl.l + (i % 3 - 1) * 5, 40), 70);
                colors.push(hslToHex(h, s, l));
            }
            return colors;
        }
        
        // 辅助函数：16进制颜色转HSL
        function hexToHSL(hex) {
            // 移除#号
            hex = hex.replace('#', '');
            
            // 解析RGB值
            const r = parseInt(hex.substring(0, 2), 16) / 255;
            const g = parseInt(hex.substring(2, 4), 16) / 255;
            const b = parseInt(hex.substring(4, 6), 16) / 255;
            
            // 找到最大和最小RGB值
            const max = Math.max(r, g, b);
            const min = Math.min(r, g, b);
            
            let h, s, l = (max + min) / 2;
            
            if (max === min) {
                h = s = 0; // 无彩色
            } else {
                const d = max - min;
                s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
                
                switch (max) {
                    case r: h = (g - b) / d + (g < b ? 6 : 0); break;
                    case g: h = (b - r) / d + 2; break;
                    case b: h = (r - g) / d + 4; break;
                }
                
                h = h * 60;
            }
            
            return { h, s: s * 100, l: l * 100 };
        }
        
        // 辅助函数：HSL转16进制颜色
        function hslToHex(h, s, l) {
            s /= 100;
            l /= 100;
            
            let c = (1 - Math.abs(2 * l - 1)) * s;
            let x = c * (1 - Math.abs((h / 60) % 2 - 1));
            let m = l - c / 2;
            let r = 0, g = 0, b = 0;
            
            if (0 <= h && h < 60) {
                r = c; g = x; b = 0;
            } else if (60 <= h && h < 120) {
                r = x; g = c; b = 0;
            } else if (120 <= h && h < 180) {
                r = 0; g = c; b = x;
            } else if (180 <= h && h < 240) {
                r = 0; g = x; b = c;
            } else if (240 <= h && h < 300) {
                r = x; g = 0; b = c;
            } else if (300 <= h && h < 360) {
                r = c; g = 0; b = x;
            }
            
            r = Math.round((r + m) * 255).toString(16).padStart(2, '0');
            g = Math.round((g + m) * 255).toString(16).padStart(2, '0');
            b = Math.round((b + m) * 255).toString(16).padStart(2, '0');
            
            return `#${r}${g}${b}`;
        }
    }
    
    // 检查并加载ECharts
    function loadECharts() {
        if (window.echarts) {
            initSkillChart();
            return;
        }
        
        if (_skillEchartLoaded) return;
        _skillEchartLoaded = true;
        
        var script = document.createElement('script');
        script.src = 'https://cdn.jsdelivr.net/npm/echarts@5.4.3/dist/echarts.min.js';
        script.onload = function() {
            initSkillChart();
        };
        document.head.appendChild(script);
    }
    
    // 公开接口
    return {
        init: loadECharts,
        getChart: function() { return skillChart; }
    };
})();

// 自动初始化
document.addEventListener('DOMContentLoaded', function() {
    window.SkillChartModule.init();
});