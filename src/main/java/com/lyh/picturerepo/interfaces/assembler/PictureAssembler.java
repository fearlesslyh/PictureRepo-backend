
package com.lyh.picturerepo.interfaces.assembler;

import cn.hutool.json.JSONUtil;
import com.lyh.picturerepo.domain.picture.entity.Picture;
import com.lyh.picturerepo.interfaces.dto.picture.PictureEdit;
import com.lyh.picturerepo.interfaces.dto.picture.PictureUpdate;
import org.springframework.beans.BeanUtils;

public class PictureAssembler {

    public static Picture toPictureEntity(PictureEdit request) {
        Picture picture = new Picture();
        BeanUtils.copyProperties(request, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(request.getTags()));
        return picture;
    }

    public static Picture toPictureEntity(PictureUpdate request) {
        Picture picture = new Picture();
        BeanUtils.copyProperties(request, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(request.getTags()));
        return picture;
    }
}
