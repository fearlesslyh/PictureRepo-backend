package com.lyh.picturerepobackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lyh.picturerepobackend.annotation.AuthorityCheck;
import com.lyh.picturerepobackend.common.BaseResponse;
import com.lyh.picturerepobackend.common.DeleteRequest;
import com.lyh.picturerepobackend.common.ResultUtils;
import com.lyh.picturerepobackend.constant.UserConstant;
import com.lyh.picturerepobackend.exception.BusinessException;
import com.lyh.picturerepobackend.exception.ThrowUtils;
import com.lyh.picturerepobackend.model.dto.user.*;
import com.lyh.picturerepobackend.model.entity.User;
import com.lyh.picturerepobackend.model.vo.LoginUserVO;
import com.lyh.picturerepobackend.model.vo.UserVO;
import com.lyh.picturerepobackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import java.util.List;

import static com.lyh.picturerepobackend.exception.ErrorCode.*;

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
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request);
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

    @AuthorityCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/add")
    public BaseResponse<Long> addUser(@RequestBody UserAdd userAdd) {
        ThrowUtils.throwIf(userAdd == null, PARAMS_ERROR, "用户信息不能为空");
        User user = new User();
        BeanUtils.copyProperties(userAdd, user);
        //默认密码12345678
        final String DEFAULT_PASSWORD = "12345678";
        String encryptPassword = PasswordEncoder.encode(DEFAULT_PASSWORD);
        user.setUserPassword(encryptPassword);
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, OPERATION_ERROR, "用户添加失败");
        return ResultUtils.success(user.getId());
    }

    @AuthorityCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/get")
    public BaseResponse<User> getUserById(long id){
        ThrowUtils.throwIf(id <= 0, PARAMS_ERROR, "用户id不能为空");
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, NOT_FOUND_ERROR, "用户不存在");
        return ResultUtils.success(user);
    }

    @GetMapping("/get/voId")
    public BaseResponse<UserVO> getUserVOById(long id){
        ThrowUtils.throwIf(id <= 0, PARAMS_ERROR, "用户id不能为空");
        BaseResponse<User> userById = getUserById(id);
        User user = userById.getData();
        UserVO userVO = userService.getUserVO(user);
        return ResultUtils.success(userVO);
    }
    // 删除用户
    @PostMapping("/delete")
    @AuthorityCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        //校验非空
        if (deleteRequest==null || deleteRequest.getId()<0){
            throw new BusinessException(PARAMS_ERROR,"用户信息不能为空");
        }
        //直接移除
        boolean result = userService.removeById(deleteRequest.getId());
        return ResultUtils.success(result);
    }
    // 更新用户
    @PostMapping("/update")
    @AuthorityCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdate userUpdate) {
        //校验非空
        if (userUpdate==null || userUpdate.getId()==null){
            throw new BusinessException(PARAMS_ERROR,"用户信息不能为空");
        }
        //赋值
        User user = new User();
        BeanUtils.copyProperties(userUpdate, user);
        boolean result = userService.updateById(user);
        //检测成功与否
        ThrowUtils.throwIf(!result, OPERATION_ERROR, "用户更新失败");
        return ResultUtils.success(true);
    }
    // 分页获取用户的封装列表（管理员）
    @PostMapping("/list/page/userVO")
    @AuthorityCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> getUserListByPage(@RequestBody UserQuery userQuery) {
        //校验非空
        ThrowUtils.throwIf(userQuery == null, PARAMS_ERROR, "用户信息不能为空");
        long current = userQuery.getCurrent();
        long pageSize = userQuery.getPageSize();
        //调用service
        Page<User> page = userService.page(new Page<>(current, pageSize), userService.getQueryWrapper(userQuery));
        Page<UserVO> userVOPage = new Page<>(current,pageSize, page.getTotal());
        List<UserVO> userVOList = userService.getUserVOList(page.getRecords());
        userVOPage.setRecords(userVOList);
        //返回查询结果
        return ResultUtils.success(userVOPage);
    }
}
