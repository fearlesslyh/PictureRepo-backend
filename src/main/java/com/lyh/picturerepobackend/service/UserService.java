package com.lyh.picturerepobackend.service;

import com.lyh.picturerepobackend.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lyh.picturerepobackend.model.vo.LoginUserVO;

import javax.servlet.http.HttpServletRequest;

/**
 * @author RAOYAO
 * @description 针对表【user(用户)】的数据库操作Service
 * @createDate 2025-04-09 17:27:01
 */

public interface UserService extends IService<User> {
    long userRegister(String userAccount, String userPassword, String checkPassword);
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取脱敏的已登录用户信息
     *
     * @return
     */
    LoginUserVO getLoginUserVO(User user);

}
