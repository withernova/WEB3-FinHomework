package com.maka.mapper;

import com.maka.pojo.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 走失老人任务数据访问接口（XML版本）
 */
@Mapper
public interface TaskMapper {
    int insert(Task task);

    int update(Task task);

    Task selectById(Integer id);

    List<Task> selectAll();

    List<Task> selectByStatus(String status);

    int deleteById(Integer id);
    
    /**
     * 根据ID列表批量查询任务
     */
    List<Task> selectByIdList(@Param("idList") List<Integer> idList);
}