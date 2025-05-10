package com.lyh.picturerepo.interfaces.vo.space.analyse;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/27 21:51
 */

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 空间图片分类分析响应
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SpaceCategoryResponse implements Serializable {
    /**
     * 图片分类
     */
    private String category;

    /**
     * 图片数量
     */
    private Long count;

    /**
     * 分类图片总大小
     */
    private Long totalSize;

    private static final long serialVersionUID = 1L;
}
