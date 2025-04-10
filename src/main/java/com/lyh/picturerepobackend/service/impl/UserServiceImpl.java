package com.lyh.picturerepobackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lyh.picturerepobackend.exception.BusinessException;
import com.lyh.picturerepobackend.exception.ErrorCode;
import com.lyh.picturerepobackend.model.entity.User;
import com.lyh.picturerepobackend.model.enums.UserRoleEnum;
import com.lyh.picturerepobackend.model.vo.LoginUserVO;
import com.lyh.picturerepobackend.service.UserService;
import com.lyh.picturerepobackend.mapper.UserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

import static com.lyh.picturerepobackend.constant.UserConstant.USER_LOGIN_STATE;
import static com.lyh.picturerepobackend.exception.ErrorCode.PARAMS_ERROR;
import static com.lyh.picturerepobackend.exception.ErrorCode.SYSTEM_ERROR;

/**
 * @author RAOYAO
 * @description 针对表【user(用户)】的数据库操作Service实现
 * @createDate 2025-04-09 17:27:01
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    // 定义BCryptPasswordEncoder对象，是spring security自带的加密算法，更加安全和通用
    private final static BCryptPasswordEncoder PasswordEncoder = new BCryptPasswordEncoder();

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验
        // 校验用户账号、密码、校验密码是否为空
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(PARAMS_ERROR, "用户账号、密码、校验密码不能为空");
        }
        // 校验用户账号长度是否小于4位
        if (userAccount.length() < 4) {
            throw new BusinessException(PARAMS_ERROR, "用户账号不能少于4位");
        }
        // 校验用户密码长度是否小于8位
        if (userPassword.length() < 8) {
            throw new BusinessException(PARAMS_ERROR, "用户密码不能少于8位");
        }
        // 校验两次输入的密码是否一致
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(PARAMS_ERROR, "两次输入的密码不一致");
        }
        //2.检查账号是否重复
        // 根据用户账号查询用户信息
        User currentUser = this.lambdaQuery().eq(User::getUserAccount, userAccount).one();
        // 如果用户信息不为空，则抛出异常
        if (currentUser != null) {
            throw new BusinessException(PARAMS_ERROR, "用户账号已存在");
        }
        //3.加密密码
        // 使用BCryptPasswordEncoder加密密码
        String encryptPassword = PasswordEncoder.encode(userPassword);
        //4.向数据库插入数据
        // 创建用户对象
        User user = new User();
        // 设置用户账号
        user.setUserAccount(userAccount);
        // 设置用户密码
        user.setUserPassword(encryptPassword);
        // 设置用户名
        user.setUserName(userAccount);
        // 设置用户角色
        user.setUserRole(UserRoleEnum.USER.getValue());
        // 保存用户信息
        boolean saveUser = this.save(user);
        // 如果保存失败，则抛出异常
        if (!saveUser) {
            throw new BusinessException(SYSTEM_ERROR, "用户注册失败");
        }
        // 返回用户id
        return user.getId();
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(PARAMS_ERROR, "用户账号、密码不能为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        //2.加密
        // 根据用户账号查询用户信息
        User user = this.lambdaQuery().eq(User::getUserAccount, userAccount).one();
        // 如果用户信息为空，则抛出异常
        if (user == null) {
            throw new BusinessException(PARAMS_ERROR, " 用户不存在");
        }
        // 如果密码不正确，则抛出异常
        if (!PasswordEncoder.matches(userPassword, user.getUserPassword())) {
            throw new BusinessException(PARAMS_ERROR, "密码错误");
        }

        //3.记录用户的登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        //4.返回用户信息
        return this.getLoginUserVO(user);
    }

    /**
     * 把登录的用户信息包装后再返回，User类要转换成Vo
     * @param user
     * @return
     */
    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

}




