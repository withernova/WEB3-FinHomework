package com.maka.pojo;

import java.io.Serializable;
import java.util.List;

import lombok.Data;


// 示例: 新的Rescuer类
@Data
public class Rescuer implements Serializable {
    private String uuid;
    private String name;
    private String status;
    private String location;
    private List<Integer> taskIds;
    
    // Getters and Setters
    public String getUuid() {
        return uuid;
    }
    
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getLocation() {
        return location;
    }
    
    public void setLocation(String location) {
        this.location = location;
    }
    
    public List<Integer> getTaskIds() {
        return taskIds;
    }
    
    public void setTaskIds(List<Integer> taskIds) {
        this.taskIds = taskIds;
    }
}
