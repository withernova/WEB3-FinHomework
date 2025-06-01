// 避免重复初始化
if (!window._chinaMapEchartsLoaded) {
    window._chinaMapEchartsLoaded = true;

    document.addEventListener('DOMContentLoaded', function () {
        var dom = document.getElementById('chinaMapEcharts');
        if (!dom || !window.echarts) {
            console.error('ECharts未加载或找不到容器');
            return;
        }
        var chart = echarts.init(dom);

        // 异步加载任务点数据
        fetch('/mapshow/all')
            .then(res => res.json())
            .then(data => {
                // 省块数据示例（可自己补全全部省份，或从后端接口返回）
                var provinceData = [
                    {name:'北京', value:12, status:'busy'},
                    {name:'江苏', value:8, status:'alert'},
                    {name:'广东', value:5, status:'default'},
                    {name:'浙江', value:3, status:'default'},
                    {name:'山东', value:7, status:'busy'}
                    // ...补全所有省份
                ];
                // 经纬度点数据
                var scatterData = (data.tasks || []).map(t => ({
                    name: t.elderName || '',
                    value: [t.lng, t.lat],
                    status: t.status || ''
                }));

                function getColor(status) {
                    if (status === 'busy') return '#ffb74d';
                    if (status === 'alert') return '#e57373';
                    return '#90caf9';
                }

                var option = {
                    tooltip: {
                        trigger: 'item',
                        formatter: function(params) {
                            if(params.seriesType === 'scatter') {
                                return params.name + '<br>经纬度: ['+params.value[0].toFixed(4)+','+params.value[1].toFixed(4)+']';
                            }
                            return params.name + (params.value ? (': ' + params.value) : '');
                        }
                    },
                    geo: {
                        map: 'china',
                        roam: true,
                        label: { show: true, fontSize: 14 },
                        itemStyle: {
                            areaColor: '#e0f7fa',
                            borderColor: '#444'
                        },
                        emphasis: {
                            itemStyle: {
                                areaColor: '#f57c00'
                            }
                        }
                    },
                    series: [
                        {
                            type: 'map',
                            map: 'china',
                            geoIndex: 0,
                            data: provinceData,
                            itemStyle: {
                                areaColor: function(params) {
                                    return getColor(params.data ? params.data.status : '');
                                },
                                borderColor: '#fff'
                            }
                        },
                        {
                            type: 'scatter',
                            coordinateSystem: 'geo',
                            data: scatterData,
                            symbolSize: 14,
                            itemStyle: { color: '#ff6600', borderColor: '#fff', borderWidth: 1 }
                        }
                    ]
                };

                chart.setOption(option);

                // 省份点击交互
                chart.on('click', function(params){
                    if(params.seriesType === 'scatter') {
                        alert('点击了任务点：' + params.name);
                    } else {
                        alert('点击了省份：' + params.name);
                    }
                });
            })
            .catch(e => {
                alert('地图数据加载失败！');
                console.error(e);
            });
    });
}