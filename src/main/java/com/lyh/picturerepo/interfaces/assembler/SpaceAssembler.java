package com.lyh.picturerepo.interfaces.assembler;

import com.lyh.picturerepo.domain.space.entity.Space;
import com.lyh.picturerepo.interfaces.dto.space.SpaceAdd;
import com.lyh.picturerepo.interfaces.dto.space.SpaceEdit;
import com.lyh.picturerepo.interfaces.dto.space.SpaceUpdate;
import org.springframework.beans.BeanUtils;

public class SpaceAssembler {

    public static Space toSpaceEntity(SpaceAdd request) {
        Space space = new Space();
        BeanUtils.copyProperties(request, space);
        return space;
    }

    public static Space toSpaceEntity(SpaceUpdate request) {
        Space space = new Space();
        BeanUtils.copyProperties(request, space);
        return space;
    }

    public static Space toSpaceEntity(SpaceEdit request) {
        Space space = new Space();
        BeanUtils.copyProperties(request, space);
        return space;
    }
}
