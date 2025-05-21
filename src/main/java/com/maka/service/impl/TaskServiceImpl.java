package com.maka.service.impl;

import com.maka.mapper.TaskMapper;
import com.maka.pojo.Task;
import com.maka.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class TaskServiceImpl implements TaskService {

    @Autowired
    private TaskMapper taskMapper;

    @Override
    public boolean createTask(Task task) {
        return taskMapper.insertTask(task) > 0;
    }

    @Override
    public Task getTaskById(Integer id) {
        return taskMapper.getTaskById(id);
    }

    @Override
    public List<Task> getAllTasks() {
        return taskMapper.getAllTasks();
    }

    @Override
    public List<Task> getTasksByStatus(String status) {
        return taskMapper.getTasksByStatus(status);
    }

    @Override
    public boolean updateTask(Task task) {
        return taskMapper.updateTask(task) > 0;
    }

    @Override
    public boolean updateTaskStatus(Integer id, String status) {
        return taskMapper.updateTaskStatus(id, status) > 0;
    }

    @Override
    public boolean deleteTask(Integer id) {
        return taskMapper.deleteTask(id) > 0;
    }

    /* ===== 实现分页方法 ===== */
    @Override
    public List<Task> getTasksPaged(int offset, int limit) {
        return taskMapper.getTasksPaged(offset, limit);
    }

    @Override
    public int getTasksCountByStatus(String status) {
        return taskMapper.getTasksCountByStatus(status);
    }
}
