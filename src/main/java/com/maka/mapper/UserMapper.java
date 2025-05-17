package com.maka.mapper;

import com.maka.pojo.User;
import com.maka.query.SimpleUser;
import com.maka.query.UserInfo;
import com.maka.vo.RescuingUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yang
 */
@Mapper
public interface UserMapper {
    int deleteByPrimaryKey(Integer id);

    int insert(User user);

    int insertSelective(@Param("user") SimpleUser user);

    User selectByPrimaryKey(Integer id);

    User selectByPhone(String tel);

    int updateByPrimaryKeySelective(User record);

    int updateByPrimaryKey(User record);


    List<UserInfo> selectUserByPage(@Param("from") int from, @Param("size") int size);

    int getTotalUsersNums();

    Integer getRescueNumByUserId(int userId);

    List<UserInfo> selectAllUser();

    List<UserInfo> selectPageUserBycondition(@Param("from") int from, @Param("size") int size,@Param("name")String name,@Param("gender") String gender,@Param("phone") String phone);

    List<RescuingUser> getUsersToRescueMan(int oldMan);
}