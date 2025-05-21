package com.maka.mapper;

import com.maka.pojo.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务数据访问层
 */
@Mapper
public interface TaskMapper {

    /** 新增任务 */
    int insertTask(Task task);

    /** 根据主键查询任务 */
    Task getTaskById(Integer id);

    /** 查询所有任务 */
    List<Task> getAllTasks();

    /** 根据状态查询任务 */
    List<Task> getTasksByStatus(String status);

    /** 更新任务的全部信息 */
    int updateTask(Task task);

    /** 仅更新任务状态 */
    int updateTaskStatus(@Param("id") Integer id,
                         @Param("status") String status);

    /** 删除任务 */
    int deleteTask(Integer id);

    List<Task> getTasksPaged(@Param("offset") int offset,
                             @Param("limit")  int limit);

    int getTasksCountByStatus(@Param("status") String status);
}
