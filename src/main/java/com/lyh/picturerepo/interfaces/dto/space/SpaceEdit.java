package com.lyh.picturerepo.interfaces.dto.space;

import lombok.Data;

import java.io.Serializable;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/27 21:35
 */
@Data
public class SpaceEdit implements Serializable {
    /**
     * 空间 id
     */
    private Long id;

    /**
     * 空间名称
     */
    private String spaceName;

    private static final long serialVersionUID = 1L;
}
