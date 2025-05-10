package com.lyh.picturerepo.interfaces.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/5/5 18:00
 */
@Data
public class SearchPictureByColor implements Serializable {
    /**
     * 图片主色调
     */
    private String picColor;

    /**
     * 空间 id
     */
    private Long spaceId;

    private static final long serialVersionUID = 1L;

}
