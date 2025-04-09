package com.lyh.picturerepobackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lyh.picturerepobackend.model.entity.User;
import com.lyh.picturerepobackend.service.UserService;
import com.lyh.picturerepobackend.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
* @author RAOYAO
* @description 针对表【user(用户)】的数据库操作Service实现
* @createDate 2025-04-09 17:27:01
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

}




