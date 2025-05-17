package com.maka.service;


import com.maka.component.MessageResponse;
import com.maka.pojo.User;
import com.maka.query.SimpleUser;
import com.maka.query.UserInfo;
import com.maka.vo.RescuingUser;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author yang
 */
@Service
public interface UserService {

    String DEFAULT_PASSWORD = "123456";
    MessageResponse registerVolunteer(SimpleUser user);

    int getTotalUsersNums();

    List<UserInfo> selectUserByPage(int currentPage, int pageSize);
    List<UserInfo> selectAllUser();

    List<UserInfo> getPageUserByCondition(int currentPage, int pageSize, String name, String gender, String phone);

    List<RescuingUser> getUsersToRescueMan(int oldMan);
}
