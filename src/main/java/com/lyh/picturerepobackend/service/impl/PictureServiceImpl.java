package com.lyh.picturerepobackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lyh.picturerepobackend.exception.BusinessException;
import com.lyh.picturerepobackend.exception.ErrorCode;
import com.lyh.picturerepobackend.manager.FileManager; // 引入 FileManager
import com.lyh.picturerepobackend.model.dto.file.FileUpload; // 引入 FileUpload DTO
import com.lyh.picturerepobackend.model.dto.picture.PictureQuery;
import com.lyh.picturerepobackend.model.dto.picture.PictureUpload;
import com.lyh.picturerepobackend.model.entity.Picture;
import com.lyh.picturerepobackend.model.entity.User;
import com.lyh.picturerepobackend.model.vo.PictureVO;
import com.lyh.picturerepobackend.service.PictureService;
import com.lyh.picturerepobackend.mapper.PictureMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * @author RAOYAO
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2025-04-14 19:49:22
 */
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    @Resource
    private FileManager fileManager; // 注入 FileManager


    @Override
    public PictureVO uploadPicture(MultipartFile multipartFile, PictureUpload pictureUpload, User loginUser) {
        // 0. 校验用户是否登录
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        }

        // 1. 校验文件是否为空
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "上传图片为空");
        }

        // 2.  定义上传路径前缀，例如 "picture"
        String uploadPathPrefix = "picture";

        // 3.  调用 FileManager 的 uploadPicture 方法上传图片
        FileUpload fileUploadResult;
        try {
            fileUploadResult = fileManager.uploadFile(multipartFile, uploadPathPrefix);
        } catch (BusinessException e) {
            //  这里直接抛出 FileManager 中封装的异常，避免重复处理
            throw e;
        }

        // 4.  构造要入库的图片信息
        Picture picture = new Picture();
        Long pictureId = pictureUpload.getId();
        if (pictureId != null) {
            // 更新图片
            Picture existingPicture = this.getById(pictureId);
            if (existingPicture == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            }
            picture.setId(pictureId);
            picture.setUpdateTime(new Date());
            //  保留不需要更新的字段
            picture.setCreateTime(existingPicture.getCreateTime());
            picture.setUserId(existingPicture.getUserId());
            picture.setEditTime(new Date());
        } else {
            // 新增图片
            picture.setUserId(loginUser.getId());
            picture.setCreateTime(new Date());
        }
        //  使用 FileManager 返回的 URL
        picture.setUrl(fileUploadResult.getUrl());
        picture.setName(fileUploadResult.getPicName());
        picture.setPicFormat(fileUploadResult.getPicFormat());
        picture.setPicSize(fileUploadResult.getPicSize());
        picture.setPicScale(fileUploadResult.getPicScale());
        picture.setPicWidth(fileUploadResult.getPicWidth());
        picture.setPicHeight(fileUploadResult.getPicHeight());
        picture.setUserId(loginUser.getId());
        // 5.  保存图片信息到数据库
        try {
            boolean saveOrUpdateResult = this.saveOrUpdate(picture);
            if (!saveOrUpdateResult) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片信息保存失败");
            }
        } catch (Exception e) {
            log.error("图片上传或更新失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片上传或更新过程中发生错误");
        }

        // 6.  封装返回结果
        PictureVO pictureVO = PictureVO.objToVo(picture);
        //  将 FileManager 返回的图片信息设置到 PictureVO 中
        pictureVO.setPicWidth(fileUploadResult.getPicWidth());
        pictureVO.setPicHeight(fileUploadResult.getPicHeight());
        pictureVO.setPicSize(fileUploadResult.getPicSize());
        pictureVO.setPicFormat(fileUploadResult.getPicFormat());
        pictureVO.setPicScale(fileUploadResult.getPicScale());

        return pictureVO;
    }

    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQuery pictureQuery) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQuery == null) {
            return null;
        }
        Long id = pictureQuery.getId();
        String name = pictureQuery.getName();
        String introduction = pictureQuery.getIntroduction();
        String category = pictureQuery.getCategory();
        List<String> tags = pictureQuery.getTags();
        Long picSize = pictureQuery.getPicSize();
        Integer picWidth = pictureQuery.getPicWidth();
        Integer picHeight = pictureQuery.getPicHeight();
        Double picScale = pictureQuery.getPicScale();
        String picFormat = pictureQuery.getPicFormat();
        String searchText = pictureQuery.getSearchText();
        Long userId = pictureQuery.getUserId();
        int current = pictureQuery.getCurrent();
        int pageSize = pictureQuery.getPageSize();
        String sortField = pictureQuery.getSortField();
        String sortOrder = pictureQuery.getSortOrder();

        return null;
    }
}

