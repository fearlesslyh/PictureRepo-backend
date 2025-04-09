package com.lyh.picturerepobackend.service;

import com.lyh.picturerepobackend.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @author RAOYAO
 * @description 针对表【user(用户)】的数据库操作Service
 * @createDate 2025-04-09 17:27:01
 */

public interface UserService extends IService<User> {
    long userRegister(String userAccount, String userPassword, String checkPassword);
}
