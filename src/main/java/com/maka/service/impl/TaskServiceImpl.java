package com.maka.service.impl;

import com.maka.mapper.FamilyMapper;
import com.maka.mapper.TaskMapper;
import com.maka.pojo.Family;
import com.maka.pojo.Rescuer;
import com.maka.pojo.Task;
import com.maka.service.RescuerService;
import com.maka.service.TaskService;
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

/**
 * 走失老人任务服务实现
 */
@Service
public class TaskServiceImpl implements TaskService {

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private FamilyMapper familyMapper;

    @Value("${flask.recommendation.url:http://localhost:5000/recommend-rescuers}")
    private String recommendationApiUrl;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RescuerService rescuerService;

    @Override
    @Transactional
    public int createTaskAndAssignToFamily(Task task, String familyUuid) {
        // 插入任务
        taskMapper.insert(task);

        // 获取生成的任务ID
        int taskId = task.getId();

        // 将任务ID添加到家庭成员的task_ids中
        familyMapper.addTaskId(familyUuid, taskId);

        return taskId;
    }

    @Override
    public Task getTaskById(Integer id) {
        return taskMapper.selectById(id);
    }

    @Override
    public boolean isTaskBelongsToFamily(Integer taskId, String familyUuid) {
        Family family = familyMapper.selectByUuid(familyUuid);
        return family != null && family.getTaskIds() != null && family.getTaskIds().contains(taskId);
    }

    @Override
    @Transactional
    public boolean deleteTask(String uuid,Integer taskId) {
        familyMapper.deleteTaskId(uuid,taskId);
        return taskMapper.deleteById(taskId) > 0;

    }

    @Override
    public boolean updateTaskStatus(Integer id, String status) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            return false;
        }

        task.setStatus(status);
        return taskMapper.update(task) > 0;
    }

    @Override
    public List<Task> getAllTasks() {
        return taskMapper.selectAll();
    }

    @Override
    public List<Task> getTasksByStatus(String status) {
        return taskMapper.selectByStatus(status);
    }

    @Override
    public Map<String, Object> getTasksByFamilyId(String familyUuid, int page, int limit, String elderName, String status) {
        Map<String, Object> result = new HashMap<>();
        List<Task> taskList = new ArrayList<>();
        int total = 0;

        // 获取家庭成员信息
        Family family = familyMapper.selectByUuid(familyUuid);

        if (family != null && family.getTaskIds() != null && !family.getTaskIds().isEmpty()) {
            // 获取该家庭成员的所有任务
            List<Task> allTasks = taskMapper.selectByIdList(family.getTaskIds());

            // 过滤任务（简单实现，实际应该在数据库层做筛选和分页）
            List<Task> filteredTasks = new ArrayList<>();
            for (Task task : allTasks) {
                boolean matchElderName = elderName == null || elderName.isEmpty() ||
                        (task.getElderName() != null && task.getElderName().contains(elderName));
                boolean matchStatus = status == null || status.isEmpty() ||
                        (task.getStatus() != null && task.getStatus().equals(status));

                if (matchElderName && matchStatus) {
                    filteredTasks.add(task);
                }
            }

            // 分页处理
            total = filteredTasks.size();
            int fromIndex = (page - 1) * limit;
            int toIndex = Math.min(fromIndex + limit, total);

            if (fromIndex < total) {
                taskList = filteredTasks.subList(fromIndex, toIndex);
            }
        }

        result.put("total", total);
        result.put("records", taskList);

        return result;
    }

    /**
     * 更新任务信息
     */
    @Override
    public boolean updateTask(Task task) {
        return taskMapper.update(task) > 0;
    }

    @Override
    public Map<String, Object> getRecommendedRescuersForTask(Integer taskId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 1. 获取任务信息
            Task task = getTaskById(taskId);
            if (task == null) {
                result.put("success", false);
                result.put("message", "任务不存在");
                return result;
            }
            // 2. 获取所有可用救援人员
            List<Rescuer> availableRescuers = rescuerService.getAvailableRescuers();
            if (availableRescuers.isEmpty()) {
                result.put("success", false);
                result.put("message", "当前没有可用的救援人员");
                return result;
            }
            
            // 3. 准备请求数据
            Map<String, Object> requestData = new HashMap<>();
            
            // 添加任务信息
            Map<String, Object> taskData = new HashMap<>();
            taskData.put("id", task.getId());
            taskData.put("elderName", task.getElderName());
            taskData.put("location", task.getLocation());
            taskData.put("extraInfo", task.getExtraInfo());
            requestData.put("task", taskData);
            
            // 添加救援人员信息
            List<Map<String, Object>> rescuersList = new ArrayList<>();
            for (Rescuer rescuer : availableRescuers) {
                Map<String, Object> rescuerData = new HashMap<>();
                rescuerData.put("uuid", rescuer.getUuid());
                rescuerData.put("name", rescuer.getName());
                rescuerData.put("location", rescuer.getLocation());
                rescuerData.put("skillTags", rescuer.getSkillTags());
                rescuerData.put("taskIds", rescuer.getTaskIds());
                
                // 计算成功任务数（如果需要）
                int successfulTasks = 0;
                if (rescuer.getTaskIds() != null && !rescuer.getTaskIds().isEmpty()) {
                    List<Task> rescuerTasks = taskMapper.selectByIdList(rescuer.getTaskIds());
                    successfulTasks = (int) rescuerTasks.stream()
                            .filter(t -> "finished".equals(t.getStatus()))
                            .count();
                }
                rescuerData.put("successfulTasks", successfulTasks);
                
                rescuersList.add(rescuerData);
            }
            requestData.put("rescuers", rescuersList);
            
            // 4. 调用Python Flask API
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestData, headers);
            
            Map<String, Object> response = restTemplate.postForObject(
                    recommendationApiUrl,
                    entity,
                    Map.class
            );
            
            // 5. 处理响应结果
            if (response != null && response.containsKey("success") && (Boolean)response.get("success")) {
                result.put("success", true);
                result.put("reportHtml", response.get("report_html"));
                result.put("reportText", response.get("report_text"));
                result.put("topRescuers", response.get("top_rescuers"));
            } else {
                result.put("success", false);
                result.put("message", response != null ? response.get("message") : "推荐服务异常");
            }
            
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "服务异常: " + e.getMessage());
            return result;
        }
    }
}