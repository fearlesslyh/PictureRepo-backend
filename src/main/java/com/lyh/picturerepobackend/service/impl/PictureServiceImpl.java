package com.lyh.picturerepobackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lyh.picturerepobackend.annotation.AuthorityCheck;
import com.lyh.picturerepobackend.constant.UserConstant;
import com.lyh.picturerepobackend.exception.BusinessException;
import com.lyh.picturerepobackend.exception.ErrorCode;
import com.lyh.picturerepobackend.exception.ThrowUtils;
import com.lyh.picturerepobackend.manager.upload.LocalFilePictureUpload;
import com.lyh.picturerepobackend.manager.upload.PictureUploadTemplate;
import com.lyh.picturerepobackend.manager.upload.UrlFilePictureUpload;
import com.lyh.picturerepobackend.mapper.PictureMapper;
import com.lyh.picturerepobackend.model.dto.file.FileUpload;
import com.lyh.picturerepobackend.model.dto.picture.*;
import com.lyh.picturerepobackend.model.entity.Picture;
import com.lyh.picturerepobackend.model.entity.User;
import com.lyh.picturerepobackend.model.enums.PictureReviewStatus;
import com.lyh.picturerepobackend.model.enums.UserRoleEnum;
import com.lyh.picturerepobackend.model.vo.PictureVO;
import com.lyh.picturerepobackend.model.vo.UserVO;
import com.lyh.picturerepobackend.service.PictureService;
import com.lyh.picturerepobackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.lyh.picturerepobackend.exception.ErrorCode.*;

/**
 * @author RAOYAO
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2025-04-14 19:49:22
 */
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    @Resource
    private LocalFilePictureUpload localFilePictureUpload;

    @Resource
    private UrlFilePictureUpload urlFilePictureUpload;

    @Resource
    private UserService userService;


    public PictureVO uploadPicture(Object inputPicture, PictureUpload pictureUpload, User loginUser) {
        // 0. 校验用户是否登录
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        }
        // 1.校验文件是否为空
        if (inputPicture == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "上传图片为空");
        }
        //判断上传的图片是否存在，以此判断是上传图片还是更新
        Long pictureId = null;
        if (pictureUpload != null) {
            pictureId = pictureUpload.getId();
        }
        //这是更新，因为上传的图片id不为空
        if (pictureId != null) {
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, NOT_FOUND_ERROR, "图片不存在");
        }
        // 2.根据用户id划分目录
        String uploadPathPrefix = String.format("public/%s", loginUser.getId());
        // 3.根据input区分上传方式
        PictureUploadTemplate pictureUploadTemplate = localFilePictureUpload;
        if (inputPicture instanceof String) {
            // 上传的是 URL
            pictureUploadTemplate = urlFilePictureUpload;
        }
        FileUpload fileUploadResult = pictureUploadTemplate.uploadPicture(inputPicture, uploadPathPrefix);
        // 4.  构造要入库的图片信息
        Picture picture = new Picture();
        picture.setUrl(fileUploadResult.getUrl());
        String picName = fileUploadResult.getPicName();
        if (pictureUpload != null && StrUtil.isNotBlank(pictureUpload.getPicName())) {
            picName = pictureUpload.getPicName();
        }
        picture.setName(picName);
        picture.setPicFormat(fileUploadResult.getPicFormat());
        picture.setPicSize(fileUploadResult.getPicSize());
        picture.setPicScale(fileUploadResult.getPicScale());
        picture.setPicWidth(fileUploadResult.getPicWidth());
        picture.setPicHeight(fileUploadResult.getPicHeight());
        picture.setUserId(loginUser.getId());
        if (pictureId!=null){
            picture.setEditTime(new Date());
        }
        boolean result = this.saveOrUpdate(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败,数据库操作失败");
        this.setReviewStatus(picture, loginUser);

        return PictureVO.objToVo(picture);
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
        Long reviewerId = pictureQuery.getReviewerId();
        String reviewMessage = pictureQuery.getReviewMessage();
        Integer reviewStatus = pictureQuery.getReviewStatus();
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
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "pic_size", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "pic_width", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "pic_height", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "pic_scale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "user_id", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);

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
        ThrowUtils.throwIf(ObjUtil.isNull(id), PARAMS_ERROR, "图片id不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, PARAMS_ERROR, "图片url长度太长了");
        }
        if (StrUtil.isNotBlank(introduction)) {
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

    @Override
    public void setReviewStatus(Picture picture, User loginUser) {
        validPicture(picture);
        ThrowUtils.throwIf(loginUser.getId() == null, PARAMS_ERROR, "用户id不能为空");
        if (userService.isAdmin(loginUser)) {
            picture.setReviewStatus(PictureReviewStatus.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewTime(new Date());
            picture.setReviewMessage("管理员审核通过");
            boolean result = this.updateById(picture);
            ThrowUtils.throwIf(!result, OPERATION_ERROR, "审核失败");
        } else {
            picture.setReviewStatus(PictureReviewStatus.REVIEWING.getValue());
        }
    }

    @Override
    @AuthorityCheck(mustHaveRole = UserConstant.USER_LOGIN_STATE)
    public void editPicture(PictureEdit pictureEdit, User loginUser) {
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEdit, picture);
        picture.setEditTime(new Date());
        picture.setTags(JSONUtil.toJsonStr(pictureEdit.getTags()));
        this.validPicture(picture);
        long id= pictureEdit.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, NOT_FOUND_ERROR, "图片不存在");
        this.setReviewStatus(picture, loginUser);
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, OPERATION_ERROR, "图片编辑失败");
    }

    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatch pictureUploadByBatch, User loginUser) {
        String searchText = pictureUploadByBatch.getSearchText();
        // 格式化数量
        Integer count = pictureUploadByBatch.getCount();
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多 30 条");
        // 要抓取的地址
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isNull(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        Elements imgElementList = div.select("img.mimg");
        int uploadCount = 0;
        for (Element imgElement : imgElementList) {
            String fileUrl = imgElement.attr("src");
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过: {}", fileUrl);
                continue;
            }
            // 处理图片上传地址，防止出现转义问题
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }
            // 上传图片
            PictureUpload pictureUploadRequest = new PictureUpload();
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功, id = {}", pictureVO.getId());
                uploadCount++;
            } catch (Exception e) {
                log.error("图片上传失败", e);
                continue;
            }
            if (uploadCount >= count) {
                break;
            }
        }
        return uploadCount;
    }
}

