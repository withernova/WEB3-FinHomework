package com.maka.mapper;

import com.maka.pojo.MapMarker;
import java.util.List;

public interface MapMarkerMapper {

    List<MapMarker> selectByTaskId(Integer taskId);

    MapMarker selectById(Integer id);

    int insert(MapMarker marker);

    int deleteById(Integer id);
}