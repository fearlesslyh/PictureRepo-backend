package com.lyh.picturerepobackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lyh.picturerepobackend.model.entity.Space;

/**
* @author RAOYAO
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2025-04-27 21:01:44
*/
public interface SpaceService extends IService<Space> {

    void validSpace(Space space, boolean add);

    void fillSpaceBySpaceLevel(Space space);

}
