package com.lyh.picturerepobackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lyh.picturerepobackend.model.dto.picture.*;
import com.lyh.picturerepobackend.model.entity.Picture;
import com.lyh.picturerepobackend.model.entity.User;
import com.lyh.picturerepobackend.model.vo.PictureVO;

import javax.servlet.http.HttpServletRequest;

/**
 * @author RAOYAO
 * @description 针对表【picture(图片)】的数据库操作Service
 * @createDate 2025-04-14 19:49:22
 */
public interface PictureService extends IService<Picture> {
    /**
     * 上传图片
     *
     * @param inputSource
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    PictureVO uploadPicture(Object inputSource, PictureUpload pictureUploadRequest, User loginUser);

    QueryWrapper<Picture> getQueryWrapper(PictureQuery pictureQueryRequest);

    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    void validPicture(Picture picture);

    void reviewPicture(PictureReview pictureReview, User loginUser);

    void setReviewStatus(Picture picture, User loginUser);

    void editPicture(PictureEdit pictureEdit, User loginUser);

    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatch
     * @param loginUser
     * @return 成功创建的图片数
     */
    Integer uploadPictureByBatch(PictureUploadByBatch pictureUploadByBatch, User loginUser);

    void deletePicture(Picture picture);
}
