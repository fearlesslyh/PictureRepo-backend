package com.lyh.picturerepo.interfaces.controller;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lyh.picturerepo.domain.space.service.SpaceDomainService;
import com.lyh.picturerepo.application.service.UserApplicationService;
import com.lyh.picturerepo.domain.user.constant.UserConstant;
import com.lyh.picturerepo.domain.user.entity.User;
import com.lyh.picturerepo.infrastructure.annotation.AuthorityCheck;
import com.lyh.picturerepo.infrastructure.api.CosManager;
import com.lyh.picturerepo.infrastructure.api.aliyunai.AliYunAiApi;
import com.lyh.picturerepo.infrastructure.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.lyh.picturerepo.infrastructure.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.lyh.picturerepo.infrastructure.api.imagesearch.ImageSearchApiFacade;
import com.lyh.picturerepo.infrastructure.api.imagesearch.model.ImageSearchResult;
import com.lyh.picturerepo.infrastructure.common.BaseResponse;
import com.lyh.picturerepo.infrastructure.common.DeleteRequest;
import com.lyh.picturerepo.infrastructure.common.ResultUtils;
import com.lyh.picturerepo.infrastructure.exception.BusinessException;
import com.lyh.picturerepo.infrastructure.exception.ErrorCode;
import com.lyh.picturerepo.infrastructure.exception.ThrowUtils;
import com.lyh.picturerepo.interfaces.assembler.PictureAssembler;
import com.lyh.picturerepo.interfaces.dto.picture.*;
import com.lyh.picturerepobackend.manager.auth.SpaceUserAuthManager;
import com.lyh.picturerepobackend.manager.auth.StpKit;
import com.lyh.picturerepobackend.manager.auth.annotation.SaSpaceCheckPermission;
import com.lyh.picturerepobackend.manager.auth.model.SpaceUserPermissionConstant;
import com.lyh.picturerepo.domain.picture.entity.Picture;
import com.lyh.picturerepo.domain.space.entity.Space;
import com.lyh.picturerepo.domain.picture.valueObject.PictureReviewStatus;
import com.lyh.picturerepo.interfaces.vo.picture.PictureVO;
import com.lyh.picturerepo.application.service.PictureApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.lyh.picturerepo.infrastructure.exception.ErrorCode.*;

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
    private UserApplicationService userApplicationService;

    @Resource
    private PictureApplicationService pictureApplicationService;
    @Resource
    private CosManager cosManager;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    @Lazy
    private SpaceDomainService spaceService;

    @Resource
    private AliYunAiApi aliYunAiApi;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;
    private final Cache<String, String> LOCAL_CACHE =
            Caffeine.newBuilder().initialCapacity(1024)
                    .maximumSize(10000L)
                    // 缓存 5 分钟移除
                    .expireAfterWrite(5L, TimeUnit.MINUTES)
                    .build();

    @PostMapping("/upload/localFile")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
    public BaseResponse<PictureVO> uploadPictureByLocalFile(@RequestPart("file") MultipartFile file, PictureUpload pictureUpload, HttpServletRequest request) {
        User loginUser = userApplicationService.getLoginUser(request);
        // 2.  打印日志，方便调试
        log.info("上传图片请求，图片信息：{}，用户信息：{}", pictureUpload, loginUser);
        // 3.  调用 service 方法上传图片
        PictureVO pictureVO = pictureApplicationService.uploadPicture(file, pictureUpload, loginUser);
        // 4.  返回成功结果
        return ResultUtils.success(pictureVO);
    }

    @PostMapping("/upload/urlFile")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
    public BaseResponse<PictureVO> uploadPictureByUrlFile(@RequestBody PictureUpload pictureUpload, HttpServletRequest request) {
        User loginUser = userApplicationService.getLoginUser(request);
        String fileUrl = pictureUpload.getUrl();
        PictureVO pictureVO = pictureApplicationService.uploadPicture(fileUrl, pictureUpload, loginUser);
        return ResultUtils.success(pictureVO);
    }

    @PostMapping("/delete")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_DELETE)
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() < 0) {
            throw new BusinessException(PARAMS_ERROR, "请求的参数为空或id为空");
        }
        Long id = deleteRequest.getId();
        User loginUser = userApplicationService.getLoginUser(request);
        pictureApplicationService.deletePicture(id, loginUser);
        return ResultUtils.success(true);
    }

    //更新步骤：1.dto转换 2.校验数据 3.补充审核状态 4.操作数据库
    @PostMapping("/update")

    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdate pictureUpdate, HttpServletRequest request) {
        if (pictureUpdate == null || pictureUpdate.getId() < 0) {
            throw new BusinessException(PARAMS_ERROR, "请求的参数为空或id为空");
        }
        // 将实体类和 DTO 进行转换
        Picture pictureEntity = PictureAssembler.toPictureEntity(pictureUpdate);
        //校验数据
        pictureApplicationService.validPicture(pictureEntity);
        Long id = pictureEntity.getId();
        Picture serviceById = pictureApplicationService.getById(id);
        if (serviceById == null) {
            throw new BusinessException(NOT_FOUND_ERROR, "图片不存在");
        }
        //补充审核状态。如果是管理员则直接审核通过，如果是普通用户则审核状态为审核中
        User loginUser = userApplicationService.getLoginUser(request);
        pictureApplicationService.setReviewStatus(pictureEntity, loginUser);
        //操作数据库
        boolean result = pictureApplicationService.updateById(pictureEntity);
        ThrowUtils.throwIf(!result, OPERATION_ERROR, "图片更新失败");
        return ResultUtils.success(true);
    }

    @GetMapping("/get")
    @AuthorityCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPicture(Long id, HttpServletRequest request) {
        if (id == null) {
            throw new BusinessException(PARAMS_ERROR, "id不能为空");
        }
        Picture picture = pictureApplicationService.getById(id);
        if (picture == null) {
            throw new BusinessException(NOT_FOUND_ERROR, "图片不存在");
        }
        return ResultUtils.success(picture);
    }


    // 自主实现的拓展功能：我们首先尝试从 Redis 缓存中获取 PictureVO。
    //
    //如果缓存中没有，我们使用 Redisson 的 RLock 来获取一个分布式锁。
    //
    //只有拿到锁的线程才会去数据库查询，并把结果放入缓存。
    //
    //其他线程会等待锁释放，然后直接从缓存中读取数据，避免了缓存击穿。
    @GetMapping("/get/VO")
    public BaseResponse<PictureVO> getPictureVO(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "id不能为空");
        final String cacheKey = "picture:vo:" + id;
        // 尝试从本地缓存中获取
        String cacheLocalValue = LOCAL_CACHE.getIfPresent(cacheKey);
        if (cacheLocalValue != null) {
            if (cacheLocalValue.equals("null")) {
                throw new BusinessException(NOT_FOUND_ERROR, "图片不存在");
            }
            PictureVO vo = JSONUtil.toBean(cacheLocalValue, PictureVO.class);
            return ResultUtils.success(vo);
        }
        // 尝试从 Redis 中获取
        String cacheRedisValue = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.isNotBlank(cacheRedisValue)) {
            if ("null".equals(cacheRedisValue)) { // 检查是否是缓存的空对象
                throw new BusinessException(NOT_FOUND_ERROR, "图片不存在");
            }
            return ResultUtils.success(JSONUtil.toBean(cacheRedisValue, PictureVO.class));
        }
        // 如果redis缓存没有数据，尝试获取分布式锁
        Picture picture = pictureApplicationService.getById(id);
        RLock lock = redissonClient.getLock("lock:" + cacheKey); // 使用 Redisson 锁
        try {
            // 尝试获取锁，如果获取不到，会等待7秒
            Space space = null;
            if (lock.tryLock(7, TimeUnit.SECONDS)) {
                // 从数据库查询
                if (picture == null) {
                    ValueOperations<String, String> valuesOps = stringRedisTemplate.opsForValue();
                    // 设置缓存空对象，避免缓存穿透
                    valuesOps.set(cacheKey, "null", 60, TimeUnit.SECONDS);
                    LOCAL_CACHE.put(cacheKey, "null");
                    throw new BusinessException(NOT_FOUND_ERROR, "图片不存在");
                }
                // 空间权限校验
                Long spaceId = picture.getSpaceId();
                if (spaceId != null) {
                    // 检查空间权限
//                    pictureApplicationService.checkPictureAuthority(picture, loginUser);
                    boolean hasPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
                    ThrowUtils.throwIf(!hasPermission, ErrorCode.NO_AUTH_ERROR, "没有权限");
                    space = spaceService.getById(spaceId);
                    ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
                }
            }
            // 数据库查询数据成功，设置redis和本地缓存的数据
            User loginUser = userApplicationService.getLoginUser(request);
            List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
            PictureVO pictureVO = pictureApplicationService.getPictureVO(picture, request);
            pictureVO.setPermissionList(permissionList);
            ValueOperations<String, String> valueOperations = stringRedisTemplate.opsForValue();
            valueOperations.set(cacheKey, JSONUtil.toJsonStr(pictureVO), 1, TimeUnit.HOURS);// 缓存一小时
            LOCAL_CACHE.put(cacheKey, JSONUtil.toJsonStr(pictureVO));
            return ResultUtils.success(pictureVO);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
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
        QueryWrapper<Picture> queryWrapper = pictureApplicationService.getQueryWrapper(pictureQuery);
        Page<Picture> picturePageResult = pictureApplicationService.page(picturePage, queryWrapper);
        return ResultUtils.success(picturePageResult);
    }

    @GetMapping("/list/VO/page")
    public BaseResponse<Page<PictureVO>> getPictureVOPage(PictureQuery pictureQuery, HttpServletRequest request) {
        if (pictureQuery == null) {
            throw new BusinessException(PARAMS_ERROR, "请求的参数为空");
        }
        int current = pictureQuery.getCurrent();
        int size = pictureQuery.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, PARAMS_ERROR, "每页数量不能超过20");
        // 空间权限校验
        Long spaceId = pictureQuery.getSpaceId();
        if (spaceId == null) {
            //普通用户只能查看审核通过的图片
            pictureQuery.setReviewStatus(PictureReviewStatus.PASS.getValue());
            pictureQuery.setNullSpaceId(true);
        } else {
            // 是私有空间
            boolean hasPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hasPermission, ErrorCode.NO_AUTH_ERROR, "没有权限");
        }
        Page<Picture> picturePage = new Page<>(current, size);
        QueryWrapper<Picture> queryWrapper = pictureApplicationService.getQueryWrapper(pictureQuery);
        //查询数据库，picturePage是页面信息，queryWrapper是查询条件
        Page<Picture> picturePageResult = pictureApplicationService.page(picturePage, queryWrapper);
        Page<PictureVO> pictureVOPage = pictureApplicationService.getPictureVOPage(picturePageResult, request);
        return ResultUtils.success(pictureVOPage);
    }

    @PostMapping("/edit")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEdit pictureEdit, HttpServletRequest request) {
        if (pictureEdit == null || pictureEdit.getId() < 0) {
            throw new BusinessException(PARAMS_ERROR, "请求的参数为空或id为空");
        }
        User loginUser = userApplicationService.getLoginUser(request);
        Picture pictureEntity = PictureAssembler.toPictureEntity(pictureEdit);
        pictureApplicationService.editPicture(pictureEntity, loginUser);
        return ResultUtils.success(true);
    }

    @PostMapping("/review")
    @AuthorityCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> reviewPicture(@RequestBody PictureReview pictureReview, HttpServletRequest request) {
        // 审核接口
        ThrowUtils.throwIf(pictureReview == null, PARAMS_ERROR, "请求的参数为空");
        User loginUser = userApplicationService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(NOT_LOGIN_ERROR, "未登录");
        }
        pictureApplicationService.reviewPicture(pictureReview, loginUser);
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
        User loginUser = userApplicationService.getLoginUser(request);
        int uploadCount = pictureApplicationService.uploadPictureByBatch(pictureUploadByBatch, loginUser);
        return ResultUtils.success(uploadCount);
    }

    //redis缓存：先查询缓存，如果没有，从数据库中获取数据，再存入缓存中。
    @Deprecated
    @PostMapping("/list/page/vo/cache")
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
        QueryWrapper<Picture> queryWrapper = pictureApplicationService.getQueryWrapper(pictureQuery);
        Page<Picture> picturePageResult = pictureApplicationService.page(picturePage, queryWrapper);
        Page<PictureVO> pictureVOPage = pictureApplicationService.getPictureVOPage(picturePageResult, request);
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
    @PostMapping("/list/page/vo/caffeine")
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
        QueryWrapper<Picture> queryWrapper = pictureApplicationService.getQueryWrapper(pictureQuery);
        Page<Picture> picturePageResult = pictureApplicationService.page(picturePage, queryWrapper);
        Page<PictureVO> pictureVOPage = pictureApplicationService.getPictureVOPage(picturePageResult, request);
        //将数据放入缓存
        String cachePageJson = JSONUtil.toJsonStr(pictureVOPage);
        LOCAL_CACHE.put(cacheKey, cachePageJson);
        //返回数据
        return ResultUtils.success(pictureVOPage);
    }

    //多级缓存：先查询本地缓存，如果没有，从redis中读取数据，如果命中，则存入本地缓存再返回。如果没有命中，则从数据库中获取数据，再存入redis和本地缓存中。
    @PostMapping("/list/page/vo/multiCache")
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
        QueryWrapper<Picture> queryWrapper = pictureApplicationService.getQueryWrapper(pictureQuery);
        Page<Picture> picturePageResult = pictureApplicationService.page(picturePage, queryWrapper);
        Page<PictureVO> pictureVOPage = pictureApplicationService.getPictureVOPage(picturePageResult, request);
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
    @PostMapping("/list/page/manualRefresh")
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
        QueryWrapper<Picture> queryWrapper = pictureApplicationService.getQueryWrapper(pictureQuery);
        Page<Picture> picturePageResult = pictureApplicationService.page(picturePage, queryWrapper);
        Page<PictureVO> pictureVOPage = pictureApplicationService.getPictureVOPage(picturePageResult, request);
        String cachePageValue = JSONUtil.toJsonStr(pictureVOPage);
        //更新本地缓存和redis缓存
        LOCAL_CACHE.put(cacheKey, cachePageValue);
        ValueOperations<String, String> valuesOps = stringRedisTemplate.opsForValue();
        valuesOps.set(cacheKey, cachePageValue, 400 + RandomUtil.randomInt(0, 600), TimeUnit.MINUTES);
        //返回成功
        return ResultUtils.success(true);
    }

    @PostMapping("/search/byPicture")
    public BaseResponse<List<ImageSearchResult>> searchPictureByImage(@RequestBody SearchPictureByPicture searchPictureByPicture) {
        ThrowUtils.throwIf(searchPictureByPicture == null, ErrorCode.PARAMS_ERROR, "请求的参数为空");
        Long pictureId = searchPictureByPicture.getPictureId();
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR, "图片id不能为空");
        Picture oldPicture = pictureApplicationService.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        List<ImageSearchResult> resultList = ImageSearchApiFacade.searchImage(oldPicture.getUrl());
        return ResultUtils.success(resultList);
    }

    @PostMapping("/search/byColor")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_VIEW)
    public BaseResponse<List<PictureVO>> searchPictureByColor(@RequestBody SearchPictureByColor searchPictureByColor, HttpServletRequest request) {
        ThrowUtils.throwIf(searchPictureByColor == null, ErrorCode.PARAMS_ERROR);
        String picColor = searchPictureByColor.getPicColor();
        Long spaceId = searchPictureByColor.getSpaceId();
        User loginUser = userApplicationService.getLoginUser(request);
        List<PictureVO> result = pictureApplicationService.searchPictureByColor(spaceId, picColor, loginUser);
        return ResultUtils.success(result);
    }

    @PostMapping("/edit/batch")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPictureByBatch(@RequestBody PictureEditByBatch pictureEditByBatch, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureEditByBatch == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userApplicationService.getLoginUser(request);
        pictureApplicationService.editPictureByBatch(pictureEditByBatch, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 创建 AI 扩图任务
     */
    @PostMapping("/out_painting/create_task")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<CreateOutPaintingTaskResponse> createPictureOutPaintingTask(
            @RequestBody CreatePictureOutPaintingTask createPictureOutPaintingTaskRequest,
            HttpServletRequest request) {
        if (createPictureOutPaintingTaskRequest == null || createPictureOutPaintingTaskRequest.getPictureId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求的参数为空");
        }
        User loginUser = userApplicationService.getLoginUser(request);
        CreateOutPaintingTaskResponse response = pictureApplicationService.createPictureOutPaintingTask(createPictureOutPaintingTaskRequest, loginUser);
        return ResultUtils.success(response);
    }

    /**
     * 查询 AI 扩图任务
     */
    @GetMapping("/out_painting/get_task")
    public BaseResponse<GetOutPaintingTaskResponse> getPictureOutPaintingTask(String taskId) {
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.PARAMS_ERROR, "任务id不能为空");
        GetOutPaintingTaskResponse task = aliYunAiApi.getOutPaintingTask(taskId);
        return ResultUtils.success(task);
    }

}