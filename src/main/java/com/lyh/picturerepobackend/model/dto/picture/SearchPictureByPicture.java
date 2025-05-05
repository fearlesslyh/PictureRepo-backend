package com.lyh.picturerepobackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/5/1 0:40
 */
@Data
public class SearchPictureByPicture implements Serializable {
    /**
     * 图片 id
     */
    private Long pictureId;

    private static final long serialVersionUID = 1L;
}
