package com.lyh.picturerepobackend.controller;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lyh.picturerepobackend.annotation.AuthorityCheck;
import com.lyh.picturerepobackend.common.BaseResponse;
import com.lyh.picturerepobackend.common.DeleteRequest;
import com.lyh.picturerepobackend.common.ResultUtils;
import com.lyh.picturerepobackend.constant.UserConstant;
import com.lyh.picturerepobackend.exception.BusinessException;
import com.lyh.picturerepobackend.exception.ErrorCode;
import com.lyh.picturerepobackend.exception.ThrowUtils;
import com.lyh.picturerepobackend.manager.CosManager;
import com.lyh.picturerepobackend.model.dto.picture.*;
import com.lyh.picturerepobackend.model.entity.Picture;
import com.lyh.picturerepobackend.model.entity.User;
import com.lyh.picturerepobackend.model.enums.PictureReviewStatus;
import com.lyh.picturerepobackend.model.vo.PictureVO;
import com.lyh.picturerepobackend.service.PictureService;
import com.lyh.picturerepobackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import springfox.documentation.spring.web.json.Json;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.lyh.picturerepobackend.exception.ErrorCode.*;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/15 19:53
 */
@RestController
@RequestMapping("/picture")
@Slf4j
public class PictureController {
    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;
    @Resource
    private CosManager cosManager;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private final Cache<String, String> LOCAL_CACHE =
            Caffeine.newBuilder().initialCapacity(1024)
                    .maximumSize(10000L)
                    // 缓存 5 分钟移除
                    .expireAfterWrite(5L, TimeUnit.MINUTES)
                    .build();

    @PostMapping("/upload/localFile")
    public BaseResponse<PictureVO> uploadPictureByLocalFile(@RequestPart("file") MultipartFile file, PictureUpload pictureUpload, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        // 2.  打印日志，方便调试
        log.info("上传图片请求，图片信息：{}，用户信息：{}", pictureUpload, loginUser);
        // 3.  调用 service 方法上传图片
        PictureVO pictureVO = pictureService.uploadPicture(file, pictureUpload, loginUser);
        // 4.  返回成功结果
        return ResultUtils.success(pictureVO);
    }

    @PostMapping("/upload/urlFile")
    public BaseResponse<PictureVO> uploadPictureByUrlFile(@RequestBody PictureUpload pictureUpload, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        String fileUrl = pictureUpload.getUrl();
        PictureVO pictureVO = pictureService.uploadPicture(fileUrl, pictureUpload, loginUser);
        return ResultUtils.success(pictureVO);
    }

