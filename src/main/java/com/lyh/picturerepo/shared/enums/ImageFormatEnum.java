package com.lyh.picturerepo.shared.enums;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/14 21:46
 */

import lombok.Getter;

/**
 * 图片格式枚举
 * 定义支持的图片格式，用于文件上传时的格式校验
 */
@Getter
public enum ImageFormatEnum {
    JPEG("jpeg", "image/jpeg"),
    JPG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    GIF("gif", "image/gif"),
    WEBP("webp", "image/webp");

    private final String extension;      // 文件后缀名，例如：jpeg, jpg, png
    private final String contentType;    // Content-Type，例如：image/jpeg, image/png

    // 构造函数
    ImageFormatEnum(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    // 获取文件后缀名
    public String getExtension() {
        return extension;
    }

    // 获取 Content-Type
    public String getContentType() {
        return contentType;
    }

    /**
     * 根据文件后缀名获取枚举对象
     *
     * @param extension 文件后缀名
     * @return ImageFormatEnum 枚举对象，如果找不到，返回 null
     */
    public static ImageFormatEnum getByExtension(String extension) {
        for (ImageFormatEnum value : values()) {
            if (value.getExtension().equalsIgnoreCase(extension)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 根据 Content-Type 获取枚举对象
     *
     * @param contentType Content-Type
     * @return ImageFormatEnum 枚举对象，如果找不到，返回 null
     */
    public static ImageFormatEnum getByContentType(String contentType) {
        for (ImageFormatEnum value : values()) {
            if (value.getContentType().equalsIgnoreCase(contentType)) {
                return value;
            }
        }
        return null;
    }
}
