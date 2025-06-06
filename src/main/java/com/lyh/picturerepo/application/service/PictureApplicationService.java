package com.lyh.picturerepo.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lyh.picturerepo.domain.picture.entity.Picture;
import com.lyh.picturerepo.domain.user.entity.User;
import com.lyh.picturerepo.infrastructure.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.lyh.picturerepo.interfaces.dto.picture.*;
import com.lyh.picturerepo.interfaces.vo.picture.PictureVO;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author RAOYAO
 * @description 针对表【picture(图片)】的数据库操作Service
 * @createDate 2025-04-14 19:49:22
 */
public interface PictureApplicationService extends IService<Picture> {
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

    void reviewPicture(PictureReview pictureReview, User loginUser);

    void setReviewStatus(Picture picture, User loginUser);

    void editPicture(Picture picture, User loginUser);

    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatch
     * @param loginUser
     * @return 成功创建的图片数
     */
    Integer uploadPictureByBatch(PictureUploadByBatch pictureUploadByBatch, User loginUser);

    void clearPictureFile(Picture picture);

//    void checkPictureAuthority(Picture picture, User loginUser);

    void deletePicture(long pictureId, User loginUser);

    List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser);

    void editPictureByBatch(PictureEditByBatch pictureEditByBatch, User loginUser);

    @Transactional(rollbackFor = Exception.class)
    void batchEditPictureMetadata(PictureEditByBatch request, Long spaceId, Long loginUserId);

    CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTask createPictureOutPaintingTask, User loginUser);

    void validPicture(Picture picture);
}
