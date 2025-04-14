package com.lyh.picturerepobackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/14 19:54
 */
@Data
public class PictureUpload implements Serializable {
    /**
     * 图片id
     */
    private  Long id;
    private static final long serialVersionUID = 1L;
}
