package com.lyh.picturerepobackend.model.dto.space;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/27 21:41
 */
@Data
@AllArgsConstructor
public class SpaceLevel {
    /**
     * 值
     */
    private int value;

    /**
     * 中文
     */
    private String text;

    /**
     * 最大数量
     */
    private long maxCount;

    /**
     * 最大容量
     */
    private long maxSize;
}
