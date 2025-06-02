package com.maka.service.impl;

import com.maka.mapper.FamilyMapper;
import com.maka.mapper.TaskMapper;
import com.maka.pojo.Family;
import com.maka.pojo.Rescuer;
import com.maka.pojo.SkillTag;
import com.maka.pojo.Task;
import com.maka.pojo.User;
import com.maka.mapper.UserMapper;
import com.maka.mapper.SkillTagMapper;
import com.maka.service.RescuerService;
import com.maka.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * 走失老人任务服务实现
 */
@Service
public class TaskServiceImpl implements TaskService {

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private FamilyMapper familyMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SkillTagMapper skillTagMapper;

    private static final Logger logger = LoggerFactory.getLogger(TaskServiceImpl.class);

    @Value("${flask.recommendation.url:http://localhost:5000/api/recommend-rescuers}")
    private String recommendationApiUrl;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RescuerService rescuerService;

    @Override @Transactional
    public int createTaskAndAssignToFamily(Task task, String familyUuid) {
        taskMapper.insert(task);
        familyMapper.addTaskId(familyUuid, task.getId());
        return task.getId();
    }

    @Override
    public Task getTaskById(Integer id) {
        return taskMapper.selectById(id);
    }

    @Override public boolean isTaskBelongsToFamily(Integer taskId,String familyUuid){
        Family f = familyMapper.selectByUuid(familyUuid);
        return f!=null && f.getTaskIds()!=null && f.getTaskIds().contains(taskId);
    }

    @Override @Transactional
    public boolean deleteTask(String uuid,Integer taskId){
        familyMapper.deleteTaskId(uuid,taskId);
        return taskMapper.deleteById(taskId)>0;
    }

