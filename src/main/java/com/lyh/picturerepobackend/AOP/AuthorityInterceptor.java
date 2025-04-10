package com.lyh.picturerepobackend.AOP;

import com.lyh.picturerepobackend.annotation.AuthorityCheck;
import com.lyh.picturerepobackend.exception.BusinessException;
import com.lyh.picturerepobackend.exception.ErrorCode;
import com.lyh.picturerepobackend.model.entity.User;
import com.lyh.picturerepobackend.service.UserService;
import com.lyh.picturerepobackend.model.enums.UserRoleEnum;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import javax.servlet.http.HttpServletRequest;
import javax.annotation.Resource;

/**
 * 权限拦截器
 *
 * @author <a href="https://github.com/fearlesslyh">梁懿豪</a>
 * @version 1.1
 * @date 2025/4/11 10:00
 */
@Aspect
@Component
public class AuthorityInterceptor {

    @Resource
    private UserService userService;

    /**
     * 环绕通知：进行权限校验
     *
     * @param joinPoint   连接点
     * @param authorityCheck 权限校验注解
     * @return 方法执行结果
     * @throws Throwable 异常
     */
    @Around("@annotation(authorityCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthorityCheck authorityCheck) throws Throwable {

        // 1. 获取权限要求
        String mustRole = authorityCheck.mustHaveRole();
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);

        // 2. 无需权限，直接放行
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }

        // 3. 获取当前登录用户
        HttpServletRequest request = getRequest();
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR); // 用户未登录
        }

        // 4. 校验用户权限
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        if (!hasPermission(userRoleEnum, mustRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR); // 权限不足
        }

        // 5. 权限校验通过，放行
        return joinPoint.proceed();
    }

    /**
     * 检查用户是否拥有指定权限
     *
     * @param userRoleEnum   用户角色
     * @param mustRoleEnum 需要的角色
     * @return 是否有权限
     */
    private boolean hasPermission(UserRoleEnum userRoleEnum, UserRoleEnum mustRoleEnum) {
        if (userRoleEnum == null) {
            return false;
        }
        // 管理员拥有所有权限
        if (UserRoleEnum.ADMIN.equals(userRoleEnum)) {
            return true;
        }
        // 只有角色匹配时才有权限
        return userRoleEnum.equals(mustRoleEnum);
    }

    /**
     * 获取 HttpServletRequest
     *
     * @return HttpServletRequest
     */
    private HttpServletRequest getRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        if (requestAttributes == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "RequestAttributes is null");
        }
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        if (request == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "HttpServletRequest is null");
        }
        return request;
    }
}

