package com.maka.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maka.mapper.MapMarkerMapper;
import com.maka.mapper.TaskMapper;
import com.maka.pojo.Family;
import com.maka.pojo.MapMarker;
import com.maka.pojo.Rescuer;
import com.maka.pojo.SkillTag;
import com.maka.pojo.Task;
import com.maka.query.ApiResponse;
import com.maka.service.FamilyService;
import com.maka.service.MapMarkerService;
import com.maka.service.RescuerService;
import com.maka.service.TaskService;
import com.maka.service.UserService;
import com.maka.service.MapService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;

@RestController
@RequestMapping("/mapshow")
public class MapShowController {

    @Autowired
    private RescuerService rescuerService;
    @Autowired
    private TaskMapper taskMapper;
    @Autowired
    private MapMarkerMapper mapMarkerMapper;
    @Autowired
    private MapService mapService;

    public double[] getTaskLngLat(Task task) {
        if (task == null || task.getLocation() == null) return null;
        Map<String, Object> geo = mapService.geocodeAddress(task.getLocation());
        if (geo != null && geo.containsKey("lng") && geo.containsKey("lat")) {
            return new double[]{
                ((Number) geo.get("lng")).doubleValue(),
                ((Number) geo.get("lat")).doubleValue()
            };
        }
        return null;
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllTasksAndMarkers() {
        // 添加日志
        System.out.println("=== 开始获取所有任务和标记点 ===");
        
        List<Task> tasks = taskMapper.selectAll();
        System.out.println("获取到的任务数量: " + tasks.size());
        
        List<MapMarker> markers = mapMarkerMapper.selectAll();
        System.out.println("获取到的原始标记点数量: " + markers.size());
        
        // 打印每个标记点的详细信息
        for (int i = 0; i < markers.size(); i++) {
            MapMarker m = markers.get(i);
            System.out.println("标记点 " + i + ": ID=" + m.getId() + 
                            ", TaskId=" + m.getTaskId() + 
                            ", CreatedAt=" + m.getCreatedAt() +
                            ", 原始数据=" + m.toString());
        }

        List<Map<String, Object>> taskPoints = new ArrayList<>();
        for (Task t : tasks) {
            double[] lngLat = getTaskLngLat(t);
            if (lngLat != null) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", t.getId());
                item.put("elderName", t.getElderName());
                item.put("lostTime", t.getLostTime() != null ? t.getLostTime().getTime() : null);
                item.put("status", t.getStatus());
                item.put("lng", lngLat[0]);
                item.put("lat", lngLat[1]);
                taskPoints.add(item);
            } else {
                System.out.println("任务 " + t.getId() + " 的经纬度为空");
            }
        }

        List<Map<String, Object>> markerPoints = new ArrayList<>();
        for (int i = 0; i < markers.size(); i++) {
            MapMarker m = markers.get(i);
            System.out.println("处理标记点 " + i + ": " + m.getId());
            
            double[] center = this.getMarkerCenter(m);
            System.out.println("标记点 " + m.getId() + " 的坐标: " + 
                            (center != null ? "[" + center[0] + ", " + center[1] + "]" : "null"));
            
            if (center != null) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", m.getId());
                item.put("taskId", m.getTaskId());
                item.put("lng", center[0]);
                item.put("lat", center[1]);
                item.put("time", m.getCreatedAt() != null ? m.getCreatedAt().getTime() : null);
                markerPoints.add(item);
                System.out.println("成功添加标记点: " + item);
            } else {
                System.out.println("标记点 " + m.getId() + " 坐标计算失败");
            }
        }

        System.out.println("最终任务点数量: " + taskPoints.size());
        System.out.println("最终标记点数量: " + markerPoints.size());

        Map<String, Object> result = new HashMap<>();
        result.put("tasks", taskPoints);
        result.put("markers", markerPoints);
        
        System.out.println("返回结果: " + result);
        System.out.println("=== 结束获取所有任务和标记点 ===");
        
