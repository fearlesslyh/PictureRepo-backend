package com.lyh.picturerepo.domain.picture.valueObject;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/17 15:10
 */
@Getter
public enum PictureReviewStatus {
    REVIEWING("审核中", 0),
    PASS("通过", 1),
    REJECT("拒绝", 2);
    private final String text;
    private final int value;

    PictureReviewStatus(String text, int value) {
        this.text = text;
        this.value = value;
    }

    //根据value获得枚举
    public static PictureReviewStatus getPictureReviewStatus(int value) {
        if (ObjUtil.isEmpty(value)){
            return null;
        }
        for (PictureReviewStatus pictureReviewStatus : PictureReviewStatus.values()) {
            if (pictureReviewStatus.getValue() == value) {
                return pictureReviewStatus;
            }
        }
        return null;
    }
}
