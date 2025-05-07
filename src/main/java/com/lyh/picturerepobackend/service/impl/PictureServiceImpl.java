package com.lyh.picturerepobackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lyh.picturerepobackend.api.aliyunai.AliYunAiApi;
import com.lyh.picturerepobackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.lyh.picturerepobackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.lyh.picturerepobackend.exception.BusinessException;
import com.lyh.picturerepobackend.exception.ErrorCode;
import com.lyh.picturerepobackend.exception.ThrowUtils;
import com.lyh.picturerepobackend.manager.CosManager;
import com.lyh.picturerepobackend.manager.upload.LocalFilePictureUpload;
import com.lyh.picturerepobackend.manager.upload.PictureUploadTemplate;
import com.lyh.picturerepobackend.manager.upload.UrlFilePictureUpload;
import com.lyh.picturerepobackend.mapper.PictureMapper;
import com.lyh.picturerepobackend.model.dto.file.FileUpload;
import com.lyh.picturerepobackend.model.dto.picture.*;
import com.lyh.picturerepobackend.model.entity.Picture;
import com.lyh.picturerepobackend.model.entity.Space;
import com.lyh.picturerepobackend.model.entity.User;
import com.lyh.picturerepobackend.model.enums.PictureReviewStatus;
import com.lyh.picturerepobackend.model.vo.PictureVO;
import com.lyh.picturerepobackend.model.vo.UserVO;
import com.lyh.picturerepobackend.service.PictureService;
import com.lyh.picturerepobackend.service.SpaceService;
import com.lyh.picturerepobackend.service.UserService;
import com.lyh.picturerepobackend.utils.ColorSimilarUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
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

    @Resource
    private CosManager cosManager;

    @Resource
    private SpaceService spaceService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private ThreadPoolExecutor customExecutor;

    @Resource
    private AliYunAiApi aliYunAiApi;


    public PictureVO uploadPicture(Object inputPicture, PictureUpload pictureUpload, User loginUser) {
        // 0. 校验用户是否登录
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        }
        // 校验空间是否存在
        Long spaceId = pictureUpload.getSpaceId();// 获取空间id
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, NOT_FOUND_ERROR, "空间不存在");
            // 必须是本人或管理员才可上传
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有权限上传图片");
            }
            // 校验额度
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间图片数量已满，已达最大上限");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间图片大小已满，已经没有空间存放图片");
            }
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
            // 仅本人或管理员才可更新
            if (!oldPicture.getUserId().equals(loginUser.getId()) && userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有权限更新图片");
            }
            // 校验空间id是否一致
            // 如果没有spaceId，则使用原来的id
            if (spaceId == null) {
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else {
                if (!spaceId.equals(oldPicture.getSpaceId())) {
                    throw new BusinessException(PARAMS_ERROR, "空间不一致，无法更新图片");
                }
            }
        }

        // 2.根据用户id划分目录
        String uploadPathPrefix;
        if (spaceId == null) {
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        } else {
            uploadPathPrefix = String.format("space/%s", spaceId);
        }
        // 3.根据input区分上传方式
        PictureUploadTemplate pictureUploadTemplate = localFilePictureUpload;
        if (inputPicture instanceof String) {
            // 上传的是 URL
            pictureUploadTemplate = urlFilePictureUpload;
        }
        FileUpload fileUploadResult = pictureUploadTemplate.uploadPicture(inputPicture, uploadPathPrefix);
        // 4.  构造要入库的图片信息
        Picture picture = new Picture();
        picture.setSpaceId(spaceId);
        picture.setUrl(fileUploadResult.getUrl());
        picture.setThumbnailUrl(fileUploadResult.getThumbnailUrl());
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
        picture.setPicColor(fileUploadResult.getPicColor());
        if (pictureId != null) {
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }

        // 5. 保存图片信息
        Long finalSpaceId = spaceId;
        // 开启事务
        transactionTemplate.execute(status -> {
            boolean result = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败,数据库操作失败");
            this.setReviewStatus(picture, loginUser);
            if (finalSpaceId != null) {
                // 更新空间的额度
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, finalSpaceId)
                        .setSql("totalCount=totalCount+1")
                        .setSql("totalSize=totalSize+" + picture.getPicSize())
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "空间额度更新失败");
            }
            return picture;
        });
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
        Long spaceId = pictureQuery.getSpaceId();
        boolean nullSpaceId = pictureQuery.isNullSpaceId();
        Date startEditTime = pictureQuery.getStartEditTime();
        Date endEditTime = pictureQuery.getEndEditTime();
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
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);

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
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet)
                .stream()
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
    public void editPicture(PictureEdit pictureEdit, User loginUser) {
        // 实体类和dto转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEdit, picture);
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 注意需要将list（pictureEdit）转换为string（picture）
        picture.setTags(JSONUtil.toJsonStr(pictureEdit.getTags()));
        // 校验数据
        this.validPicture(picture);
        // 判断图片是否存在
        long id = pictureEdit.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, NOT_FOUND_ERROR, "图片不存在");
        // 校验权限
        this.checkPictureAuthority(oldPicture, loginUser);
        // 补充审核状态
        this.setReviewStatus(picture, loginUser);
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, OPERATION_ERROR, "图片编辑失败");
    }

    /**
     * 批量上传图片实现方法
     *
     * @param pictureUploadByBatch 批量上传参数对象，包含搜索关键词、命名前缀、数量等参数
     * @param loginUser            当前登录用户
     * @return 实际成功上传的图片数量
     */
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatch pictureUploadByBatch, User loginUser) {
        // 参数预处理：获取搜索关键词并设置默认命名前缀
        String searchText = pictureUploadByBatch.getSearchText();
        String namePrefix = pictureUploadByBatch.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText; // 当用户未指定命名前缀时，使用搜索关键词作为前缀
        }

        // 数量参数校验：限制最大批量上传数量为30
        Integer count = pictureUploadByBatch.getCount();
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多 30 条");

        // 构造Bing图片搜索URL（使用异步接口）
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            // 发送HTTP请求获取搜索结果页面
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }

        // 解析页面DOM结构：定位图片容器元素
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isNull(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }

        // 获取所有图片元素（使用iusc类选择器）
        Elements imgElementList = div.select(".iusc");
        int uploadCount = 0;  // 成功计数器

        // 遍历搜索结果图片进行上传
        for (Element imgElement : imgElementList) {
            // 处理图片URL：移除可能存在的查询参数
//            String fileUrl = imgElement.attr("src");
            String dataM = imgElement.attr("m");
            String fileUrl;
            try {
                // 解析JSON字符串
                JSONObject jsonObject = JSONUtil.parseObj(dataM);
                // 获取murl字段（原始图片URL）
                fileUrl = jsonObject.getStr("murl");
            } catch (Exception e) {
                log.error("解析图片数据失败", e);
                continue;
            }
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过: {}", fileUrl);
                continue;
            }
            // 截断问号后的参数（避免URL转义问题）
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }

            // 获取文件后缀名
            String fileExtension = getFileExtension(fileUrl);  //  调用getFileExtension方法获取后缀名
            if (fileExtension == null) {
                log.warn("无法获取文件类型，已跳过: {}", fileUrl);
                continue; // 无法获取文件类型，跳过
            }

            // 构建单个图片上传参数
            PictureUpload pictureUpload = new PictureUpload();
            if (StrUtil.isNotBlank(namePrefix)) {
                // 生成格式化的图片名称（前缀+序号）
                pictureUpload.setPicName(namePrefix + (uploadCount + 1) + "." + fileExtension);
            }

            try {
                // 执行单个图片上传操作
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUpload, loginUser);
                log.info("图片上传成功, id = {}", pictureVO.getId());
                uploadCount++;
            } catch (Exception e) {
                log.error("图片上传失败", e);
                continue;
            }

            // 达到指定数量时提前终止循环
            if (uploadCount >= count) {
                break;
            }
        }
        return uploadCount;
    }

    @Async
    @Override
    public void clearPictureFile(Picture oldPicture) {
        // 判断图片是否被引用
        String pictureUrl = oldPicture.getUrl();
        Long count = this.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();
        if (count > 1) {
            log.warn("图片被引用，无法删除: {}", pictureUrl);
            return;
        }
        //  注意，这里的 url 包含了域名，实际上只要传 key 值（存储路径）就够了
        try {
            // 提取路径部分
            String picturePath = new URL(pictureUrl).getPath();
            cosManager.deleteFile(picturePath);

            // 清理缩略图
            String thumbnailUrl = oldPicture.getThumbnailUrl();
            if (StrUtil.isNotBlank(thumbnailUrl)) {
                String thumbnailPath = new URL(thumbnailUrl).getPath();
                cosManager.deleteFile(thumbnailPath);
            }
        } catch (MalformedURLException e) {
            log.error("处理图片删除时遇到格式错误的 URL。图片 URL: {}", pictureUrl, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "格式错误的 URL");
        }
    }

    @Override
    // picture是要进行修改的图片，loginUser是当前登录用户
    public void checkPictureAuthority(Picture picture, User loginUser) {
        Long userId = loginUser.getId();
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            // 说明是公共空间，只有本人或管理员可以修改
            if (!userService.isAdmin(loginUser) && !userId.equals(picture.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "你没有权限修改图片，这是公共空间，只有本人或管理员才可以修改");
            }
        } else {
            // 说明是私有空间，只有空间创建者才可以修改
            if (!userId.equals(picture.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "你没有权限修改图片，这是私有空间，只有本人才可以修改");
            }
        }
    }

    @Override
    public void deletePicture(long pictureId, User loginUser) {
        // 鉴权和校验合法
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR, "图片id错误");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户未登录");
        Picture picture = this.getById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        // 校验权限
        this.checkPictureAuthority(picture, loginUser);
        // 开启事务
        transactionTemplate.execute(status -> {
            // 删除图片
            boolean result = this.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除图片失败");
            // 释放额度
            Long spaceId = picture.getSpaceId();
            if (spaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, spaceId)
                        .setSql("totalCount=totalCount-1")
                        .setSql("totalSize=totalSize-" + picture.getPicSize())
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "释放空间额度失败");
            }
            return true;
        });
        // 清理cos中的文件
        this.clearPictureFile(picture);
    }

    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor), ErrorCode.PARAMS_ERROR, "空间id不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户未登录");
        // 2. 校验空间
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }
        // 3. 查询该空间下所有图片（必须有主色调）
        List<Picture> pictureList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        // 如果没有图片，直接返回空列表
        if (CollUtil.isEmpty(pictureList)) {
            return Collections.emptyList();
        }
        // 将目标颜色转为 Color 对象
        Color targetColor = Color.decode(picColor);
        List<Picture> sortedPictures = pictureList.stream()
                // 按相似度排序，最后按比较器的比较对象排序
                .sorted(Comparator.comparingDouble(
                        // 计算相似度，遍历每一个图片
                        picture -> {
                            // 提取图片主色调
                            String hexColor = picture.getPicColor();
                            // 没有主色调的图片放到最后
                            if (StrUtil.isBlank(hexColor)) {
                                return Double.MAX_VALUE;
                            }
                            Color pictureColor = Color.decode(hexColor);
                            // 越大越相似，-是从大到小降序排序
                            return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                        }
                ))
                // 取前12个
                .limit(12)
                .collect(Collectors.toList());
        // 转换为 PictureVO
        return sortedPictures.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editPictureByBatch(PictureEditByBatch pictureEditByBatch, User loginUser) {
        List<Long> pictureIdList = pictureEditByBatch.getPictureIdList();
        Long spaceId = pictureEditByBatch.getSpaceId();
        String category = pictureEditByBatch.getCategory();
        List<String> tags = pictureEditByBatch.getTags();
        String nameRule = pictureEditByBatch.getNameRule();
        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR, "spaceId不能为空或pictureIdList不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户未登录");
        // 2. 校验空间
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }
        // 3. 查询指定图片，仅选择需要的字段
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .list();
        if (pictureList.isEmpty()) {
            return;
        }
        // 4. 更新分类和标签
        pictureList.forEach(picture -> {
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (CollUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });
        //批量重命名
        fillPictureWithNameRule(pictureList, nameRule);
        // 5. 批量更新图片
        boolean result = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "批量更新图片失败");
    }

    //  添加了获取文件后缀名的函数
    private String getFileExtension(String fileUrl) {
        try {
            URL url = new URL(fileUrl);
            URLConnection connection = url.openConnection();
            String contentType = connection.getContentType();  //  获取Content-Type
            if (contentType == null) {
                return null;
            }
            if (contentType.startsWith("image/")) {
                return contentType.substring(6); // "image/jpeg" -> "jpeg"  提取后缀名
            }
            return null;
        } catch (IOException e) {
            log.error("获取文件类型失败: {}", fileUrl, e);
            return null;
        }
    }

    /**
     * 批量编辑图片分类和标签
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchEditPictureMetadata(PictureEditByBatch pictureEditByBatch, Long spaceId, Long loginUserId) {
        // 参数校验
        if (pictureEditByBatch.getPictureIdList() == null || pictureEditByBatch.getPictureIdList().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片id列表不能为空");
        }
        ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR, "空间id不能为空");
        ThrowUtils.throwIf(loginUserId == null, ErrorCode.PARAMS_ERROR, "用户id不能为空");

        // 查询空间下的图片
        List<Picture> pictureList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureEditByBatch.getPictureIdList())
                .list();

        if (pictureList.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "指定的图片不存在或不属于该空间");
        }

        // 分批处理避免长事务
        int batchSize = 100;
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < pictureList.size(); i += batchSize) {
            List<Picture> batch = pictureList.subList(i, Math.min(i + batchSize, pictureList.size()));

            // 异步处理每批数据
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                batch.forEach(picture -> {
                    // 编辑分类和标签
                    if (pictureEditByBatch.getCategory() != null) {
                        picture.setCategory(pictureEditByBatch.getCategory());
                    }
                    if (pictureEditByBatch.getTags() != null) {
                        picture.setTags(String.join(",", pictureEditByBatch.getTags()));
                    }
                });
                boolean result = this.updateBatchById(batch);
                if (!result) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "批量更新图片失败");
                }
            }, customExecutor);

            futures.add(future);
        }

        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTask createPictureOutPaintingTask, User loginUser) {
        // 获取图片信息
        Long pictureId = createPictureOutPaintingTask.getPictureId();
        Picture picture = Optional.ofNullable(this.getById(pictureId))
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在")
                );
        // 权限校验
        checkPictureAuthority(picture, loginUser);
        // 构造请求参数
        CreateOutPaintingTaskRequest taskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        taskRequest.setInput(input);
        BeanUtil.copyProperties(createPictureOutPaintingTask, taskRequest);
        // 创建任务
        return aliYunAiApi.createOutPaintingTask(taskRequest);
    }


    /**
     * nameRule 格式：图片{序号}
     *
     * @param pictureList
     * @param nameRule
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if (StrUtil.isBlank(nameRule) || CollUtil.isEmpty(pictureList)) {
            return;
        }
        long count = 1;
        try {
            for (Picture picture : pictureList) {
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(pictureName);
            }
        } catch (Exception e) {
            log.error("名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }
}