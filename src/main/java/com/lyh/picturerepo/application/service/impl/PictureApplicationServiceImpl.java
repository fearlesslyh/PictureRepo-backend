package com.lyh.picturerepo.application.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lyh.picturerepo.application.service.PictureApplicationService;
import com.lyh.picturerepo.application.service.UserApplicationService;
import com.lyh.picturerepo.domain.picture.entity.Picture;
import com.lyh.picturerepo.domain.picture.service.PictureDomainService;
import com.lyh.picturerepo.domain.user.entity.User;
import com.lyh.picturerepo.infrastructure.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.lyh.picturerepo.infrastructure.exception.BusinessException;
import com.lyh.picturerepo.infrastructure.mapper.PictureMapper;
import com.lyh.picturerepo.interfaces.dto.picture.*;
import com.lyh.picturerepo.interfaces.vo.picture.PictureVO;
import com.lyh.picturerepo.interfaces.vo.user.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.lyh.picturerepo.infrastructure.exception.ErrorCode.PARAMS_ERROR;

/**
 * @author RAOYAO
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2025-04-14 19:49:22
 */
@Service
@Slf4j
public class PictureApplicationServiceImpl extends ServiceImpl<PictureMapper, Picture> implements PictureApplicationService {
    @Resource
    private UserApplicationService userApplicationService;

    @Resource
    private PictureDomainService pictureDomainService;

    public PictureVO uploadPicture(Object inputPicture, PictureUpload pictureUpload, User loginUser) {
        return pictureDomainService.uploadPicture(inputPicture, pictureUpload, loginUser);
    }

    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQuery pictureQuery) {
        return pictureDomainService.getQueryWrapper(pictureQuery);
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
            User user = userApplicationService.getUserById(userId);
            UserVO userVO = userApplicationService.getUserVO(user);
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
        Map<Long, List<User>> userIdUserListMap = userApplicationService.listByIds(userIdSet)
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
            UserVO userVO = userApplicationService.getUserVO(user);
            pictureVO.setUser(userVO);
        });
        //将转换后的pictureVOList设置到pictureVOPage中
        pictureVOPage.setRecords(pictureVOList);
        //返回pictureVOPage
        return pictureVOPage;
    }

    @Override
    public void reviewPicture(PictureReview pictureReview, User loginUser) {
        pictureDomainService.reviewPicture(pictureReview, loginUser);
    }

    @Override
    public void setReviewStatus(Picture picture, User loginUser) {
        pictureDomainService.setReviewStatus(picture, loginUser);
    }

    @Override
    public void editPicture(Picture picture, User loginUser) {
        pictureDomainService.editPicture(picture, loginUser);
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
        return pictureDomainService.uploadPictureByBatch(pictureUploadByBatch, loginUser);
    }

    @Async
    @Override
    public void clearPictureFile(Picture oldPicture) {
        pictureDomainService.clearPictureFile(oldPicture);
    }

//    @Override
//    // picture是要进行修改的图片，loginUser是当前登录用户
//    public void checkPictureAuthority(Picture picture, User loginUser) {
//        Long userId = loginUser.getId();
//        Long spaceId = picture.getSpaceId();
//        if (spaceId == null) {
//            // 说明是公共空间，只有本人或管理员可以修改
//            if (!userService.isAdmin(loginUser) && !userId.equals(picture.getUserId())) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "你没有权限修改图片，这是公共空间，只有本人或管理员才可以修改");
//            }
//        } else {
//            // 说明是私有空间，只有空间创建者才可以修改
//            if (!userId.equals(picture.getUserId())) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "你没有权限修改图片，这是私有空间，只有本人才可以修改");
//            }
//        }
//    }

    @Override
    public void deletePicture(long pictureId, User loginUser) {
        pictureDomainService.deletePicture(pictureId, loginUser);
    }

    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        return pictureDomainService.searchPictureByColor(spaceId, picColor, loginUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editPictureByBatch(PictureEditByBatch pictureEditByBatch, User loginUser) {
        pictureDomainService.editPictureByBatch(pictureEditByBatch, loginUser);
    }

    /**
     * 批量编辑图片分类和标签
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchEditPictureMetadata(PictureEditByBatch pictureEditByBatch, Long spaceId, Long loginUserId) {
        pictureDomainService.batchEditPictureMetadata(pictureEditByBatch, spaceId, loginUserId);
    }

    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTask createPictureOutPaintingTask, User loginUser) {
        return pictureDomainService.createPictureOutPaintingTask(createPictureOutPaintingTask, loginUser);
    }

    @Override
    public void validPicture(Picture picture) {
        if (picture == null){
            throw new BusinessException(PARAMS_ERROR, "图片不能为空");
        }
        picture.validPicture();
    }


    /**
     * nameRule 格式：图片{序号}
     *
     * @param pictureList
     * @param nameRule
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        pictureDomainService.fillPictureWithNameRule(pictureList, nameRule);
    }
}