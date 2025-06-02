// 搜救地图模块 - 使用命名空间避免冲突
window.RescueMapModule = (function() {
    'use strict';
    
    var _rescueMapLoaded = false;
    var _mapGeocoderLoaded = false;
    var mapChart = null;
    
    function initRescueMap() {
        console.log("地图初始化开始...");
        
        var dom = document.getElementById('rescue-map');
        if (!dom || !window.echarts) {
            console.error('ECharts未加载或找不到容器');
            return;
        }
        
        // 确保高德地图API加载
        if (window.AMap) {
            if (!_mapGeocoderLoaded) {
                AMap.plugin(['AMap.Geocoder'], function() {
                    console.log('高德地图Geocoder插件加载完成');
                    _mapGeocoderLoaded = true;
                });
            }
        } else {
            console.error('高德地图API未加载');
        }
        
        mapChart = echarts.init(dom);
        console.log("ECharts实例已创建");
        
        // 显示加载动画，使用更现代的加载效果
        mapChart.showLoading({
            text: '搜救数据加载中...',
            color: '#1890ff',
            textColor: '#fff',
            maskColor: 'rgba(0, 21, 41, 0.9)',
            zlevel: 10,
            fontSize: 16,
            showSpinner: true,
            spinnerRadius: 40
        });
        
        // 适应容器大小变化
        window.addEventListener('resize', function() {
            if (mapChart) {
                mapChart.resize();
            }
        });
        
        // 江苏省地理边界范围
        const JIANGSU_BOUNDS = {
            minLng: 116.18,
            maxLng: 121.57,
            minLat: 30.45,
            maxLat: 35.20
        };

        // 备用的城市边界（仅在高德API失败时使用）
        const CITY_BOUNDS = {
            '南京市': { minLng: 118.22, maxLng: 119.14, minLat: 31.14, maxLat: 32.37 },
            '无锡市': { minLng: 119.33, maxLng: 120.38, minLat: 31.07, maxLat: 32.00 },
            '苏州市': { minLng: 119.55, maxLng: 121.65, minLat: 30.47, maxLat: 32.04 },
            '常州市': { minLng: 119.08, maxLng: 120.12, minLat: 31.09, maxLat: 32.04 },
            '镇江市': { minLng: 118.58, maxLng: 119.58, minLat: 31.37, maxLat: 32.37 },
            '扬州市': { minLng: 119.01, maxLng: 119.95, minLat: 32.15, maxLat: 33.25 },
            '泰州市': { minLng: 119.38, maxLng: 120.32, minLat: 32.01, maxLat: 32.66 },
            '南通市': { minLng: 120.13, maxLng: 121.54, minLat: 31.41, maxLat: 32.43 },
            '盐城市': { minLng: 119.27, maxLng: 120.95, minLat: 32.34, maxLat: 34.28 },
            '淮安市': { minLng: 118.12, maxLng: 119.37, minLat: 32.43, maxLat: 34.06 },
            '宿迁市': { minLng: 117.56, maxLng: 119.10, minLat: 33.42, maxLat: 34.25 },
            '徐州市': { minLng: 116.22, maxLng: 118.28, minLat: 33.43, maxLat: 34.58 },
            '连云港市': { minLng: 118.24, maxLng: 119.48, minLat: 34.05, maxLat: 35.07 }
        };

        // 检查坐标是否在江苏省范围内
        function isInJiangsu(lng, lat) {
            return lng >= JIANGSU_BOUNDS.minLng && lng <= JIANGSU_BOUNDS.maxLng &&
                   lat >= JIANGSU_BOUNDS.minLat && lat <= JIANGSU_BOUNDS.maxLat;
        }
        
        // 获取任务状态颜色 - 更鲜艳的配色
        function getTaskColor(status) {
            if (status === 'waiting') return '#FF5722'; // 更明亮的橙色
            if (status === 'rescuing') return '#2979FF'; // 更鲜艳的蓝色
            if (status === 'finished') return '#00E676'; // 更鲜艳的绿色
            return '#9E9E9E';
        }
        
        // 高德地图逆地理编码缓存
        var geocodeCache = {};
        
        // 优先使用高德API进行地理位置识别
        function getLocationByAmap(lng, lat, callback) {
            const key = lng + '_' + lat;
            if (geocodeCache[key]) {
                callback(geocodeCache[key]);
                return;
            }
            
            // 确保高德地图API和Geocoder插件已加载
            if (!window.AMap) {
                console.error('高德地图API未加载');
                useFallback();
                return;
            }
            
            // 如果插件未加载，尝试加载
            if (!_mapGeocoderLoaded) {
                AMap.plugin(['AMap.Geocoder'], function() {
                    console.log('动态加载Geocoder插件完成');
                    _mapGeocoderLoaded = true;
                    proceedWithGeocoder();
                });
            } else {
                proceedWithGeocoder();
            }
            
            function proceedWithGeocoder() {
                console.log('使用高德API识别坐标 [' + lng + ', ' + lat + ']');
                
                try {
                    var geocoder = new AMap.Geocoder({
                        radius: 1000,
                        extensions: "all"
                    });
                    
                    geocoder.getAddress([lng, lat], function(status, geocodeResult) {
                        // 记录完整响应
                        console.log('高德API响应状态:', status);
                        console.log('高德API完整响应:', geocodeResult);
                        
                        if (status !== 'complete') {
                            console.warn('高德API响应状态异常:', status);
                            useFallback();
                            return;
                        }
                        
                        if (!geocodeResult || !geocodeResult.regeocode) {
                            console.warn('高德API返回数据异常');
                            useFallback();
                            return;
                        }
                        
                        var regeocode = geocodeResult.regeocode;
                        var address = regeocode.formattedAddress || '位置解析失败';
                        var addressComponent = regeocode.addressComponent || {};
                        var city = '';
                        var district = addressComponent.district || '';
                        
                        console.log('高德API返回的地址组件:', addressComponent);
                        
                        // 简化城市判断逻辑
                        if (addressComponent.city) {
                            city = addressComponent.city;
                            // 标准化城市名称
                            if (city && !city.endsWith('市')) {
                                city += '市';
                            }
                            console.log('高德API直接返回城市:', city);
                        } else if (district) {
                            city = inferCityFromDistrict(district);
                            console.log('通过区县推断城市:', city);
                        } else if (addressComponent.province === '江苏省') {
                            console.log('只有省级信息，使用备用判断');
                            city = fallbackCityDetermination(lng, lat);
                        } else {
                            console.log('无法从返回信息确定城市，使用备用判断');
                            city = fallbackCityDetermination(lng, lat);
                        }
                        
                        // 验证并纠正城市
                        if (!isValidJiangsuCity(city)) {
                            console.warn('识别的城市不在江苏有效城市列表中:', city);
                            city = fallbackCityDetermination(lng, lat);
                        }
                        
                        var locationResult = {
                            city: city,
                            address: address,
                            district: district,
                            source: 'amap_api'
                        };
                        
                        geocodeCache[key] = locationResult;
                        callback(locationResult);
                    });
                } catch (error) {
                    console.error('使用高德API时发生错误:', error);
                    useFallback();
                }
            }
            
            function useFallback() {
                var city = fallbackCityDetermination(lng, lat);
                var locationResult = {
                    city: city,
                    address: city + ' 附近',
                    district: '',
                    source: 'fallback'
                };
                geocodeCache[key] = locationResult;
                callback(locationResult);
            }
        }
                            
        // 江苏省有效城市列表
        function isValidJiangsuCity(city) {
            var validCities = [
                '南京市', '苏州市', '无锡市', '常州市', '镇江市', '扬州市',
                '泰州市', '南通市', '盐城市', '淮安市', '宿迁市', '徐州市', '连云港市'
            ];
            return validCities.indexOf(city) !== -1 || city === '江苏省其他地区';
        }
        
        // 根据区县推断城市
        function inferCityFromDistrict(district) {
            var districtCityMap = {
                // 南京市
                '玄武区': '南京市', '秦淮区': '南京市', '建邺区': '南京市', '鼓楼区': '南京市',
                '浦口区': '南京市', '栖霞区': '南京市', '雨花台区': '南京市', '江宁区': '南京市',
                '六合区': '南京市', '溧水区': '南京市', '高淳区': '南京市',
                
                // 苏州市
                '姑苏区': '苏州市', '虎丘区': '苏州市', '吴中区': '苏州市', '相城区': '苏州市',
                '吴江区': '苏州市', '昆山市': '苏州市', '太仓市': '苏州市', '常熟市': '苏州市', 
                '张家港市': '苏州市',
                
                // 无锡市
                '梁溪区': '无锡市', '锡山区': '无锡市', '惠山区': '无锡市', '滨湖区': '无锡市',
                '新吴区': '无锡市', '江阴市': '无锡市', '宜兴市': '无锡市',
                
                // 常州市
                '天宁区': '常州市', '钟楼区': '常州市', '新北区': '常州市', '武进区': '常州市',
                '金坛区': '常州市', '溧阳市': '常州市',
                
                // 镇江市
                '京口区': '镇江市', '润州区': '镇江市', '丹徒区': '镇江市', '丹阳市': '镇江市',
                '扬中市': '镇江市', '句容市': '镇江市',
                
                // 扬州市
                '广陵区': '扬州市', '邗江区': '扬州市', '江都区': '扬州市', '宝应县': '扬州市',
                '仪征市': '扬州市', '高邮市': '扬州市',
                
                // 泰州市
                '海陵区': '泰州市', '高港区': '泰州市', '姜堰区': '泰州市', '兴化市': '泰州市',
                '靖江市': '泰州市', '泰兴市': '泰州市',
                
                // 南通市
                '崇川区': '南通市', '港闸区': '南通市', '通州区': '南通市', '海安市': '南通市',
                '如东县': '南通市', '启东市': '南通市', '如皋市': '南通市', '海门市': '南通市',
                
                // 盐城市
                '亭湖区': '盐城市', '盐都区': '盐城市', '大丰区': '盐城市', '响水县': '盐城市',
                '滨海县': '盐城市', '阜宁县': '盐城市', '射阳县': '盐城市', '建湖县': '盐城市',
                '东台市': '盐城市',
                
                // 淮安市
                '淮安区': '淮安市', '淮阴区': '淮安市', '清江浦区': '淮安市', '洪泽区': '淮安市',
                '涟水县': '淮安市', '盱眙县': '淮安市', '金湖县': '淮安市',
                
                // 宿迁市
                '宿城区': '宿迁市', '宿豫区': '宿迁市', '沭阳县': '宿迁市', '泗阳县': '宿迁市',
                '泗洪县': '宿迁市',
                
                // 徐州市
                '云龙区': '徐州市', '鼓楼区': '徐州市', '贾汪区': '徐州市', '泉山区': '徐州市',
                '铜山区': '徐州市', '丰县': '徐州市', '沛县': '徐州市', '睢宁县': '徐州市',
                '新沂市': '徐州市', '邳州市': '徐州市',
                
                // 连云港市
                '连云区': '连云港市', '海州区': '连云港市', '赣榆区': '连云港市', '东海县': '连云港市',
                '灌云县': '连云港市', '灌南县': '连云港市'
            };
            
            return districtCityMap[district] || '江苏省其他地区';
        }
        
        // 备用的坐标边界判断
        function fallbackCityDetermination(lng, lat) {
            for (var cityName in CITY_BOUNDS) {
                var bounds = CITY_BOUNDS[cityName];
                if (lng >= bounds.minLng && lng <= bounds.maxLng && 
                    lat >= bounds.minLat && lat <= bounds.maxLat) {
                    console.log('备用判断: 坐标 [' + lng + ', ' + lat + '] 属于 ' + cityName);
                    return cityName;
                }
            }
            
            // 如果都不匹配，找最近的城市
            var nearestCity = '江苏省其他地区';
            var minDistance = Infinity;
            
            for (var cityName in CITY_BOUNDS) {
                var bounds = CITY_BOUNDS[cityName];
                var centerLng = (bounds.minLng + bounds.maxLng) / 2;
                var centerLat = (bounds.minLat + bounds.maxLat) / 2;
                var distance = Math.sqrt(
                    Math.pow(lng - centerLng, 2) + 
                    Math.pow(lat - centerLat, 2)
                );
                
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestCity = cityName;
                }
            }
            
            console.log('备用判断: 坐标 [' + lng + ', ' + lat + '] 最接近 ' + nearestCity);
            return nearestCity;
        }
        
        // 从后端获取数据
        console.log("开始从后端获取数据...");
        
        fetch('/mapshow/all')
            .then(function(response) {
                console.log("后端响应状态:", response.status);
                if (!response.ok) {
                    throw new Error('网络响应错误: ' + response.status);
                }
                return response.json();
            })
            .then(function(data) {
                console.log("获取到的数据:", data);
                
                // 过滤并处理任务点数据
                var validTasks = (data.tasks || []).filter(function(task) {
                    if (!task.lng || !task.lat) {
                        console.log("任务点缺少经纬度，跳过:", task);
                        return false;
                    }
                    if (!isInJiangsu(task.lng, task.lat)) {
                        console.log("任务点超出江苏省范围，跳过:", task);
                        return false;
                    }
                    return true;
                });
                
                console.log('过滤后的有效任务点数量: ' + validTasks.length);
                
                // 过滤并处理标记点数据
                var validMarkers = (data.markers || []).filter(function(marker) {
                    if (!marker.lng || !marker.lat) {
                        console.log("标记点缺少经纬度，跳过:", marker);
                        return false;
                    }
                    if (!isInJiangsu(marker.lng, marker.lat)) {
                        console.log("标记点超出江苏省范围，跳过:", marker);
                        return false;
                    }
                    return true;
                });
                
                console.log('过滤后的有效标记点数量: ' + validMarkers.length);
                
                // 处理任务点数据
                var taskPoints = validTasks.map(function(task) {
                    console.log("处理任务点:", task);
                    return {
                        name: task.elderName || '走失老人',
                        value: [task.lng, task.lat],
                        status: task.status || 'waiting',
                        id: task.id,
                        lostTime: task.lostTime ? new Date(task.lostTime).toLocaleString() : '未知',
                        rawData: task
                    };
                });
                
                // 处理标记点数据
                var markerPoints = validMarkers.map(function(marker, index) {
                    console.log("处理标记点:", marker);
                    return {
                        name: '搜救标记',
                        value: [marker.lng, marker.lat],
                        taskId: marker.taskId,
                        id: marker.id,
                        time: marker.time ? new Date(marker.time).toLocaleString() : '未知',
                        rawData: marker,
                        // 给每个标记点添加动画延迟，使其不同步
                        animationDelay: index * 300
                    };
                });
                
                // 按任务ID分组标记点
                var markersByTask = {};
                validMarkers.forEach(function(marker) {
                    if (!marker.taskId) {
                        console.log("标记点没有taskId:", marker);
                        return;
                    }
                    
                    if (!markersByTask[marker.taskId]) {
                        markersByTask[marker.taskId] = [];
                    }
                    markersByTask[marker.taskId].push(marker);
                });
                
                console.log("按任务ID分组的标记点:", markersByTask);
                
                // 创建搜救轨迹
                var pathLines = [];
                for (var taskId in markersByTask) {
                    var markers = markersByTask[taskId];
                    if (markers.length > 1) {
                        console.log('为任务 ' + taskId + ' 创建轨迹，有 ' + markers.length + ' 个标记点');
                        
                        markers.sort(function(a, b) {
                            var timeA = a.time || 0;
                            var timeB = b.time || 0;
                            return timeA - timeB;
                        });
                        
                        var coords = markers.map(function(m) {
                            return [m.lng, m.lat];
                        });
                        
                        pathLines.push({
                            coords: coords,
                            taskId: taskId
                        });
                    }
                }
                
                // 创建脉冲效果点 - 显示搜救活动区域
                var pulsePoints = [];
                validMarkers.forEach(function(marker, index) {
                    if (index % 3 === 0) { // 只为部分标记点添加脉冲效果，避免过多
                        pulsePoints.push({
                            name: '搜救活动',
                            value: [marker.lng, marker.lat],
                            taskId: marker.taskId,
                            animationDelay: index * 200
                        });
                    }
                });
                
                // 使用高德API进行城市数据统计
                determineCityDataWithAmap(taskPoints, function(cityData) {
                    console.log("使用高德API统计的城市数据:", cityData);
                    
                    // 使用江苏省地图
                    console.log("开始加载江苏省地图...");
                    
                    $.getJSON('/page/test.json', function(geoJson) {
                        console.log("江苏省地图数据加载成功");
                        
                        echarts.registerMap('jiangsu', geoJson);
                        mapChart.hideLoading();
                        
                        var option = createEnhancedMapOption('jiangsu', cityData, taskPoints, markerPoints, pathLines, pulsePoints);
                        
                        console.log("设置ECharts选项");
                        mapChart.setOption(option);
                        console.log("地图渲染完成");
                        
                        // 增强的点击事件，显示详细地址
                        mapChart.on('click', function(params) {
                            if (params.seriesType === 'scatter') {
                                if (params.seriesName === '任务点') {
                                    console.log('点击了任务点：', params.data);
                                    var lng = params.data.value[0];
                                    var lat = params.data.value[1];
                                    
                                    // 获取详细地址并显示
                                    getLocationByAmap(lng, lat, function(locationInfo) {
                                        console.log('任务点详细地址：', locationInfo);
                                        var statusMap = {
                                            'waiting': '待处理',
                                            'rescuing': '救援中',
                                            'finished': '已完成'
                                        };
                                        alert('任务点详细信息：\n姓名: ' + params.data.name + '\n状态: ' + (statusMap[params.data.status] || params.data.status) + '\n城市: ' + locationInfo.city + '\n详细地址: ' + locationInfo.address);
                                    });
                                } else if (params.seriesName === '搜救标记') {
                                    console.log('点击了搜救标记：', params.data);
                                    var lng = params.data.value[0];
                                    var lat = params.data.value[1];
                                    
                                    getLocationByAmap(lng, lat, function(locationInfo) {
                                        console.log('搜救标记详细地址：', locationInfo);
                                        alert('搜救标记详细信息：\n时间: ' + params.data.time + '\n城市: ' + locationInfo.city + '\n详细地址: ' + locationInfo.address);
                                    });
                                }
                            } else if (params.seriesType === 'map') {
                                console.log('点击了城市：', params.name, '走失老人数量:', params.value);
                            }
                        });
                        
                        // 添加定时动画，使搜救标记点产生震动效果
                        function createShakeAnimation() {
                            var currentPoints = JSON.parse(JSON.stringify(markerPoints));
                            
                            // 随机选择部分点进行震动
                            for (var i = 0; i < currentPoints.length; i++) {
                                if (Math.random() > 0.7) {
                                    var originalLng = currentPoints[i].value[0];
                                    var originalLat = currentPoints[i].value[1];
                                    
                                    // 添加微小的随机偏移
                                    currentPoints[i].value[0] = originalLng + (Math.random() - 0.5) * 0.01;
                                    currentPoints[i].value[1] = originalLat + (Math.random() - 0.5) * 0.01;
                                }
                            }
                            
                            // 更新点位置
                            mapChart.setOption({
                                series: [{
                                    name: '搜救标记',
                                    data: currentPoints
                                }]
                            }, false);
                            
                            // 1秒后恢复原始位置
                            setTimeout(function() {
                                mapChart.setOption({
                                    series: [{
                                        name: '搜救标记',
                                        data: markerPoints
                                    }]
                                }, false);
                            }, 1000);
                        }
                        
                        // 每5秒执行一次震动效果
                        setInterval(createShakeAnimation, 5000);
                        
                    }).fail(function(jqXHR, textStatus, errorThrown) {
                        console.error('江苏省地图数据加载失败', textStatus, errorThrown);
                        mapChart.hideLoading();
                        
                        // 降级到中国地图或显示错误
                        mapChart.setOption({
                            title: {
                                text: '地图数据加载失败',
                                subtext: '请检查网络连接并刷新页面',
                                left: 'center',
                                top: 'center',
                                textStyle: {
                                    color: '#fff'
                                },
                                subtextStyle: {
                                    color: '#aaa'
                                }
                            }
                        });
                    });
                });
            })
            .catch(function(error) {
                console.error('获取数据失败:', error);
                mapChart.hideLoading();
                
                mapChart.setOption({
                    title: {
                        text: '数据加载失败',
                        subtext: '请检查网络连接并刷新页面',
                        left: 'center',
                        top: 'center',
                        textStyle: {
                            color: '#fff'
                        },
                        subtextStyle: {
                            color: '#aaa'
                        }
                    }
                });
            });
        
        // 使用高德API进行城市数据统计
        function determineCityDataWithAmap(taskPoints, callback) {
            var cityCount = {};
            var processedCount = 0;
            
            if (taskPoints.length === 0) {
                callback([]);
                return;
            }
            
            taskPoints.forEach(function(task, index) {
                var lng = task.value[0];
                var lat = task.value[1];
                
                // 使用一个延迟来避免高德API请求过于频繁
                setTimeout(function() {
                    getLocationByAmap(lng, lat, function(locationInfo) {
                        var city = locationInfo.city;
                        
                        if (!cityCount[city]) {
                            cityCount[city] = 0;
                        }
                        cityCount[city]++;
                        task.city = city;
                        task.detailAddress = locationInfo.address;
                        task.district = locationInfo.district;
                        
                        processedCount++;
                        console.log('处理进度: ' + processedCount + '/' + taskPoints.length + ', 当前任务点城市: ' + city);
                        
                        // 所有任务点都处理完成后调用回调
                        if (processedCount === taskPoints.length) {
                            var cityData = [];
                            for (var cityName in cityCount) {
                                cityData.push({ name: cityName, value: cityCount[cityName] });
                            }
                            console.log('城市统计完成:', cityData);
                            callback(cityData);
                        }
                    });
                }, index * 100); // 每个请求间隔100ms避免频率限制
            });
        }
        
        // 创建增强版地图配置
        function createEnhancedMapOption(mapType, cityData, taskPoints, markerPoints, pathLines, pulsePoints) {
            var maxValue = 1;
            for (var i = 0; i < cityData.length; i++) {
                if (cityData[i].value > maxValue) {
                    maxValue = cityData[i].value;
                }
            }
            
            return {
                backgroundColor: {
                    type: 'linear',
                    x: 0,
                    y: 0,
                    x2: 0,
                    y2: 1,
                    colorStops: [{
                        offset: 0, 
                        color: '#001529' 
                    }, {
                        offset: 1, 
                        color: '#003366' 
                    }],
                    global: false
                },
                title: {
                    title: {
                    text: '', // 移除原标题，使用CSS伪元素显示
                    subtext: '',
                    left: 'center'
                },
                },
                tooltip: {
                    trigger: 'item',
                    backgroundColor: 'rgba(0,21,41,0.9)',
                    borderColor: '#1890ff',
                    borderWidth: 1,
                    textStyle: {
                        color: '#fff'
                    },
                    formatter: function(params) {
                        if (params.seriesType === 'scatter') {
                            if (params.seriesName === '任务点') {
                                var statusMap = {
                                    'waiting': '待处理',
                                    'rescuing': '救援中',
                                    'finished': '已完成'
                                };
                                
                                var cityInfo = params.data.city ? '<div>所在城市: ' + params.data.city + '</div>' : '';
                                var addressInfo = params.data.detailAddress ? '<div>详细地址: ' + params.data.detailAddress + '</div>' : '';
                                var districtInfo = params.data.district ? '<div>所在区县: ' + params.data.district + '</div>' : '';
                                
                                return '<div style="font-weight:bold;font-size:14px;color:#1890ff;margin-bottom:5px">' + params.data.name + '</div>' +
                                       '<div style="padding:3px 0">状态: <span style="color:' + getTaskColor(params.data.status) + '">' + (statusMap[params.data.status] || params.data.status) + '</span></div>' +
                                       '<div style="padding:3px 0">走失时间: ' + params.data.lostTime + '</div>' +
                                       cityInfo +
                                       districtInfo +
                                       addressInfo +
                                       '<div style="padding:3px 0;font-size:12px;color:#aaa">位置: [' + params.value[0].toFixed(6) + ', ' + params.value[1].toFixed(6) + ']</div>';
                            } else if (params.seriesName === '搜救标记') {
                                return '<div style="font-weight:bold;font-size:14px;color:#1890ff;margin-bottom:5px">专业搜救标记</div>' +
                                       '<div style="padding:3px 0">记录时间: ' + params.data.time + '</div>' +
                                       '<div style="padding:3px 0">任务ID: ' + (params.data.taskId || '未知') + '</div>' +
                                       '<div style="padding:3px 0;font-size:12px;color:#aaa">位置: [' + params.value[0].toFixed(6) + ', ' + params.value[1].toFixed(6) + ']</div>';
                            } else if (params.seriesName === '搜救活动热区') {
                                return '<div style="font-weight:bold;font-size:14px;color:#1890ff;margin-bottom:5px">搜救活动热区</div>' +
                                       '<div style="padding:3px 0">任务ID: ' + (params.data.taskId || '未知') + '</div>' +
                                       '<div style="padding:3px 0;font-size:12px;color:#aaa">位置: [' + params.value[0].toFixed(6) + ', ' + params.value[1].toFixed(6) + ']</div>';
                            }
                        } else if (params.seriesType === 'map') {
                            return '<div style="font-weight:bold;font-size:14px;color:#1890ff;margin-bottom:5px">' + params.name + '</div>' +
                                   '<div style="padding:3px 0">走失老人数量: ' + (params.value || 0) + '人</div>';
                        }
                        return params.name;
                    }
                },
                visualMap: {
                    min: 0,
                    max: maxValue,
                    left: 'right',
                    top: 'bottom',
                    text: ['高', '低'],
                    calculable: true,
                    inRange: {
                        color: ['#0A2A5C', '#0D47A1', '#1565C0', '#1976D2', '#1E88E5']
                    },
                    textStyle: {
                        color: '#fff'
                    }
                },
                geo: {
                    map: mapType,
                    roam: true,
                    label: { 
                        show: true, 
                        fontSize: 12,
                        color: '#fff',
                        textShadow: '0 0 5px rgba(0, 0, 0, 0.5)'
                    },
                    emphasis: {
                        label: {
                            show: true
                        },
                        itemStyle: {
                            areaColor: '#1E88E5'
                        }
                    },
                    itemStyle: {
                        areaColor: '#0A2A5C',
                        borderColor: '#1E88E5',
                        borderWidth: 0.5,
                        shadowColor: 'rgba(30, 136, 229, 0.5)',
                        shadowBlur: 10
                    }
                },
                series: [
                    {
                        name: '走失老人数量',
                        type: 'map',
                        map: mapType,
                        geoIndex: 0,
                        data: cityData,
                        label: {
                            show: true,
                            textStyle: {
                                color: '#fff',
                                textShadow: '0 0 5px rgba(0, 0, 0, 0.5)'
                            }
                        }
                    },
                    {
                        name: '搜救活动热区',
                        type: 'effectScatter',
                        coordinateSystem: 'geo',
                        data: pulsePoints,
                        symbolSize: function(val) {
                            return 20;
                        },
                        showEffectOn: 'render',
                        rippleEffect: {
                            brushType: 'stroke',
                            scale: 4,
                            period: 4
                        },
                        hoverAnimation: true,
                        itemStyle: {
                            color: 'rgba(228, 8, 10, 0.6)',
                            shadowBlur: 10,
                            shadowColor: '#69F0AE'
                        },
                        zlevel: 1
                    },
                    {
                        name: '任务点',
                        type: 'scatter',
                        coordinateSystem: 'geo',
                        data: taskPoints,
                        symbolSize: 22,
                        symbol: 'pin',
                        itemStyle: {
                            color: function(params) {
                                return getTaskColor(params.data.status);
                            },
                            borderColor: '#fff',
                            borderWidth: 2,
                            shadowColor: 'rgba(0, 0, 0, 0.5)',
                            shadowBlur: 10
                        },
                        emphasis: {
                            scale: true,
                            scaleSize: 2
                        },
                        zlevel: 4
                    },
                    {
                        name: '搜救标记',
                        type: 'scatter',
                        coordinateSystem: 'geo',
                        data: markerPoints,
                        symbolSize: 12,
                        symbol: 'triangle',
                        itemStyle: {
                            color: '#69F0AE',
                            borderColor: '#fff',
                            borderWidth: 1,
                            shadowColor: 'rgba(105, 240, 174, 0.5)',
                            shadowBlur: 5
                        },
                        emphasis: {
                            scale: true,
                            scaleSize: 1.5
                        },
                        zlevel: 3,
                        animation: true,
                        animationDuration: 1000,
                        animationEasing: 'elasticOut',
                        animationDelay: function(idx) {
                            return idx * 100;
                        }
                    },
                    {
                        name: '搜救轨迹',
                        type: 'lines',
                        coordinateSystem: 'geo',
                        data: pathLines,
                        polyline: true,
                        lineStyle: {
                            color: '#69F0AE',
                            width: 3,
                            opacity: 0.6,
                            curveness: 0.1,
                            type: 'solid'
                        },
                        effect: {
                            show: true,
                            period: 6,
                            trailLength: 0.5,
                            symbol: 'arrow',
                            symbolSize: 8,
                            color: '#fff'
                        },
                        zlevel: 2
                    }
                ],
                // 添加全局动画效果
                animation: true,
                animationThreshold: 1000,
                animationDuration: 1000,
                animationEasing: 'cubicOut',
                animationDelay: 0,
                animationDurationUpdate: 300,
                animationEasingUpdate: 'cubicOut',
                animationDelayUpdate: 0
            };
        }
    }
    
    // 公开接口
    return {
        init: function() {
            if (_rescueMapLoaded) return;
            _rescueMapLoaded = true;
            
            document.addEventListener('DOMContentLoaded', function() {
                initRescueMap();
            });
        },
        getMap: function() { return mapChart; }
    };
})();

// 自动初始化
window.RescueMapModule.init();