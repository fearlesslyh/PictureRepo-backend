package com.lyh.picturerepobackend.controller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lyh.picturerepobackend.annotation.AuthorityCheck;
import com.lyh.picturerepobackend.common.BaseResponse;
import com.lyh.picturerepobackend.common.DeleteRequest;
import com.lyh.picturerepobackend.common.ResultUtils;
import com.lyh.picturerepobackend.constant.UserConstant;
import com.lyh.picturerepobackend.exception.BusinessException;
import com.lyh.picturerepobackend.exception.ErrorCode;
import com.lyh.picturerepobackend.exception.ThrowUtils;
import com.lyh.picturerepobackend.model.dto.picture.PictureEdit;
import com.lyh.picturerepobackend.model.dto.picture.PictureQuery;
import com.lyh.picturerepobackend.model.dto.picture.PictureReview;
import com.lyh.picturerepobackend.model.dto.picture.PictureUpdate;
import com.lyh.picturerepobackend.model.entity.Picture;
import com.lyh.picturerepobackend.model.entity.User;
import com.lyh.picturerepobackend.model.enums.PictureReviewStatus;
import com.lyh.picturerepobackend.model.vo.PictureVO;
import com.lyh.picturerepobackend.service.PictureService;
import com.lyh.picturerepobackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import java.util.Date;

import static com.lyh.picturerepobackend.exception.ErrorCode.*;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/15 19:53
 */
@RestController
@RequestMapping("/picture")
public class PictureController {
    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @PostMapping("/delete")
    @AuthorityCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() < 0) {
            throw new BusinessException(PARAMS_ERROR, "请求错误");
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
            throw new BusinessException(PARAMS_ERROR, "请求错误");
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
            throw new BusinessException(PARAMS_ERROR, "请求错误");
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
            throw new BusinessException(PARAMS_ERROR, "请求错误");
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
    public BaseResponse<Page<Picture>> getPicturePage(@RequestBody PictureQuery pictureQuery) {
        if (pictureQuery == null) {
            throw new BusinessException(PARAMS_ERROR, "请求错误");
        }
        int current = pictureQuery.getCurrent();
        int size = pictureQuery.getPageSize();
        Page<Picture> picturePage = new Page<>(current, size);
        QueryWrapper<Picture> queryWrapper = pictureService.getQueryWrapper(pictureQuery);
        Page<Picture> picturePageResult = pictureService.page(picturePage, queryWrapper);
        return ResultUtils.success(picturePageResult);
    }

    @GetMapping("/list/VO/page")
    public BaseResponse<Page<PictureVO>> getPictureVOPage(@RequestBody PictureQuery pictureQuery, HttpServletRequest request) {
        if (pictureQuery == null || pictureQuery.getId() < 0) {
            throw new BusinessException(PARAMS_ERROR, "请求错误");
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
            throw new BusinessException(PARAMS_ERROR, "请求错误");
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
        ThrowUtils.throwIf(pictureReview == null, PARAMS_ERROR, "请求错误");
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(NOT_LOGIN_ERROR, "未登录");
        }
        pictureService.reviewPicture(pictureReview, loginUser);
        return ResultUtils.success(true);
    }
}
