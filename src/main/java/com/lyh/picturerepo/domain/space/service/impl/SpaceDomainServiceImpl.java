package com.lyh.picturerepo.domain.space.service.impl;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lyh.picturerepo.application.service.UserApplicationService;
import com.lyh.picturerepo.domain.space.entity.Space;
import com.lyh.picturerepo.domain.space.service.SpaceDomainService;
import com.lyh.picturerepo.domain.space.service.SpaceUserDomainService;
import com.lyh.picturerepo.domain.space.valueObject.SpaceLevelEnum;
import com.lyh.picturerepo.domain.user.entity.User;
import com.lyh.picturerepo.infrastructure.exception.BusinessException;
import com.lyh.picturerepo.infrastructure.exception.ErrorCode;
import com.lyh.picturerepo.infrastructure.mapper.SpaceMapper;
import com.lyh.picturerepo.interfaces.dto.space.SpaceQuery;
import com.lyh.picturerepo.interfaces.vo.space.SpaceVO;
import com.lyh.picturerepo.interfaces.vo.user.UserVO;
import com.lyh.picturerepo.shared.sharding.DynamicShardingManager;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * @author RAOYAO
 * @description 针对表【space(空间)】的数据库操作Service实现
 * @createDate 2025-04-27 21:01:44
 */
@Service
public class SpaceDomainServiceImpl extends ServiceImpl<SpaceMapper, Space> implements SpaceDomainService {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private UserApplicationService userApplicationService;

    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private SpaceUserDomainService spaceUserDomainService;
    @Resource
    @Lazy
    private DynamicShardingManager dynamicShardingManager;


    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        // 根据空间级别，自动填充限额
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (spaceLevelEnum != null) {
            long maxSize = spaceLevelEnum.getMaxSize();
            if (space.getMaxSize() == null) {
                space.setMaxSize(maxSize);
            }
            long maxCount = spaceLevelEnum.getMaxCount();
            if (space.getMaxCount() == null) {
                space.setMaxCount(maxCount);
            }
        }

    }


    @Override
    public void checkSpaceAuth(User loginUser, Space space) {
        // 仅本人或管理员可编辑
        if (!space.getUserId().equals(loginUser.getId()) && !(loginUser.isAdmin())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }

    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        // 对象转封装类
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        // 关联查询用户信息
        Long userId = space.getUserId();
        if (userId != null && userId > 0) {
            User user = userApplicationService.getUserById(userId);
            UserVO userVO = userApplicationService.getUserVO(user);
            spaceVO.setUser(userVO);
        }
        return spaceVO;
    }

    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQuery spaceQuery) {
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        if (spaceQuery == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = spaceQuery.getId();
        Long userId = spaceQuery.getUserId();
        String spaceName = spaceQuery.getSpaceName();
        Integer spaceLevel = spaceQuery.getSpaceLevel();
        Integer spaceType = spaceQuery.getSpaceType();
        String sortField = spaceQuery.getSortField();
        String sortOrder = spaceQuery.getSortOrder();

        // 拼接查询条件
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceType), "spaceType", spaceType);
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }
}