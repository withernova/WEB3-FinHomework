package com.maka.service;

import com.maka.pojo.Task;
import java.util.List;

public interface TaskService {

    boolean createTask(Task task);

    Task getTaskById(Integer id);

    List<Task> getAllTasks();

    List<Task> getTasksByStatus(String status);

    boolean updateTask(Task task);

    boolean updateTaskStatus(Integer id, String status);

    boolean deleteTask(Integer id);

    /* ===== 新增分页 ===== */
    List<Task> getTasksPaged(int offset, int limit);

    int getTasksCountByStatus(String status);
}
