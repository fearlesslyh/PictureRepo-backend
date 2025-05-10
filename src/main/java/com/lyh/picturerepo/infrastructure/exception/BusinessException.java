package com.lyh.picturerepo.infrastructure.exception;


import lombok.Getter;

/**
 * 自定义异常类
 * 一般不建议直接抛出 Java 内置的 RuntimeException，而是自定义一个业务异常，和内置的异常类区分开，便于定制化输出错误信息
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误码
     */
    private final int code;

// 构造函数，用于创建BusinessException对象
    public BusinessException(int code, String message) {
        // 调用父类的构造函数，传入message参数
        super(message);
        // 将code参数赋值给成员变量
        this.code = code;
    }

// 构造函数，传入一个ErrorCode对象
    public BusinessException(ErrorCode errorCode) {
        // 调用父类的构造函数，传入ErrorCode对象的message属性
        super(errorCode.getMessage());
        // 将ErrorCode对象的code属性赋值给当前对象的code属性
        this.code = errorCode.getCode();
    }

// 构造函数，用于创建BusinessException对象
    public BusinessException(ErrorCode errorCode, String message) {
        // 调用父类构造函数，传入message参数
        super(message);
        // 将errorCode的code赋值给当前对象的code属性
        this.code = errorCode.getCode();
    }

}
