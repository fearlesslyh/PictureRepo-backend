package com.lyh.picturerepo.interfaces.assembler;

import com.lyh.picturerepo.domain.user.entity.User;
import com.lyh.picturerepo.interfaces.dto.user.UserAdd;
import com.lyh.picturerepo.interfaces.dto.user.UserUpdate;
import org.springframework.beans.BeanUtils;

/**
 * 用户对象转换
 */
public class UserAssembler {

    public static User toUserEntity(UserAdd request) {
        User user = new User();
        BeanUtils.copyProperties(request, user);
        return user;
    }

    public static User toUserEntity(UserUpdate request) {
        User user = new User();
        BeanUtils.copyProperties(request, user);
        return user;
    }
}
