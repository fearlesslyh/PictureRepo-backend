package com.lyh.picturerepo.interfaces.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lyh.picturerepo.application.service.UserApplicationService;
import com.lyh.picturerepo.domain.user.constant.UserConstant;
import com.lyh.picturerepo.domain.user.entity.User;
import com.lyh.picturerepo.infrastructure.annotation.AuthorityCheck;
import com.lyh.picturerepo.infrastructure.common.BaseResponse;
import com.lyh.picturerepo.infrastructure.common.DeleteRequest;
import com.lyh.picturerepo.infrastructure.common.ResultUtils;
import com.lyh.picturerepo.infrastructure.exception.BusinessException;
import com.lyh.picturerepo.infrastructure.exception.ThrowUtils;
import com.lyh.picturerepo.interfaces.assembler.UserAssembler;
import com.lyh.picturerepo.interfaces.dto.user.*;
import com.lyh.picturerepo.interfaces.vo.user.LoginUserVO;
import com.lyh.picturerepo.interfaces.vo.user.UserVO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import static com.lyh.picturerepo.infrastructure.exception.ErrorCode.NOT_FOUND_ERROR;
import static com.lyh.picturerepo.infrastructure.exception.ErrorCode.PARAMS_ERROR;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/9 20:47
 */
@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    private UserApplicationService userApplicationService;

    private final static BCryptPasswordEncoder PasswordEncoder = new BCryptPasswordEncoder();

    /**
     * 用户注册
     *
     * @param userRegister 注册类dto
     * @return 用户id，以及注册成功的消息
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegister userRegister) {
        // 判断用户注册信息是否为空
        ThrowUtils.throwIf(userRegister == null, PARAMS_ERROR, "用户注册信息不能为空");
        // 调用userService的userRegister方法进行注册
        long result = userApplicationService.userRegister(userRegister);
        // 返回注册成功的消息
        return ResultUtils.success(result);
    }

    /**
     * 用户登录
     *
     * @param userLogin
     * @param request
     * @return
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLogin userLogin, HttpServletRequest request) {
        ThrowUtils.throwIf(userLogin == null, PARAMS_ERROR, "用户登录信息不能为空");
        String userAccount = userLogin.getUserAccount();
        String userPassword = userLogin.getUserPassword();
        LoginUserVO loginUserVO = userApplicationService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(loginUserVO);
    }

    /**
     * 获取当前登录用户信息
     *
     * @param request
     * @return
     */
    @GetMapping("/get/loginUser")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User loginUser = userApplicationService.getLoginUser(request);
        LoginUserVO loginUserVO = userApplicationService.getLoginUserVO(loginUser);
        return ResultUtils.success(loginUserVO);
    }

    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, PARAMS_ERROR, "用户登录信息不能为空");
        boolean result = userApplicationService.userLogout(request);
        return ResultUtils.success(result);
    }

    @AuthorityCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/add")
    public BaseResponse<Long> addUser(@RequestBody UserAdd userAdd) {
        ThrowUtils.throwIf(userAdd == null, PARAMS_ERROR, "用户信息不能为空");
        User userEntity = UserAssembler.toUserEntity(userAdd);
        return ResultUtils.success(userApplicationService.addUser(userEntity));
    }

    @AuthorityCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/get")
    public BaseResponse<User> getUserById(Long id) {
        ThrowUtils.throwIf(id <= 0, PARAMS_ERROR, "用户id不能为空");
        User user = userApplicationService.getUserById(id);
        ThrowUtils.throwIf(user == null, NOT_FOUND_ERROR, "用户不存在");
        return ResultUtils.success(user);
    }

    @GetMapping("/get/voId")
    public BaseResponse<UserVO> getUserVOById(Long id) {
        ThrowUtils.throwIf(id <= 0, PARAMS_ERROR, "用户id不能为空");
        BaseResponse<User> userById = getUserById(id);
        User user = userById.getData();
        UserVO userVO = userApplicationService.getUserVO(user);
        return ResultUtils.success(userVO);
    }

    // 删除用户
    @PostMapping("/delete")
    @AuthorityCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        //校验非空
        if (deleteRequest == null || deleteRequest.getId() < 0) {
            throw new BusinessException(PARAMS_ERROR, "用户信息不能为空");
        }
        //直接移除
        boolean result = userApplicationService.deleteUser(deleteRequest);
        return ResultUtils.success(result);
    }

    // 更新用户
    @PostMapping("/update")
    @AuthorityCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdate userUpdate) {
        //校验非空
        if (userUpdate == null || userUpdate.getId() == null) {
            throw new BusinessException(PARAMS_ERROR, "用户信息不能为空");
        }
        //赋值
        User userEntity = UserAssembler.toUserEntity(userUpdate);
        userApplicationService.updateUser(userEntity);
        //检测成功与否
        return ResultUtils.success(true);
    }

    // 分页获取用户的封装列表（管理员）
    @PostMapping("/list/page/userVO")
    @AuthorityCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> getUserListByPage(@RequestBody UserQuery userQuery) {
        //校验非空
        ThrowUtils.throwIf(userQuery == null, PARAMS_ERROR, "用户信息不能为空");
        Page<UserVO> userVOPage = userApplicationService.listUserVOByPage(userQuery);
        //返回查询结果
        return ResultUtils.success(userVOPage);
    }

}
