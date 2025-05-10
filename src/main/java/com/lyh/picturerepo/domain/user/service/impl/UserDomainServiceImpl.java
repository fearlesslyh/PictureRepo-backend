package com.lyh.picturerepo.domain.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lyh.picturerepo.domain.user.entity.User;
import com.lyh.picturerepo.domain.user.repository.UserRepository;
import com.lyh.picturerepo.domain.user.service.UserDomainService;
import com.lyh.picturerepo.domain.user.valueObject.UserRoleEnum;
import com.lyh.picturerepo.infrastructure.exception.BusinessException;
import com.lyh.picturerepo.infrastructure.exception.ErrorCode;
import com.lyh.picturerepo.infrastructure.exception.ThrowUtils;
import com.lyh.picturerepo.interfaces.dto.user.UserQuery;
import com.lyh.picturerepo.interfaces.vo.user.LoginUserVO;
import com.lyh.picturerepo.interfaces.vo.user.UserVO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.lyh.picturerepo.domain.user.constant.UserConstant.USER_LOGIN_STATE;
import static com.lyh.picturerepo.infrastructure.exception.ErrorCode.*;

/**
 * @author RAOYAO
 * @description 针对表【user(用户)】的数据库操作Service实现
 * @createDate 2025-04-09 17:27:01
 */
@Service
public class UserDomainServiceImpl implements UserDomainService {
    @Resource
    private UserRepository userRepository;
    // 定义BCryptPasswordEncoder对象，是spring security自带的加密算法，更加安全和通用
    private final static BCryptPasswordEncoder PasswordEncoder = new BCryptPasswordEncoder();

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 2. 检查用户账号是否和数据库中已有的重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        long count = userRepository.getBaseMapper().selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
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
        boolean saveUser = userRepository.save(user);
        // 如果保存失败，则抛出异常
        if (!saveUser) {
            throw new BusinessException(SYSTEM_ERROR, "用户注册失败");
        }
        // 返回用户id
        return user.getId();
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        //2.加密
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", userPassword);
        User user = userRepository.getBaseMapper().selectOne(queryWrapper);
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
     * @param user 用户信息
     * @return 包装好的用户VO
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

    /**
     * 获取当前登录用户信息
     * @param request 请求
     * @return 返回用户信息
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 先判断是否登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User user = (User) userObj;
        if (user == null|| user.getId() == null) {
            throw new BusinessException(NOT_LOGIN_ERROR, "用户未登录");
        }
        // 返回用户信息
        Long userId = user.getId();
        user= userRepository.getById(userId);
        if (user == null) {
            throw new BusinessException(NOT_LOGIN_ERROR, "用户不存在");
        }
        return user;
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 先判断是否登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User user = (User) userObj;
        if (user == null|| user.getId() == null) {
            throw new BusinessException(OPERATION_ERROR, "用户未登录");
        }
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollectionUtil.isEmpty(userList)) {
            return null;
        }
        // this 指的是调用 getUserVOList 方法的 类的实例对象。
        // 它用来引用当前对象的方法
        return userList.stream()
                .map(this::getUserVO)
                .collect(Collectors.toList());
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQuery userQuery) {
        if(userQuery == null) {
            throw new BusinessException(PARAMS_ERROR, "查询条件不能为空");
        }
        Long id = userQuery.getId();
        String userName = userQuery.getUserName();
        String userAccount = userQuery.getUserAccount();
        String userProfile = userQuery.getUserProfile();
        String userRole = userQuery.getUserRole();
        int current = userQuery.getCurrent();
        int pageSize = userQuery.getPageSize();
        String sortField = userQuery.getSortField();
        String sortOrder = userQuery.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjUtil.isNotNull(id), "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.orderBy(StrUtil.isNotBlank(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }


    @Override
    public Boolean removeById(Long id) {
        return userRepository.removeById(id);
    }

    @Override
    public boolean updateById(User user) {
        return userRepository.updateById(user);
    }

    @Override
    public User getById(long id) {
        return userRepository.getById(id);
    }

    @Override
    public Page<User> page(Page<User> userPage, QueryWrapper<User> queryWrapper) {
        return userRepository.page(userPage, queryWrapper);
    }

    @Override
    public List<User> listByIds(Set<Long> userIdSet) {
        return userRepository.listByIds(userIdSet);
    }

    @Override
    public long addUser(User user) {
        // 默认密码 12345678
        final String DEFAULT_PASSWORD = "12345678";
        String encryptPassword = PasswordEncoder.encode(DEFAULT_PASSWORD);
        user.setUserPassword(encryptPassword);
        boolean result = userRepository.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return user.getId();
    }

}




