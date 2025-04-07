package com.lyh.picturerepobackend.exception;

import com.lyh.picturerepobackend.common.BaseResponse;
import com.lyh.picturerepobackend.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 *
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
}
