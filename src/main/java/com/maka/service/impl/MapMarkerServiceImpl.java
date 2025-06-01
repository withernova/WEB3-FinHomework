package com.maka.service.impl;

import com.maka.pojo.MapMarker;
import com.maka.pojo.Rescuer;
import com.maka.mapper.MapMarkerMapper;
import com.maka.mapper.RescuerMapper;
import com.maka.service.MapMarkerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MapMarkerServiceImpl implements MapMarkerService {

    @Autowired
    private MapMarkerMapper mapMarkerMapper;

    @Autowired
    private RescuerMapper rescuerMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public List<MapMarker> getMarkersByTaskId(Integer taskId) {
        return mapMarkerMapper.selectByTaskId(taskId);
    }

    @Override
    @Transactional
    public MapMarker saveMarker(MapMarker marker, String userId) {
        marker.setCreatedAt(new Date());
        Rescuer rescuer = rescuerMapper.getRescuerByUuid(String.valueOf(userId));
        marker.setCreatedBy(rescuer.getName());
        mapMarkerMapper.insert(marker);
        return marker;
    }

    @Override
    @Transactional
    public void deleteMarker(Integer markerId, Integer userId) {
        MapMarker marker = mapMarkerMapper.selectById(markerId);
        if (marker == null) {
            throw new RuntimeException("标记不存在");
        }
        // 可以在此添加权限检查
        mapMarkerMapper.deleteById(markerId);
    }


    @Value("${ai.heatmap.url:http://localhost:5000/api/mapheat}")
    private String aiHeatmapUrl;

    @Override
    public List<Map<String, Object>> generateHeatmapData(Integer taskId) {
        // 1. 获取所有地图标记
        List<MapMarker> markers = mapMarkerMapper.selectByTaskId(taskId);
        if (markers == null || markers.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 2. 准备数据发送给AI服务
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("taskId", taskId);
        requestData.put("markers", markers);
        
        try {
            // 3. 调用AI服务
            ResponseEntity<Map> response = restTemplate.postForEntity(
                aiHeatmapUrl, 
                requestData, 
                Map.class
            );
            
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("heatmapPoints")) {
                // 4. 解析响应
                List<Map<String, Object>> heatmapPoints = (List<Map<String, Object>>) responseBody.get("heatmapPoints");
                System.out.println(heatmapPoints.toString());
                return heatmapPoints;
            } else {
                return Collections.emptyList();
            }
        } catch (Exception e) {
            throw new RuntimeException("热力图生成失败: " + e.getMessage());
        }
    }
}