package com.lyh.picturerepobackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/10 22:09
 */
@Data
public class UserAdd implements Serializable {
    /**
     * 用户名
     */
    private String username;
    /**
     * 用户账户
     */
    private String userAccount;
    /**
     * 头像
     */
    private String userAvatar;
    /**
     * 简历
     */
    private String userProfile;
    /**
     * 角色：admin/user
     */
    private String userRole;
    private static final long serialVersionUID = 1L;
}
