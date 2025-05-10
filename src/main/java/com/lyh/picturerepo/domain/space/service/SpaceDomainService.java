package com.lyh.picturerepo.domain.space.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lyh.picturerepo.domain.user.entity.User;
import com.lyh.picturerepo.interfaces.dto.space.SpaceAdd;
import com.lyh.picturerepo.interfaces.dto.space.SpaceQuery;
import com.lyh.picturerepo.domain.space.entity.Space;
import com.lyh.picturerepo.interfaces.vo.space.SpaceVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author RAOYAO
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2025-04-27 21:01:44
*/
public interface SpaceDomainService extends IService<Space> {

    void validSpace(Space space, boolean add);

    void fillSpaceBySpaceLevel(Space space);

    long addSpace(SpaceAdd spaceAdd, User loginUser);

    void checkSpaceAuth(User loginUser, Space space);

    SpaceVO getSpaceVO(Space space, HttpServletRequest request);
    /**
     * 获取空间包装类（分页）
     *
     * @param spacePage
     * @param request
     * @return
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);
    /**
     * 获取查询对象
     *
     * @param spaceQuery
     * @return
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQuery spaceQuery);
}
