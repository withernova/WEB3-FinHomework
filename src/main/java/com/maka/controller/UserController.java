package com.maka.controller;

import com.maka.component.MessageResponse;
import com.maka.query.PageRequest;
import com.maka.query.PageResponse;
import com.maka.query.SimpleUser;
import com.maka.query.UserInfo;
import com.maka.service.UserService;
import com.maka.service.impl.UserServiceImpl;
import com.mysql.cj.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author yang
 */
@CrossOrigin
@RestController
public class UserController {

    private Logger logger = LoggerFactory.getLogger(UserController.class);

    private UserService userService;

    @Autowired
    public void setUserService(UserServiceImpl userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public MessageResponse registerVolunteer(@Validated SimpleUser simpleUser) {
        logger.info("{}", simpleUser);

        return userService.registerVolunteer(simpleUser);
    }


    @RequestMapping("/getPageUser")
    public PageResponse selectUserByPage(PageRequest pageRequest) {
        List<UserInfo> users = userService.selectUserByPage(pageRequest.getCurrentPage(), pageRequest.getPageSize());
        int totalNum = userService.getTotalUsersNums();
        pageRequest.setData(users);
        pageRequest.setPageTotalNum(totalNum / pageRequest.getPageSize() + 1);
        pageRequest.setTotalNum(totalNum);
        logger.info(users.toString());
        return new PageResponse(HttpStatus.OK.value(), "查询成功", totalNum, users);
    }

    @RequestMapping("/getUser")
    public PageResponse selectAllUser() {
        List<UserInfo> users = userService.selectAllUser();
        int totalNum =users.size();
        return new PageResponse(HttpStatus.OK.value(), "查询成功", totalNum, users);
    }


    @RequestMapping("getPageUserByCondition")
    public PageResponse getPageUserByCondition(PageRequest pageRequest,String name,String gender,String phone) {
        if(StringUtils.isEmptyOrWhitespaceOnly(gender)){
            gender = null;
        }
        if(StringUtils.isEmptyOrWhitespaceOnly(phone)){
            phone = null;
        }
        if(StringUtils.isEmptyOrWhitespaceOnly(name)){
            name = null;
        }
        if(name==null&&phone==null&&gender==null){
            return selectAllUser();
        }
        logger.info("name  = {}",name);
        logger.info("gender = {}",gender);
        logger.info("phone= {}",phone);
        List<UserInfo> users = userService.getPageUserByCondition(pageRequest.getCurrentPage(), pageRequest.getPageSize(),name,gender,phone);
        int totalNum = users.size();
        return new PageResponse(HttpStatus.OK.value(), "查询成功", totalNum, users);
    }
}
