package com.lyh.picturerepo.shared.enums;

import lombok.Getter;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/14 21:39
 */
@Getter
public enum FileUploadBizEnum {
    PICTURE("PICTURE", "picture", new String[]{"image/jpeg", "image/png", "image/gif"}, 2 * 1024 * 1024), // 2MB
    VIDEO("VIDEO", "video", new String[]{"video/mp4", "video/webm"}, 100 * 1024 * 1024), // 100MB
    AUDIO("AUDIO", "audio", new String[]{"audio/mpeg", "audio/wav"}, 10 * 1024 * 1024), // 10MB
    COMMON("COMMON", "common", new String[]{"*"}, 50 * 1024 * 1024); // 50MB

    private final String code;
    private final String pathPrefix;
    private final String[] allowedContentTypes;
    private final long maxFileSize;

    FileUploadBizEnum(String code, String pathPrefix, String[] allowedContentTypes, long maxFileSize) {
        this.code = code;
        this.pathPrefix = pathPrefix;
        this.allowedContentTypes = allowedContentTypes;
        this.maxFileSize = maxFileSize;
    }

    public static FileUploadBizEnum getByCode(String code) {
        for (FileUploadBizEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return COMMON; // 默认使用通用配置
    }
}

