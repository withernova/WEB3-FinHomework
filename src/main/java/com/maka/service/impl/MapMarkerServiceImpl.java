package com.maka.service.impl;

import com.maka.pojo.MapMarker;
import com.maka.pojo.Rescuer;
import com.maka.mapper.MapMarkerMapper;
import com.maka.mapper.RescuerMapper;
import com.maka.service.MapMarkerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class MapMarkerServiceImpl implements MapMarkerService {

    @Autowired
    private MapMarkerMapper mapMarkerMapper;

    @Autowired
    private RescuerMapper rescuerMapper;

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
}