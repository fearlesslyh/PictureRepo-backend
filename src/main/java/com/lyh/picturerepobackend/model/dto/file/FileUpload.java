package com.lyh.picturerepobackend.model.dto.file;

import lombok.Data;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/14 20:45
 */
@Data
public class FileUpload {
    /**
     * 图片地址
     */
    private String url;

    /**
     * 图片名称
     */
    private String picName;

    /**
     * 文件体积
     */
    private Long picSize;

    /**
     * 图片宽度
     */
    private int picWidth;

    /**
     * 图片高度
     */
    private int picHeight;

    /**
     * 图片宽高比
     */
    private Double picScale;

    /**
     * 图片格式
     */
    private String picFormat;
    /**
     * 缩略图 url
     */
    private String thumbnailUrl;
    /**
     * 图片主色调
     */
    private String picColor;


}
