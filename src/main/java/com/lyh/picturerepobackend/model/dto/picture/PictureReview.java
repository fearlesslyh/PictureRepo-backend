package com.lyh.picturerepobackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/17 16:54
 */
@Data
public class PictureReview implements Serializable {
    /**
     * id
     */
    private Long id;
    /**
     * 审核信息
     */
    private String reviewMessage;
    /**
     * 审核状态: 0:待审核 1:审核通过 2:审核不通过
     */
    private Integer reviewStatus;

    private static final long serialVersionUID = 1L;
}
