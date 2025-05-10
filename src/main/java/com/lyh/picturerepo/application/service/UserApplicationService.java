package com.lyh.picturerepo.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lyh.picturerepo.domain.user.entity.User;
import com.lyh.picturerepo.infrastructure.common.DeleteRequest;
import com.lyh.picturerepo.interfaces.dto.user.UserQuery;
import com.lyh.picturerepo.interfaces.dto.user.UserRegister;
import com.lyh.picturerepo.interfaces.vo.user.LoginUserVO;
import com.lyh.picturerepo.interfaces.vo.user.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;

/**
 * @author RAOYAO
 * @description 针对表【user(用户)】的数据库操作Service
 * @createDate 2025-04-09 17:27:01
 */

public interface UserApplicationService{
    /**
     * 用户注册
     *
     * @param userRegister 用户注册信息
     * @return 用户id
     */
    long userRegister(UserRegister userRegister);

    /**
     * 用户登录
     *
     * @param userAccount  用户账号
     * @param userPassword 用户密码
     * @param request      请求
     * @return 登录用户信息
     */

    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取脱敏的已登录用户信息
     *
     * @return
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 获取当前登录用户信息
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);

    UserVO getUserVO(User user);

    List<UserVO> getUserVOList(List<User> userList);

    QueryWrapper<User> getQueryWrapper(UserQuery userQuery);


    User getUserById(long id);

    UserVO getUserVOById(long id);

    boolean deleteUser(DeleteRequest deleteRequest);

    void updateUser(User user);

    Page<UserVO> listUserVOByPage(UserQuery userQueryRequest);

    List<User> listByIds(Set<Long> userIdSet);

    long addUser(User user);

}
