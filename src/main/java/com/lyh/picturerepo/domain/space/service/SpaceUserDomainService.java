package com.lyh.picturerepo.domain.space.service;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lyh.picturerepo.domain.space.entity.SpaceUser;
import com.lyh.picturerepo.interfaces.dto.spaceuser.SpaceUserQueryRequest;

/**
 * @author 李鱼皮
 * @description 针对表【space_user(空间用户关联)】的数据库操作Service
 * @createDate 2025-01-02 20:07:15
 */
public interface SpaceUserDomainService extends IService<SpaceUser> {

    /**
     * 获取查询对象
     *
     * @param spaceUserQueryRequest
     * @return
     */
    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);
}
