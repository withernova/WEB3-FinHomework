package com.maka.pojo;

import java.io.Serializable;
import java.util.List;

import lombok.Data;


// 示例: 新的Rescuer类
@Data
public class Rescuer implements Serializable {
    private String uuid;
    private String name;
    private String status; // available, visitor
    private String location;
    private String skillsDescription; // 用户描述的技能
    
    private List<String> skill_tags;   // AI生成的技能标签
    private List<Integer> taskIds;
    @Override
    public String toString() {
        return "Rescuer{" +
                "uuid='" + uuid + '\'' +
                ", name='" + name + '\'' +
                ", status='" + status + '\'' +
                ", location='" + location + '\'' +
                ", skillsDescription='" + skillsDescription + '\'' +
                ", skillTags=" + skill_tags +
                ", taskIds=" + taskIds +
                '}';
    }
}