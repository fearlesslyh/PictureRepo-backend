package com.lyh.picturerepobackend.controller;

import com.lyh.picturerepobackend.common.BaseResponse;
import com.lyh.picturerepobackend.common.ResultUtils;
import com.lyh.picturerepobackend.exception.ThrowUtils;
import com.lyh.picturerepobackend.model.dto.user.UserLogin;
import com.lyh.picturerepobackend.model.dto.user.UserRegister;
import com.lyh.picturerepobackend.model.entity.User;
import com.lyh.picturerepobackend.model.vo.LoginUserVO;
import com.lyh.picturerepobackend.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import static com.lyh.picturerepobackend.exception.ErrorCode.PARAMS_ERROR;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/9 20:47
 */
@RestController
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

    /**
     * 用户登录
     * @param userLogin
     * @param request
     * @return
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLogin userLogin, HttpServletRequest request) {
        ThrowUtils.throwIf( userLogin == null, PARAMS_ERROR, "用户登录信息不能为空");
        String userAccount = userLogin.getUserAccount();
        String userPassword = userLogin.getUserPassword();
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(loginUserVO);
    }

    /**
     * 获取当前登录用户信息
     * @param request
     * @return
     */
    @GetMapping("/get/loginUser")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        LoginUserVO loginUserVO = userService.getLoginUserVO(loginUser);
        return ResultUtils.success(loginUserVO);
    }

    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, PARAMS_ERROR, "用户登录信息不能为空");
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }
}