    @Override public boolean updateTaskStatus(Integer id,String status){
        Task t = taskMapper.selectById(id);
        if (t==null) return false;
        t.setStatus(status);
        return taskMapper.update(t)>0;
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
    @Transactional(rollbackFor = Exception.class)
    public boolean acceptTask(Integer taskId, String rescuerUid) {

        /* ① 查询任务 */
        Task task = taskMapper.selectById(taskId);
        if (task == null) return false;
        if ("finished".equals(task.getStatus())) return false;      // 已完成不可再接

        /* ② 若还是 waiting → 改成 rescuing；若已 rescui ng 保持原状 */
        if ("waiting".equals(task.getStatus())) {
            int u = taskMapper.updateTaskStatus(taskId,"rescuing");
            if (u == 0) {
                // 并发下被他人改变为 finished / 其它状态
                task = taskMapper.selectById(taskId);
                if (!"rescuing".equals(task.getStatus())) return false;
            }
        }

        /* ③ 将任务 ID 写入救援者 task_ids（需去重） */
        boolean ok = rescuerService.addTaskToRescuer(rescuerUid, taskId);
        if (!ok) throw new IllegalStateException("写入 rescuer.task_ids 失败");
        return true;
    }

    @Override
    public int countTasks(String elderName, String location, String status) {
        return taskMapper.countTasks(elderName, location, status);
    }

    @Override
    public List<Task> selectTasksByPage(int offset, int limit, String elderName, String location, String status) {
        return taskMapper.selectTasksByPage(offset, limit, elderName, location, status);
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
            
            // 3. 获取所有技能标签
            List<SkillTag> skillTags = skillTagMapper.getAllTags();
            List<String> allTags = skillTags.stream()
                .map(SkillTag::getTagName)
                .collect(Collectors.toList());
            
            // 4. 准备请求数据
            Map<String, Object> requestData = new HashMap<>();
            
            // 添加任务信息
            Map<String, Object> taskData = new HashMap<>();
            taskData.put("id", task.getId());
            taskData.put("elderName", task.getElderName());
            taskData.put("location", task.getLocation());
            taskData.put("extraInfo", task.getExtraInfo());
            System.out.println("extrainfo!!!"+task.getExtraInfo());
            requestData.put("task", taskData);
            
            // 添加标签库
            requestData.put("tagLibrary", allTags);
            
            // 添加救援人员信息
            List<Map<String, Object>> rescuersList = new ArrayList<>();
            for (Rescuer rescuer : availableRescuers) {
                Map<String, Object> rescuerData = new HashMap<>();
                rescuerData.put("uuid", rescuer.getUuid());
                rescuerData.put("name", rescuer.getName());
                rescuerData.put("location", rescuer.getLocation());
                rescuerData.put("skillTags", rescuer.getSkill_tags());
                rescuerData.put("taskIds", rescuer.getTaskIds());
                
                // 获取用户信息和电话号码
                User user = userMapper.getUserByUuid(rescuer.getUuid());
                if (user != null) {
                    rescuerData.put("phone", user.getPhone());
                }
                
                // 计算成功任务数
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
            
            // 5. 调用Python Flask API
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestData, headers);
            
            Map<String, Object> response = restTemplate.postForObject(
                    recommendationApiUrl,
                    entity,
                    Map.class
            );
            
            // 6. 处理响应结果
            if (response != null && response.containsKey("success") && (Boolean)response.get("success")) {
                result.put("success", true);
                result.put("reportHtml", response.get("report_html"));
                result.put("reportText", response.get("report_text"));
                result.put("topRescuers", response.get("top_rescuers"));
                
                // 添加docx_url字段
                if (response.containsKey("docx_url")) {
                    result.put("docx_url", response.get("docx_url"));
                }
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


    @Override
    public List<Task> getTasksByRescuer(String rescuerUid) {
        Rescuer r = rescuerService.getRescuerByUuid(rescuerUid);
        if (r == null || r.getTaskIds() == null || r.getTaskIds().isEmpty()) {
            return Collections.emptyList();
        }
        return taskMapper.selectByIdList(r.getTaskIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean finishTask(Integer taskId, String rescuerUid) {
        // 校验任务是否属于该 rescuer
        Rescuer r = rescuerService.getRescuerByUuid(rescuerUid);
        if (r == null || r.getTaskIds() == null || !r.getTaskIds().contains(taskId))
            return false;

        // 更新状态
        int u = taskMapper.updateStatus(taskId, "finished");
        return u == 1;
    }

    @Value("${flask.summary.url:http://localhost:5000/api/generate-summary}")
    private String summaryApiUrl;
    
    /**
     * 生成老人信息摘要
     * 
     * @param templateData 模板收集的详细信息
     * @return 生成的摘要及结果状态
     */
    @Override
    public Map<String, Object> generateElderInfoSummary(Map<String, Object> templateData) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            logger.info("开始生成老人信息摘要，数据包含字段: {}", templateData.keySet());
            
            // 尝试调用Python Flask API生成摘要
            try {
                // 构建请求头
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(templateData, headers);
                
                // 调用Python Flask API
                Map<String, Object> response = restTemplate.postForObject(
                    summaryApiUrl,
                    entity,
                    Map.class
                );
                
                logger.info("Python摘要服务返回: {}", response);
                
                // 处理响应结果
                if (response != null && response.containsKey("success") && (Boolean)response.get("success")) {
                    String summary = (String)response.get("summary");
                    result.put("code", 0);
                    result.put("msg", "摘要生成成功");
                    result.put("data", Map.of("summary", summary));
                    return result;
                } else {
                    logger.warn("Python摘要服务未返回有效结果，将使用本地摘要方法");
                }
            } catch (Exception e) {
                logger.warn("调用Python摘要服务失败，将使用本地摘要方法", e);
            }
            
            // 如果Python服务失败，使用本地方法生成摘要
            return generateLocalSummary(templateData);
            
        } catch (Exception e) {
            logger.error("生成摘要时发生错误", e);
            result.put("code", 500);
            result.put("msg", "服务器错误: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * 本地生成摘要（当Python服务不可用时）
     * 
     * @param templateData 模板收集的详细信息
     * @return 生成的摘要及结果状态
     */
    private Map<String, Object> generateLocalSummary(Map<String, Object> templateData) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            StringBuilder summary = new StringBuilder();
            
            // 基本信息
            if (templateData.containsKey("age") || templateData.containsKey("gender")) {
                summary.append("基本特征：");
                if (templateData.containsKey("age") && templateData.get("age") != null && !templateData.get("age").toString().isEmpty()) {
                    summary.append(templateData.get("age")).append("岁");
                }
                
                if (templateData.containsKey("gender") && templateData.get("gender") != null) {
                    if (summary.toString().endsWith("：")) {
                        summary.append(templateData.get("gender")).append("性");
                    } else {
                        summary.append("、").append(templateData.get("gender")).append("性");
                    }
                }
                
                if (templateData.containsKey("height") && templateData.get("height") != null && !templateData.get("height").toString().isEmpty()) {
                    summary.append("、身高约").append(templateData.get("height")).append("cm");
                }
                
                if (templateData.containsKey("weight") && templateData.get("weight") != null && !templateData.get("weight").toString().isEmpty()) {
                    summary.append("、体重约").append(templateData.get("weight")).append("kg");
                }
                
                summary.append("。\n\n");
            }
            
            // 身体状况
            StringBuilder healthInfo = new StringBuilder();
            
            if (templateData.containsKey("health") && templateData.get("health") != null) {
                Object healthObj = templateData.get("health");
                if (healthObj instanceof java.util.List) {
                    java.util.List<?> health = (java.util.List<?>) healthObj;
                    if (!health.isEmpty()) {
                        healthInfo.append("健康状况：");
                        for (int i = 0; i < health.size(); i++) {
                            if (i > 0) healthInfo.append("、");
                            healthInfo.append(health.get(i));
                        }
                        healthInfo.append("。");
                    }
                }
            }
            
            if (templateData.containsKey("mobility") && templateData.get("mobility") != null) {
                Object mobilityObj = templateData.get("mobility");
                if (mobilityObj instanceof java.util.List) {
                    java.util.List<?> mobility = (java.util.List<?>) mobilityObj;
                    if (!mobility.isEmpty()) {
                        if (healthInfo.length() > 0) healthInfo.append(" ");
                        healthInfo.append("行动能力：");
                        for (int i = 0; i < mobility.size(); i++) {
                            if (i > 0) healthInfo.append("、");
                            healthInfo.append(mobility.get(i));
                        }
                        healthInfo.append("。");
                    }
                }
            }
            
            if (templateData.containsKey("medication") && templateData.get("medication") != null && !templateData.get("medication").toString().isEmpty()) {
                if (healthInfo.length() > 0) healthInfo.append(" ");
                healthInfo.append("用药情况：").append(templateData.get("medication")).append("。");
            }
            
            if (healthInfo.length() > 0) {
                summary.append("身体状况：").append(healthInfo).append("\n\n");
            }
            
            // 外貌与着装
            StringBuilder appearanceInfo = new StringBuilder();
            
            // 处理外貌特征
            if (templateData.containsKey("hair") && templateData.get("hair") != null) {
                Object hairObj = templateData.get("hair");
                if (hairObj instanceof java.util.List) {
                    java.util.List<?> hair = (java.util.List<?>) hairObj;
                    if (!hair.isEmpty()) {
                        appearanceInfo.append("发型特征：");
                        for (int i = 0; i < hair.size(); i++) {
                            if (i > 0) appearanceInfo.append("、");
                            appearanceInfo.append(hair.get(i));
                        }
                        appearanceInfo.append("。");
                    }
                }
            }
            
            if (templateData.containsKey("marks") && templateData.get("marks") != null) {
                Object marksObj = templateData.get("marks");
                if (marksObj instanceof java.util.List) {
                    java.util.List<?> marks = (java.util.List<?>) marksObj;
                    if (!marks.isEmpty()) {
                        if (appearanceInfo.length() > 0) appearanceInfo.append(" ");
                        appearanceInfo.append("特殊标记：");
                        for (int i = 0; i < marks.size(); i++) {
                            if (i > 0) appearanceInfo.append("、");
                            appearanceInfo.append(marks.get(i));
                        }
                        appearanceInfo.append("。");
                    }
                }
            }
            
            if (templateData.containsKey("bodyType") && templateData.get("bodyType") != null) {
                Object bodyTypeObj = templateData.get("bodyType");
                if (bodyTypeObj instanceof java.util.List) {
                    java.util.List<?> bodyType = (java.util.List<?>) bodyTypeObj;
                    if (!bodyType.isEmpty()) {
                        if (appearanceInfo.length() > 0) appearanceInfo.append(" ");
                        appearanceInfo.append("体型特征：");
                        for (int i = 0; i < bodyType.size(); i++) {
                            if (i > 0) appearanceInfo.append("、");
                            appearanceInfo.append(bodyType.get(i));
                        }
                        appearanceInfo.append("。");
                    }
                }
            }
            
            // 处理着装信息
            StringBuilder clothingInfo = new StringBuilder();
            if (templateData.containsKey("topClothing") && templateData.get("topClothing") != null && !templateData.get("topClothing").toString().isEmpty()) {
                clothingInfo.append("上衣：").append(templateData.get("topClothing"));
            }
            
            if (templateData.containsKey("bottomClothing") && templateData.get("bottomClothing") != null && !templateData.get("bottomClothing").toString().isEmpty()) {
                if (clothingInfo.length() > 0) clothingInfo.append("，");
                clothingInfo.append("下装：").append(templateData.get("bottomClothing"));
            }
            
            if (templateData.containsKey("shoes") && templateData.get("shoes") != null && !templateData.get("shoes").toString().isEmpty()) {
                if (clothingInfo.length() > 0) clothingInfo.append("，");
                clothingInfo.append("鞋子：").append(templateData.get("shoes"));
            }
            
            if (clothingInfo.length() > 0) {
                if (appearanceInfo.length() > 0) appearanceInfo.append(" ");
                appearanceInfo.append("着装：").append(clothingInfo).append("。");
            }
            
            if (templateData.containsKey("belongings") && templateData.get("belongings") != null) {
                Object belongingsObj = templateData.get("belongings");
                if (belongingsObj instanceof java.util.List) {
                    java.util.List<?> belongings = (java.util.List<?>) belongingsObj;
                    if (!belongings.isEmpty()) {
                        if (appearanceInfo.length() > 0) appearanceInfo.append(" ");
                        appearanceInfo.append("随身物品：");
                        for (int i = 0; i < belongings.size(); i++) {
                            if (i > 0) appearanceInfo.append("、");
                            appearanceInfo.append(belongings.get(i));
                        }
                        appearanceInfo.append("。");
                    }
                }
            }
            
            if (appearanceInfo.length() > 0) {
                summary.append("外貌与着装：").append(appearanceInfo).append("\n\n");
            }
            
            // 爱好与习惯
            StringBuilder hobbyInfo = new StringBuilder();
            
            if (templateData.containsKey("hobbies") && templateData.get("hobbies") != null) {
                Object hobbiesObj = templateData.get("hobbies");
                if (hobbiesObj instanceof java.util.List) {
                    java.util.List<?> hobbies = (java.util.List<?>) hobbiesObj;
                    if (!hobbies.isEmpty()) {
                        hobbyInfo.append("爱好：");
                        for (int i = 0; i < hobbies.size(); i++) {
                            if (i > 0) hobbyInfo.append("、");
                            hobbyInfo.append(hobbies.get(i));
                        }
                        hobbyInfo.append("。");
                    }
                }
            }
            
            if (templateData.containsKey("activities") && templateData.get("activities") != null) {
                Object activitiesObj = templateData.get("activities");
                if (activitiesObj instanceof java.util.List) {
                    java.util.List<?> activities = (java.util.List<?>) activitiesObj;
                    if (!activities.isEmpty()) {
                        if (hobbyInfo.length() > 0) hobbyInfo.append(" ");
                        hobbyInfo.append("日常活动：");
                        for (int i = 0; i < activities.size(); i++) {
                            if (i > 0) hobbyInfo.append("、");
                            hobbyInfo.append(activities.get(i));
                        }
                        hobbyInfo.append("。");
                    }
                }
            }
            
            if (templateData.containsKey("habits") && templateData.get("habits") != null && !templateData.get("habits").toString().isEmpty()) {
                if (hobbyInfo.length() > 0) hobbyInfo.append(" ");
                hobbyInfo.append("生活习惯：").append(templateData.get("habits")).append("。");
            }
            
            if (hobbyInfo.length() > 0) {
                summary.append("爱好与习惯：").append(hobbyInfo).append("\n\n");
            }
            
            // 行为特点
            StringBuilder behaviorInfo = new StringBuilder();
            
            if (templateData.containsKey("behavior") && templateData.get("behavior") != null) {
                Object behaviorObj = templateData.get("behavior");
                if (behaviorObj instanceof java.util.List) {
                    java.util.List<?> behavior = (java.util.List<?>) behaviorObj;
                    if (!behavior.isEmpty()) {
                        behaviorInfo.append("行为特点：");
                        for (int i = 0; i < behavior.size(); i++) {
                            if (i > 0) behaviorInfo.append("、");
                            behaviorInfo.append(behavior.get(i));
                        }
                        behaviorInfo.append("。");
                    }
                }
            }
            
            if (templateData.containsKey("language") && templateData.get("language") != null) {
                Object languageObj = templateData.get("language");
                if (languageObj instanceof java.util.List) {
                    java.util.List<?> language = (java.util.List<?>) languageObj;
                    if (!language.isEmpty()) {
                        if (behaviorInfo.length() > 0) behaviorInfo.append(" ");
                        behaviorInfo.append("语言能力：");
                        for (int i = 0; i < language.size(); i++) {
                            if (i > 0) behaviorInfo.append("、");
                            behaviorInfo.append(language.get(i));
                        }
                        behaviorInfo.append("。");
                    }
                }
            }
            
            if (templateData.containsKey("frequentPlaces") && templateData.get("frequentPlaces") != null) {
                Object frequentPlacesObj = templateData.get("frequentPlaces");
                if (frequentPlacesObj instanceof java.util.List) {
                    java.util.List<?> frequentPlaces = (java.util.List<?>) frequentPlacesObj;
                    if (!frequentPlaces.isEmpty()) {
                        if (behaviorInfo.length() > 0) behaviorInfo.append(" ");
                        behaviorInfo.append("常去地点：");
                        for (int i = 0; i < frequentPlaces.size(); i++) {
                            if (i > 0) behaviorInfo.append("、");
                            behaviorInfo.append(frequentPlaces.get(i));
                        }
                        behaviorInfo.append("。");
                    }
                }
            }
            
            if (behaviorInfo.length() > 0) {
                summary.append("行为特点：").append(behaviorInfo).append("\n\n");
            }
            
            // 紧急情况
            if (templateData.containsKey("emergency") && templateData.get("emergency") != null) {
                Object emergencyObj = templateData.get("emergency");
                if (emergencyObj instanceof java.util.List) {
                    java.util.List<?> emergency = (java.util.List<?>) emergencyObj;
                    if (!emergency.isEmpty()) {
                        summary.append("紧急注意事项：");
                        for (int i = 0; i < emergency.size(); i++) {
                            if (i > 0) summary.append("、");
                            summary.append(emergency.get(i));
                        }
                        summary.append("。\n\n");
                    }
                }
            }
            
            // 其他信息
            if (templateData.containsKey("additionalInfo") && templateData.get("additionalInfo") != null && !templateData.get("additionalInfo").toString().isEmpty()) {
                summary.append("其他信息：").append(templateData.get("additionalInfo"));
            }
            
            String finalSummary = summary.toString().trim();
            
            result.put("code", 0);
            result.put("msg", "摘要生成成功");
            result.put("data", Map.of("summary", finalSummary));
            
            return result;
        } catch (Exception e) {
            logger.error("生成本地摘要时发生错误", e);
            result.put("code", 500);
            result.put("msg", "服务器错误: " + e.getMessage());
            return result;
        }
    }

}