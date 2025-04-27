package com.lyh.picturerepobackend.model.vo.space.analyse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/27 21:52
 */

/**
 * 空间用户上传行为分析响应
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SpaceUserResponse implements Serializable {

    /**
     * 时间区间
     */
    private String period;

    /**
     * 上传数量
     */
    private Long count;

    private static final long serialVersionUID = 1L;
}
