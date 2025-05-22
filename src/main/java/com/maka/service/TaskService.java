package com.maka.service;

import com.maka.pojo.Task;
import java.util.List;
import java.util.Map;

public interface TaskService {

    /**
     * 创建任务并分配给家庭成员
     * @param task 任务对象
     * @param familyUuid 家庭成员UUID
     * @return 任务ID
     */
    int createTaskAndAssignToFamily(Task task, String familyUuid);

    /**
     * 根据ID获取任务
     * @param id 任务ID
     * @return 任务对象
     */
    Task getTaskById(Integer id);

    /**
     * 更新任务状态
     * @param id 任务ID
     * @param status 新状态
     * @return 是否更新成功
     */
    boolean updateTaskStatus(Integer id, String status);

    /**
     * 获取所有任务
     * @return 任务列表
     */
    List<Task> getAllTasks();

    /**
     * 根据状态获取任务
     * @param status 任务状态
     * @return 任务列表
     */
    List<Task> getTasksByStatus(String status);


    boolean isTaskBelongsToFamily(Integer taskId, String familyUuid);

    /**
     * 删除任务
     */
    boolean deleteTask(String uuid, Integer taskId);

    /**
     * 获取家庭成员的任务列表（分页）
     */
    Map<String, Object> getTasksByFamilyId(String familyUuid, int page, int limit, String elderName, String status);

    /**
     * 更新任务信息
     */
    boolean updateTask(Task task);

    Map<String, Object> getRecommendedRescuersForTask(Integer taskId);
}