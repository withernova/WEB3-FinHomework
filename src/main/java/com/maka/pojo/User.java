package com.maka.pojo;

import java.io.Serializable;
import lombok.Data;

/**
 * tb_user
 * @author 
 */
@Data
public class User implements Serializable {
    private Integer id;

    /**
     * 长期授权字符串
     */
    private String openId;

    private String password;

    /**
     * 真实姓名
     */
    private String realname;

    /**
     * 手机号为账号，用户名
     */
    private String username;

    /**
     * 手机号，账号
     */
    private String tel;

    /**
     * 姓名
     */
    private String nickname;

    /**
     * 头像地址
     */
    private String avatar;

    private String gender;

    private Boolean worker;

    private static final long serialVersionUID = 1L;
}