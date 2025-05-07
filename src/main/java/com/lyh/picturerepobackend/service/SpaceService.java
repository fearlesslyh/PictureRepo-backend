package com.lyh.picturerepobackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lyh.picturerepobackend.model.dto.space.SpaceAdd;
import com.lyh.picturerepobackend.model.dto.space.SpaceQuery;
import com.lyh.picturerepobackend.model.entity.Space;
import com.lyh.picturerepobackend.model.entity.User;
import com.lyh.picturerepobackend.model.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author RAOYAO
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2025-04-27 21:01:44
*/
public interface SpaceService extends IService<Space> {

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
