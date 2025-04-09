package com.lyh.picturerepobackend.model.dto.user;

import java.io.Serializable;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/9 22:05
 */
public class UserLogin implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 账号
     */
    private String userAccount;
    /**
     * 密码
     */
    private String userPassword;
}
