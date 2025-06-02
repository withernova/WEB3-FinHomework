package com.maka.controller;


import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.maka.service.DataViewService;
import com.maka.service.FaceComparisonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/data_view")
public class DataViewController {
    @Autowired
    private DataViewService dataViewService;

    @GetMapping("/data_visualizer")
    public Map<String, Integer> dataVisualizer()
    {
        Map<String, Integer> res = new HashMap<>();
        int rescuedCount = dataViewService.getRescuedCount();
        int rescuingCount = dataViewService.getRescuingCount();
        int totalPeopleNum = dataViewService.getTotalPeopleNum();
        res.put("RescuedCount", rescuedCount);
        res.put("RescuingCount",rescuingCount);
        res.put("TotalTaskNum",rescuingCount + rescuedCount);
        res.put("TotalPeopleNum",totalPeopleNum);
        return res;
    }
    @GetMapping("/tag")
    public String tagVisualizer() throws JsonProcessingException {
        String res = dataViewService.getTagJson();
        return res;
    }
}
