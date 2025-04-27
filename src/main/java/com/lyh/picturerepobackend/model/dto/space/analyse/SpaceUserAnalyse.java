package com.lyh.picturerepobackend.model.dto.space.analyse;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/27 21:46
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SpaceUserAnalyse extends SpaceAnalyse{
    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 时间维度：day / week / month
     */
    private String timeDimension;
}
