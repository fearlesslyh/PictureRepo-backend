package com.lyh.picturerepobackend.controller;

import com.lyh.picturerepobackend.common.BaseResponse;
import com.lyh.picturerepobackend.common.ResultUtils;
import com.lyh.picturerepobackend.exception.ThrowUtils;
import com.lyh.picturerepobackend.model.dto.user.UserRegister;
import com.lyh.picturerepobackend.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.Resource;

import static com.lyh.picturerepobackend.exception.ErrorCode.PARAMS_ERROR;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/9 20:47
 */
@RequestMapping("/user")
public class UserController {
    @Resource
    private UserService userService;

    /**
     * 用户注册
     * @param userRegister 注册类dto
     * @return 用户id，以及注册成功的消息
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegister userRegister) {
        // 判断用户注册信息是否为空
        ThrowUtils.throwIf(userRegister == null, PARAMS_ERROR, "用户注册信息不能为空");
        // 获取用户账号、密码和确认密码
        String userAccount = userRegister.getUserAccount();
        String userPassword = userRegister.getUserPassword();
        String checkPassword = userRegister.getCheckPassword();
        // 调用userService的userRegister方法进行注册
        long result = userService.userRegister(userAccount, userPassword, checkPassword);

        // 返回注册成功的消息
        return ResultUtils.success(result);
    }
}
