package com.maka.service.impl;


import com.maka.component.MessageResponse;
import com.maka.mapper.UserMapper;
import com.maka.pojo.User;
import com.maka.query.SimpleUser;
import com.maka.query.UserInfo;
import com.maka.service.UserService;
import com.maka.vo.RescuingUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author admin
 */
@Service
public class UserServiceImpl implements UserService {


    private UserMapper userMapper;
    private Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);
    private JdbcTemplate jdbcTemplate;
    @Autowired
    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Autowired
    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    @Override
    public List<RescuingUser> getUsersToRescueMan(int oldMan) {
        return userMapper.getUsersToRescueMan(oldMan);
    }

    @Override
    public MessageResponse registerVolunteer(SimpleUser user) {
        User result = userMapper.selectByPhone(user.getUserPhone());
        if(null!=result){
            return MessageResponse.userError("该手机号已经注册,请更换手机号");
        }
        return userMapper.insertSelective(user)>0?MessageResponse.success("注册成功"):MessageResponse.systemError("出错！请稍后再试");
    }

    @Override
    public int getTotalUsersNums() {
        return userMapper.getTotalUsersNums();
    }

    @Override
        public List<UserInfo> selectUserByPage(int currentPage, int pageSize) {
        List<UserInfo> users = userMapper.selectUserByPage((currentPage - 1) * pageSize, pageSize);
        for (UserInfo user : users) {
            transfer(user);
            user.setRescueNum(userMapper.getRescueNumByUserId(user.getUserId()));
        }
        return users;
    }

    @Override
    public List<UserInfo> getPageUserByCondition(int currentPage, int pageSize, String name, String gender, String phone) {
        List<UserInfo> users = userMapper.selectPageUserBycondition((currentPage - 1) * pageSize, pageSize, name, gender, phone);
        for (UserInfo user : users) {
            transfer(user);
            user.setRescueNum(userMapper.getRescueNumByUserId(user.getUserId()));
        }
        return users;
    }

    @Override
    public List<UserInfo> selectAllUser() {
        List<UserInfo> users = userMapper.selectAllUser();
        for (UserInfo user : users) {
            user.setRescueNum(userMapper.getRescueNumByUserId(user.getUserId()));
        }
        return users;
    }




    private void transfer(UserInfo user){
        //替换手机号
        StringBuilder tempPhone = new StringBuilder(user.getPhone());
        StringBuilder replace = tempPhone.replace(3, 7, "****");
        user.setPhone( replace.toString());
        //替换密码
        char[] pwd = new char[user.getPassword().length()];
        Arrays.fill(pwd, '*');
        user.setPassword(new String(pwd));

    }

}
