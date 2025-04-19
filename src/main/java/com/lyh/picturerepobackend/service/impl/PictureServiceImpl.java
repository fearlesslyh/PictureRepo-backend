package com.lyh.picturerepobackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lyh.picturerepobackend.exception.BusinessException;
import com.lyh.picturerepobackend.exception.ErrorCode;
import com.lyh.picturerepobackend.exception.ThrowUtils;
import com.lyh.picturerepobackend.manager.FileManager; // 引入 FileManager
import com.lyh.picturerepobackend.model.dto.file.FileUpload; // 引入 FileUpload DTO
import com.lyh.picturerepobackend.model.dto.picture.PictureQuery;
import com.lyh.picturerepobackend.model.dto.picture.PictureReview;
import com.lyh.picturerepobackend.model.dto.picture.PictureUpload;
import com.lyh.picturerepobackend.model.entity.Picture;
import com.lyh.picturerepobackend.model.entity.User;
import com.lyh.picturerepobackend.model.enums.PictureReviewStatus;
import com.lyh.picturerepobackend.model.vo.PictureVO;
import com.lyh.picturerepobackend.model.vo.UserVO;
import com.lyh.picturerepobackend.service.PictureService;
import com.lyh.picturerepobackend.mapper.PictureMapper;
import com.lyh.picturerepobackend.service.UserService;
import io.swagger.models.auth.In;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.lyh.picturerepobackend.exception.ErrorCode.OPERATION_ERROR;
import static com.lyh.picturerepobackend.exception.ErrorCode.PARAMS_ERROR;

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

    @Resource
    private UserService userService;


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
        // 从对象中取值
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
        String sortField = pictureQuery.getSortField();
        String sortOrder = pictureQuery.getSortOrder();
        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText));
        }

        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "pic_format", picFormat);
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "pic_size", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "pic_width", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "pic_height", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "pic_scale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "user_id", userId);

        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotBlank(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        if (picture == null) {
            return null;
        }
        //对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        //创建一个PictureVO的分页对象，并将picturePage的当前页、每页大小和总记录数赋值给它,后面返回这个分页对象
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        //获取picturePage中的记录列表
        List<Picture> pictureList = picturePage.getRecords();
        //如果记录列表为空，则直接返回pictureVOPage
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        //将记录列表中的每个Picture对象转换为PictureVO对象，并收集到一个列表中
        List<PictureVO> pictureVOList = pictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
        //关联查询用户信息
        //这里我们做了个小优化，不是针对每条数据都查询一次用户，而是先获取到要查询的用户 id 列表，只发送一次查询用户表的请求，再将查到的值设置到图片对象中。
        //也就是不通过循环遍历每一个userId，而是通过set和map方法，将userId收集到set中，再通过map查询，再比对id是否相同，再给每一个PictureVO对象设置用户信息
        //将记录列表中的每个Picture对象的userId收集到一个集合中
        Set<Long> userIdSet = pictureList.stream()
                .map(Picture::getUserId)
                .collect(Collectors.toSet());
        //根据userId集合查询用户列表，并将用户列表按照userId分组，listByIds是mybatis-plus提供的方法，查询用户id
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        //遍历pictureVOList，为每个PictureVO对象设置用户信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            //如果userIdUserListMap中包含该userId，则获取该userId对应的用户列表中的第一个用户
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            //将用户对象转换为UserVO对象，并设置到PictureVO对象中
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        });
        //将转换后的pictureVOList设置到pictureVOPage中
        pictureVOPage.setRecords(pictureVOList);
        //返回pictureVOPage
        return pictureVOPage;
    }

    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(ObjUtil.isNull(picture), PARAMS_ERROR, "图片信息不能为空");

        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        if (ObjUtil.isNull(id)) {
            ThrowUtils.throwIf(ObjUtil.isNull(id), PARAMS_ERROR, "图片id不能为空");
        }
        if (ObjUtil.isNull(url)) {
            ThrowUtils.throwIf(url.length() > 1024, PARAMS_ERROR, "图片url长度太长了");
        }
        if (ObjUtil.isNull(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, PARAMS_ERROR, "图片介绍长度太长了");
        }
    }

    @Override
    public void reviewPicture(PictureReview pictureReview, User loginUser) {
        Long id = pictureReview.getId();
        ThrowUtils.throwIf(ObjUtil.isNull(id), PARAMS_ERROR, "图片id不能为空");
        Integer reviewStatus = pictureReview.getReviewStatus();
        ThrowUtils.throwIf(ObjUtil.isNull(reviewStatus), PARAMS_ERROR, "审核状态不能为空");
        PictureReviewStatus pictureReviewStatus = PictureReviewStatus.getPictureReviewStatus(reviewStatus);
        ThrowUtils.throwIf(ObjUtil.isNull(pictureReviewStatus), PARAMS_ERROR, "审核状态错误");
        Picture picture = this.getById(id);
        ThrowUtils.throwIf(ObjUtil.isNull(picture), PARAMS_ERROR, "图片不存在");
        //状态相同，无需更新
        if (picture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(PARAMS_ERROR, "审核状态相同，无需更新");
        }
        Picture currentPicture = new Picture();
        BeanUtils.copyProperties(pictureReview, currentPicture);
        currentPicture.setReviewTime(new Date());
        currentPicture.setReviewerId(loginUser.getId());
        boolean result = this.updateById(currentPicture);
        ThrowUtils.throwIf(!result, OPERATION_ERROR, "审核失败");


    }
}