    @PostMapping("/delete")
    @AuthorityCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() < 0) {
            throw new BusinessException(PARAMS_ERROR, "请求的参数为空或id为空");
        }
        User loginUser = userService.getLoginUser(request);
        Long id = deleteRequest.getId();
        if (loginUser == null || id == null) {
            throw new BusinessException(NOT_LOGIN_ERROR, "未登录");
        }
        boolean result = pictureService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除失败");
        return ResultUtils.success(true);
    }

    @PostMapping("/update")
    @AuthorityCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdate pictureUpdate, HttpServletRequest request) {
        if (pictureUpdate == null || pictureUpdate.getId() < 0) {
            throw new BusinessException(PARAMS_ERROR, "请求的参数为空或id为空");
        }
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdate, picture);
        picture.setTags(JSONUtil.toJsonStr(pictureUpdate.getTags()));
        pictureService.validPicture(picture);
        Long id = picture.getId();
        Picture serviceById = pictureService.getById(id);
        if (serviceById == null) {
            throw new BusinessException(NOT_FOUND_ERROR, "图片不存在");
        }
        //补充审核状态。如果是管理员则直接审核通过，如果是普通用户则审核状态为审核中
        User loginUser = userService.getLoginUser(request);
        pictureService.setReviewStatus(picture, loginUser);
        //操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, OPERATION_ERROR, "图片更新失败");
        return ResultUtils.success(true);
    }

    @GetMapping("/get")
    @AuthorityCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPicture(Long id, HttpServletRequest request) {
        if (id == null) {
            throw new BusinessException(PARAMS_ERROR, "id不能为空");
        }
        Picture picture = pictureService.getById(id);
        if (picture == null) {
            throw new BusinessException(NOT_FOUND_ERROR, "图片不存在");
        }
        return ResultUtils.success(picture);
    }


    @GetMapping("/get/VO")
    public BaseResponse<PictureVO> getPictureVO(Long id, HttpServletRequest request) {
        if (id == null) {
            throw new BusinessException(PARAMS_ERROR, "id不能为空");
        }
        Picture picture = pictureService.getById(id);
        if (picture == null) {
            throw new BusinessException(NOT_FOUND_ERROR, "图片不存在");
        }
        PictureVO pictureVO = pictureService.getPictureVO(picture, request);
        return ResultUtils.success(pictureVO);
    }

    @GetMapping("/list/page")
    @AuthorityCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> getPicturePage(PictureQuery pictureQuery) {
        if (pictureQuery == null) {
            throw new BusinessException(PARAMS_ERROR, "请求的参数为空");
        }
        int current = pictureQuery.getCurrent();
        int size = pictureQuery.getPageSize();
        Page<Picture> picturePage = new Page<>(current, size);
        QueryWrapper<Picture> queryWrapper = pictureService.getQueryWrapper(pictureQuery);
        Page<Picture> picturePageResult = pictureService.page(picturePage, queryWrapper);
        return ResultUtils.success(picturePageResult);
    }

    @GetMapping("/list/VO/page")
    public BaseResponse<Page<PictureVO>> getPictureVOPage(PictureQuery pictureQuery, HttpServletRequest request) {
        if (pictureQuery == null) {
            throw new BusinessException(PARAMS_ERROR, "请求的参数为空");
        }
        int current = pictureQuery.getCurrent();
        int size = pictureQuery.getPageSize();
        //普通用户只能查看审核通过的图片
        pictureQuery.setReviewStatus(PictureReviewStatus.PASS.getValue());
        ThrowUtils.throwIf(size > 20, PARAMS_ERROR, "每页数量不能超过20");
        Page<Picture> picturePage = new Page<>(current, size);
        QueryWrapper<Picture> queryWrapper = pictureService.getQueryWrapper(pictureQuery);
        //查询数据库，picturePage是页面信息，queryWrapper是查询条件
        Page<Picture> picturePageResult = pictureService.page(picturePage, queryWrapper);
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePageResult, request);
        return ResultUtils.success(pictureVOPage);
    }

    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEdit pictureEdit, HttpServletRequest request) {
        if (pictureEdit == null || pictureEdit.getId() < 0) {
            throw new BusinessException(PARAMS_ERROR, "请求的参数为空或id为空");
        }
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEdit, picture);
        picture.setTags(JSONUtil.toJsonStr(pictureEdit.getTags()));
        picture.setEditTime(new Date());
        pictureService.validPicture(picture);
        //查询要编辑的图片是否存在
        Long id = pictureEdit.getId();
        Picture serviceById = pictureService.getById(id);
        if (serviceById == null) {
            throw new BusinessException(NOT_FOUND_ERROR, "图片不存在");
        }
        //本人和管理员才可编辑
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null || !loginUser.getId().equals(serviceById.getUserId())) {
            throw new BusinessException(NO_AUTH_ERROR, "无权限");
        }
        //补充审核状态。如果是管理员则直接审核通过，如果是普通用户则审核状态为审核中
        pictureService.setReviewStatus(picture, loginUser);
        //操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, OPERATION_ERROR, "图片更新失败");
        return ResultUtils.success(true);
    }

    @PostMapping("/review")
    @AuthorityCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> reviewPicture(@RequestBody PictureReview pictureReview, HttpServletRequest request) {
        // 审核接口
        ThrowUtils.throwIf(pictureReview == null, PARAMS_ERROR, "请求的参数为空");
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(NOT_LOGIN_ERROR, "未登录");
        }
        pictureService.reviewPicture(pictureReview, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 查询文件列表
     *
     * @param prefix 前缀，用于过滤文件
     * @return 包含文件 URL 的列表
     */
    @GetMapping("/list")
    public BaseResponse<List<String>> listFiles(@RequestParam(required = false) String prefix) {
        List<String> fileUrls = cosManager.listObjects(prefix);
        return ResultUtils.success(fileUrls);
    }

    /**
     * 文件下载
     *
     * @param filename 文件名
     * @param response HttpServletResponse
     */
    @PostMapping("/download")
    public void downloadFile(String filename, HttpServletResponse response) {
        if (StringUtils.isBlank(filename)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件名不能为空");
        }
        cosManager.downloadFile(filename, response);
    }

    @PostMapping("/upload/batch")
    @AuthorityCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureByBatch(
            @RequestBody PictureUploadByBatch pictureUploadByBatch,
            HttpServletRequest request
    ) {
        ThrowUtils.throwIf(pictureUploadByBatch == null, ErrorCode.PARAMS_ERROR, "请求的参数为空");
        User loginUser = userService.getLoginUser(request);
        int uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatch, loginUser);
        return ResultUtils.success(uploadCount);
    }

    //redis缓存：先查询缓存，如果没有，从数据库中获取数据，再存入缓存中。
    @Deprecated
    @PostMapping("list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCache(@RequestBody PictureQuery pictureQuery, HttpServletRequest request) {
        long current = pictureQuery.getCurrent();
        long size = pictureQuery.getPageSize();
        //限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR, "每页数量不能超过20");
        //普通用户只能查看已过审的数据
        pictureQuery.setReviewStatus(PictureReviewStatus.PASS.getValue());

        //构建缓存key
        String queryCondition = JSONUtil.toJsonStr(pictureQuery);
        String cacheKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String redisKey = "picture:listPictureVOByPageWithCache" + cacheKey;
        //从缓存中获取数据
        ValueOperations<String, String> valuesOps = stringRedisTemplate.opsForValue();
        String cacheValue = valuesOps.get(redisKey);
        if (cacheValue != null) {
            Page<PictureVO> cachePage = JSONUtil.toBean(cacheValue, Page.class);
            return ResultUtils.success(cachePage);
        }
        //缓存中没有数据，从数据库中获取数据
        Page<Picture> picturePage = new Page<>(current, size);
        QueryWrapper<Picture> queryWrapper = pictureService.getQueryWrapper(pictureQuery);
        Page<Picture> picturePageResult = pictureService.page(picturePage, queryWrapper);
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePageResult, request);
        //将数据放入缓存
        String cachePageJson = JSONUtil.toJsonStr(pictureVOPage);
        //5-10分钟随机过期， 防止缓存雪崩
        int cacheExpireTime = 400 + RandomUtil.randomInt(0, 600);
        valuesOps.set(redisKey, cachePageJson, cacheExpireTime, TimeUnit.MINUTES);
        //返回数据
        return ResultUtils.success(pictureVOPage);
    }

    //本地缓存：先查询本地缓存，如果没有，从数据库中获取数据，再存入本地缓存中。
    @Deprecated
    @PostMapping("list/page/vo/caffeine")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCaffeine(@RequestBody PictureQuery pictureQuery, HttpServletRequest request) {
        long current = pictureQuery.getCurrent();
        long size = pictureQuery.getPageSize();
        //限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR, "每页数量不能超过20");
        //普通用户只能查看已过审的数据
        pictureQuery.setReviewStatus(PictureReviewStatus.PASS.getValue());

        //构建缓存key
        String queryCondition = JSONUtil.toJsonStr(pictureQuery);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String cacheKey = "picture:listPictureVOByPageWithCaffeine" + hashKey;
        //从缓存中获取数据
        ValueOperations<String, String> valuesOps = stringRedisTemplate.opsForValue();
        String cacheValue = valuesOps.get(cacheKey);
        if (cacheValue != null) {
            Page<PictureVO> cachePage = JSONUtil.toBean(cacheValue, Page.class);
            return ResultUtils.success(cachePage);
        }
        //缓存中没有数据，从数据库中获取数据
        Page<Picture> picturePage = new Page<>(current, size);
        QueryWrapper<Picture> queryWrapper = pictureService.getQueryWrapper(pictureQuery);
        Page<Picture> picturePageResult = pictureService.page(picturePage, queryWrapper);
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePageResult, request);
        //将数据放入缓存
        String cachePageJson = JSONUtil.toJsonStr(pictureVOPage);
        LOCAL_CACHE.put(cacheKey, cachePageJson);
        //返回数据
        return ResultUtils.success(pictureVOPage);
    }

    //多级缓存：先查询本地缓存，如果没有，从redis中读取数据，如果命中，则存入本地缓存再返回。如果没有命中，则从数据库中获取数据，再存入redis和本地缓存中。
    @PostMapping("list/page/vo/multiCache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithMultiCache(@RequestBody PictureQuery pictureQuery, HttpServletRequest request) {
        long current = pictureQuery.getCurrent();
        long size = pictureQuery.getPageSize();
        //限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR, "每页数量不能超过20");
        //普通用户只能查看已过审的数据
        pictureQuery.setReviewStatus(PictureReviewStatus.PASS.getValue());
        //构建缓存key，优先从本地缓存中读取数据。如果命中，则直接返回。
        String queryCondition = JSONUtil.toJsonStr(pictureQuery);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String cacheKey = "picture:listPictureVOByPageWithMultiCache" + hashKey;
        String cacheValue = LOCAL_CACHE.getIfPresent(cacheKey);
        if (cacheValue != null) {
            Page<PictureVO> cachePage = JSONUtil.toBean(cacheValue, Page.class);
            return ResultUtils.success(cachePage);
        }
        //本地缓存中没有数据，从redis中读取数据
        ValueOperations<String, String> valuesOps = stringRedisTemplate.opsForValue();
        cacheValue = valuesOps.get(cacheKey);
        if (cacheValue != null) {
            //如果命中了redis缓存，存入本地缓存再返回
            LOCAL_CACHE.put(cacheKey, cacheValue);
            Page<PictureVO> cachePage = JSONUtil.toBean(cacheValue, Page.class);
            return ResultUtils.success(cachePage);
        }
        //redis中也没有数据，从数据库中获取数据
        Page<Picture> picturePage = new Page<>(current, size);
        QueryWrapper<Picture> queryWrapper = pictureService.getQueryWrapper(pictureQuery);
        Page<Picture> picturePageResult = pictureService.page(picturePage, queryWrapper);
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePageResult, request);
        //将数据放入缓存
        String cachePageJson = JSONUtil.toJsonStr(pictureVOPage);
        LOCAL_CACHE.put(cacheKey, cachePageJson);
        //更新redis缓存, 5-10分钟随机过期， 防止缓存雪崩
        valuesOps.set(cacheKey, cachePageJson, 400 + RandomUtil.randomInt(0, 600), TimeUnit.MINUTES);
        //返回数据
        return ResultUtils.success(pictureVOPage);
    }

    // 拓展：手动刷新缓存：在某些情况下，数据更新较为频繁，但自动刷新缓存机制可能存在延迟，可以通过手动刷新来解决。、
    // 提供一个刷新缓存的接口，仅管理员可调用。提供管理后台，支持管理员手动刷新指定缓存。
    @PostMapping("list/page/manualRefresh")
    @AuthorityCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> manualRefreshCache(@RequestBody PictureQuery pictureQuery, HttpServletRequest request) {
        if (pictureQuery == null) {
            throw new BusinessException(PARAMS_ERROR, "请求的参数为空");
        }
        long current = pictureQuery.getCurrent();
        long size = pictureQuery.getPageSize();
        //构造缓存key
        String queryCondition = JSONUtil.toJsonStr(pictureQuery);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String cacheKey = "picture:listPictureVOByPageWithMultiCache" + hashKey;
        //从数据库中获取数据
        Page<Picture> picturePage = new Page<>(current, size);
        QueryWrapper<Picture> queryWrapper = pictureService.getQueryWrapper(pictureQuery);
        Page<Picture> picturePageResult = pictureService.page(picturePage, queryWrapper);
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePageResult, request);
        String cachePageValue = JSONUtil.toJsonStr(pictureVOPage);
        //更新本地缓存和redis缓存
        LOCAL_CACHE.put(cacheKey, cachePageValue);
        ValueOperations<String, String> valuesOps = stringRedisTemplate.opsForValue();
        valuesOps.set(cacheKey, cachePageValue, 400 + RandomUtil.randomInt(0, 600), TimeUnit.MINUTES);
        //返回成功
        return ResultUtils.success(true);
    }

}