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
 * 空间图片标签分析响应
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SpaceTagResponse implements Serializable {
    /**
     * 标签名称
     */
    private String tag;

    /**
     * 使用次数
     */
    private Long count;

    private static final long serialVersionUID = 1L;
}
