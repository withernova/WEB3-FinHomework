package com.maka.service;

import com.maka.pojo.MapMarker;
import java.util.List;

public interface MapMarkerService {
    /**
     * 获取任务的所有地图标记
     */
    List<MapMarker> getMarkersByTaskId(Integer taskId);

    /**
     * 保存新的地图标记
     */
    MapMarker saveMarker(MapMarker marker, String userId);

    /**
     * 删除地图标记
     */
    void deleteMarker(Integer markerId, Integer userId);
}