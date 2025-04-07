package com.lyh.picturerepobackend.common;

import java.io.Serializable;

import com.lyh.picturerepobackend.exception.ErrorCode;
import lombok.Data;

/**
 * 响应包装类
 * 一般情况下，每个后端接口都要返回调用码、数据、调用信息等，前端可以根据这些信息进行相应的处理。
 * 我们可以封装统一的响应结果类，便于前端统一获取这些信息。
 *
 * @param <T>
 */
@Data
public class BaseResponse<T> implements Serializable {

    // 返回码
    private int code;

    // 返回数据
    private T data;

    // 返回信息
    private String message;

    // 构造函数，传入返回码、返回数据和返回信息
    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    // 构造函数，传入返回码和返回数据，返回信息为空字符串
    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    // 构造函数，传入错误码，返回码为错误码的code，返回数据为null，返回信息为错误码的message
    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}
