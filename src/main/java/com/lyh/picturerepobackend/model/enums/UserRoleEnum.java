package com.lyh.picturerepobackend.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/9 17:33
 */
@Getter
public enum UserRoleEnum {
    USER("用户", "user"),
    ADMIN("管理员", "admin");
    private final String text;
    private final String value;

    UserRoleEnum(String text, String value) {
        this.value = value;
        this.text = text;
    }

    /**
     * 根据value 获得枚举。
     *
     * @Param value
     * @Return
     */
    public static UserRoleEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (UserRoleEnum userRoleEnum : UserRoleEnum.values()) {
            if (userRoleEnum.value.equals(value)) {
                return userRoleEnum;
            }
        }
        return null;
    }
}