        return ResponseEntity.ok(result);
    }

    public double[] getMarkerCenter(MapMarker marker) {
        System.out.println("=== getMarkerCenter 开始 ===");
        System.out.println("输入的marker: " + marker);
        
        if (marker == null) {
            System.out.println("marker为null，返回null");
            return null;
        }
        
        try {
            String geometryJson = marker.getGeometry();
            System.out.println("geometryJson: " + geometryJson);
            
            if (geometryJson == null || geometryJson.trim().isEmpty()) {
                System.out.println("geometryJson为空，返回null");
                return null;
            }
            
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> geometry = objectMapper.readValue(geometryJson, Map.class);
            System.out.println("解析后的geometry: " + geometry);
            
            String type = (String) geometry.get("type");
            System.out.println("geometry类型: " + type);
            
            List coords = (List) geometry.get("coordinates");
            System.out.println("coordinates: " + coords);
            
            if ("Point".equals(type)) {
                System.out.println("处理Point类型");
                double lng = ((Number) coords.get(0)).doubleValue();
                double lat = ((Number) coords.get(1)).doubleValue();
                System.out.println("Point坐标: [" + lng + ", " + lat + "]");
                return new double[]{lng, lat};
                
            } else if ("Polygon".equals(type)) {
                System.out.println("处理Polygon类型");
                // 取第一个环第一个点
                List firstRing = (List) coords.get(0);
                List firstPoint = (List) firstRing.get(0);
                double lng = ((Number) firstPoint.get(0)).doubleValue();
                double lat = ((Number) firstPoint.get(1)).doubleValue();
                System.out.println("Polygon第一个点坐标: [" + lng + ", " + lat + "]");
                return new double[]{lng, lat};
                
            } else if ("LineString".equals(type)) {
                System.out.println("处理LineString类型");
                List firstPoint = (List) coords.get(0);
                double lng = ((Number) firstPoint.get(0)).doubleValue();
                double lat = ((Number) firstPoint.get(1)).doubleValue();
                System.out.println("LineString第一个点坐标: [" + lng + ", " + lat + "]");
                return new double[]{lng, lat};
                
            } else {
                System.out.println("未知的geometry类型: " + type);
            }
            
        } catch (Exception e) {
            System.out.println("解析geometry时发生异常: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("=== getMarkerCenter 结束，返回null ===");
        return null;
    }


    @GetMapping("/skill-distribution")
    public ResponseEntity<Map<String, Object>> getSkillDistribution() {
        // 获取所有可用的搜救队员
        List<Rescuer> rescuers = rescuerService.getAvailableRescuers();
        
        // 获取所有技能标签
        List<SkillTag> allTags = rescuerService.getAllSkillTags();
        
        // 按标签类别分类并统计
        Map<String, Integer> categoryCount = new HashMap<>();
        Map<String, List<Map<String, Object>>> categoryDetails = new HashMap<>();
        
        // 初始化类别映射
        for (SkillTag tag : allTags) {
            if (!categoryCount.containsKey(tag.getTagCategory())) {
                categoryCount.put(tag.getTagCategory(), 0);
                categoryDetails.put(tag.getTagCategory(), new ArrayList<>());
            }
        }
        
        // 统计每个队员的技能标签
        for (Rescuer rescuer : rescuers) {
            List<String> rescuerTags = rescuer.getSkillTags();
            
            for (SkillTag tag : allTags) {
                if (rescuerTags.contains(tag.getTagName())) {
                    // 增加类别计数
                    categoryCount.put(tag.getTagCategory(), 
                                     categoryCount.get(tag.getTagCategory()) + 1);
                    
                    // 查找该标签是否已存在于详情列表中
                    boolean found = false;
                    for (Map<String, Object> detail : categoryDetails.get(tag.getTagCategory())) {
                        if (detail.get("name").equals(tag.getTagName())) {
                            int count = (int) detail.get("value");
                            detail.put("value", count + 1);
                            found = true;
                            break;
                        }
                    }
                    
                    // 如果不存在，添加新的标签统计
                    if (!found) {
                        Map<String, Object> detail = new HashMap<>();
                        detail.put("name", tag.getTagName());
                        detail.put("value", 1);
                        categoryDetails.get(tag.getTagCategory()).add(detail);
                    }
                }
            }
        }
        
        // 准备返回数据
        Map<String, Object> result = new HashMap<>();
        result.put("categoryCount", categoryCount);
        result.put("categoryDetails", categoryDetails);
        result.put("totalRescuers", rescuers.size());
        
        return ResponseEntity.ok(result);
    }
}