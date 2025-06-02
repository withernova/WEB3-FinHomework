package com.maka.service.impl;

import com.maka.mapper.RescuerMapper;
import com.maka.mapper.SkillTagMapper;
import com.maka.pojo.Rescuer;
import com.maka.pojo.SkillTag;
import com.maka.service.RescuerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RescuerServiceImpl implements RescuerService {

    @Autowired
    private RescuerMapper rescuerMapper;
    
    @Autowired
    private SkillTagMapper skillTagMapper;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Value("${flask.api.url:http://localhost:5000/api/extract_tags}")
    private String flaskApiUrl;
    
    @Override
    @Transactional
    public boolean applyRescuer(Rescuer rescuer) {
        // 设置初始任务列表为空
        rescuer.setTaskIds(new ArrayList<>());
        // 设置初始状态为available (直接通过)
        rescuer.setStatus("available");
        
        return rescuerMapper.insertRescuer(rescuer) > 0;
    }
    
    @Override
    @Transactional
    public boolean updateOrInsertRescuer(Rescuer rescuer) {
        // 设置初始任务列表为空 (如果是新记录)
        if (rescuer.getTaskIds() == null) {
            rescuer.setTaskIds(new ArrayList<>());
        }
        // 设置状态为available
        rescuer.setStatus("available");
        
        return rescuerMapper.updateOrInsertRescuer(rescuer) > 0;
    }

    @Override
    public Rescuer getRescuerByUuid(String uuid) {
        return rescuerMapper.getRescuerByUuid(uuid);
    }

    @Override
    public List<Rescuer> getAvailableRescuers() {
        return rescuerMapper.getAvailableRescuers();
    }
    
    @Override
    @Transactional
    public boolean updateRescuerSkillTags(String uuid, List<String> skillTags) {
        // 验证所有提交的标签是否在标签库中
        List<String> allTagNames = getAllSkillTags().stream()
                                   .map(SkillTag::getTagName)
                                   .collect(Collectors.toList());
        
        List<String> validTags = skillTags.stream()
                                 .filter(tag -> allTagNames.contains(tag))
                                 .collect(Collectors.toList());
        
        return rescuerMapper.updateRescuerSkillTags(uuid, validTags) > 0;
    }
    
    @Override
    public List<String> generateSkillTags(String skillsDescription) {
        try {
            // 获取所有标签
            List<SkillTag> allTags = getAllSkillTags();
            List<String> tagNames = allTags.stream()
                                   .map(SkillTag::getTagName)
                                   .collect(Collectors.toList());
            
            // 准备请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // 准备请求体 - 包含用户描述和标签库
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("user_bio", skillsDescription);
            requestBody.put("tag_library", tagNames);  // 发送数据库中的所有标签
            
            // 创建HTTP实体
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            // 调用Flask API
            Map<String, Object> response = restTemplate.postForObject(
                flaskApiUrl, 
                entity, 
                Map.class
            );
            
            if (response != null && response.containsKey("tags")) {
                @SuppressWarnings("unchecked")
                List<String> tags = (List<String>) response.get("tags");
                return tags;
            }
            
            return new ArrayList<>();
            
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<SkillTag> getAllSkillTags() {
        return skillTagMapper.getAllTags();
    }

    @Override
    @Transactional
    public boolean updateRescuerWithTags(Rescuer rescuer) {
        // 1. 更新 rescuer 基本信息和 status
        int res1 = rescuerMapper.updateRescuerWithStatus(rescuer);

        // 2. 更新 skill_tags
        int res2 = rescuerMapper.updateRescuerSkillTags(rescuer.getUuid(), rescuer.getSkill_tags());

        return res1 > 0 && res2 > 0;
    }
    
    @Override
    public List<Rescuer> getAvailableRescuersPaged(int offset, int limit) {
        return rescuerMapper.getAvailableRescuersPaged(offset, limit);
    }

    @Override
    public int getAvailableRescuersCount() {
        return rescuerMapper.getAvailableRescuersCount();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addTaskToRescuer(String uuid, Integer taskId) {
        return rescuerMapper.appendTaskId(uuid, taskId) == 1;
    }
}