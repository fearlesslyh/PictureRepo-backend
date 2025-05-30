package com.lyh.picturerepo.infrastructure.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.lyh.picturerepo.infrastructure.common.BaseResponse;
import com.lyh.picturerepo.infrastructure.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 为了防止意料之外的异常，利用 AOP 切面全局对业务异常和 RuntimeException 进行捕获：
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 处理BusinessException异常
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        // 记录异常信息
        log.error("BusinessException", e);
        // 返回错误信息
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    // 处理RuntimeException异常
    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        // 记录异常信息
        log.error("RuntimeException", e);
        // 返回错误信息
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
    @ExceptionHandler(NotLoginException.class)
    public BaseResponse<?> notLoginException(NotLoginException e) {
        log.error("NotLoginException", e);
        return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR,"未登录");
    }

    @ExceptionHandler(NotPermissionException.class)
    public BaseResponse<?> notPermissionExceptionHandler(NotPermissionException e) {
        log.error("NotPermissionException", e);
        return ResultUtils.error(ErrorCode.NO_AUTH_ERROR,"没有权限");
    }

}
