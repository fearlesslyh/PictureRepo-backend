package com.lyh.picturerepo.domain.space.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lyh.picturerepo.domain.space.service.SpaceDomainService;
import com.lyh.picturerepo.application.service.UserApplicationService;
import com.lyh.picturerepo.domain.user.entity.User;
import com.lyh.picturerepo.infrastructure.exception.BusinessException;
import com.lyh.picturerepo.infrastructure.exception.ErrorCode;
import com.lyh.picturerepo.infrastructure.exception.ThrowUtils;
import com.lyh.picturerepo.infrastructure.mapper.SpaceMapper;
import com.lyh.picturerepo.interfaces.vo.user.UserVO;
import com.lyh.picturerepobackend.manager.sharding.DynamicShardingManager;
import com.lyh.picturerepo.interfaces.dto.space.SpaceAdd;
import com.lyh.picturerepo.interfaces.dto.space.SpaceQuery;
import com.lyh.picturerepo.domain.space.entity.Space;
import com.lyh.picturerepo.domain.space.entity.SpaceUser;
import com.lyh.picturerepo.domain.space.valueObject.SpaceLevelEnum;
import com.lyh.picturerepo.domain.space.valueObject.SpaceRoleEnum;
import com.lyh.picturerepo.domain.space.valueObject.SpaceTypeEnum;
import com.lyh.picturerepo.interfaces.vo.space.SpaceVO;
import com.lyh.picturerepo.domain.space.service.SpaceUserDomainService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR, "空间不能为空");
        //从对象取值
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        Integer spaceType = space.getSpaceType();
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(spaceType);
// 创建时校验
        if (add) {
            if (StrUtil.isBlank(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            }
            if (spaceLevel == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            }
            if (spaceType == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类别不能为空");
            }
        }
        // 修改数据时，空间名称进行校验
        if (StrUtil.isNotBlank(spaceName) && spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称过长");
        }
        // 修改数据时，空间级别进行校验
        if (spaceLevel != null && spaceLevelEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
        // 修改数据时，空间类别进行校验
        if (spaceType != null && spaceTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类别不存在");
        }
    }

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
    public long addSpace(SpaceAdd spaceAdd, User loginUser) {
        Space space = new Space();
        BeanUtils.copyProperties(spaceAdd, space);
        // 默认值
        if (StrUtil.isBlank(spaceAdd.getSpaceName())) {
            space.setSpaceName("默认空间");
        }
        if (spaceAdd.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        if (space.getSpaceType() == null) {
            space.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        }
        // 填充容量和大小
        this.fillSpaceBySpaceLevel(space);
        // 2. 校验参数
        this.validSpace(space, true);
        // 3. 校验权限，非管理员只能创建普通级别的空间
        Long userId = loginUser.getId();
        space.setUserId(userId);
        if (SpaceLevelEnum.COMMON.getValue() != spaceAdd.getSpaceLevel() && (loginUser.isAdmin())) {
            // 非普通空间，需要管理员审核
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限创建指定的空间");
        }
        // 针对用户进行加锁
        String lockName = "space_add_" + userId;
        RLock lock = redissonClient.getLock(lockName);
        try {
            // 尝试获取锁，如果获取不到，会等待5秒，超时时间为10秒
            boolean getLockSuccess = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if (getLockSuccess) {
                try {
//                    // Spring的编程式事务管理器，封装跟数据库有关的查询和更新。
//                    // 不使用@Transactional注解是因为，保证事务的提交在锁的范围内
//                    // 使用了redisson的分布式锁，保证事务的原子性
//                    Long execute = transactionTemplate.execute(status -> {
//                        boolean exists = this.lambdaQuery()
//                                .eq(Space::getUserId, userId)
//                                .exists();
//                        ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "空间已存在");
//                        // 保存数据
//                        boolean save = this.save(space);
//                        ThrowUtils.throwIf(!save, ErrorCode.OPERATION_ERROR, "空间创建失败");
//                        return space.getId();
//                    });
//                    return Optional.ofNullable(execute).orElse(-1L);
                    Long newSpaceId = transactionTemplate.execute(status -> {
                        // 判断是否已有空间
                        boolean exists = this.lambdaQuery()
                                .eq(Space::getUserId, userId)
                                .eq(Space::getSpaceType, space.getSpaceType())
                                .exists();
                        // 如果已有空间，就不能再创建
                        ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "每个用户每类空间只能创建一个");
                        // 创建
                        boolean result = this.save(space);
                        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "保存空间到数据库失败");
                        // 创建成功后，如果是团队空间，关联新增团队成员记录
                        if (SpaceTypeEnum.TEAM.getValue() == space.getSpaceType()) {
                            SpaceUser spaceUser = new SpaceUser();
                            spaceUser.setSpaceId(space.getId());
                            spaceUser.setUserId(userId);
                            spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());
                            result = spaceUserDomainService.save(spaceUser);
                            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建团队成员记录失败");
                        }
                        // 创建分表（仅对团队空间生效）为方便部署，暂时不使用
                        dynamicShardingManager.createSpacePictureTable(space);
                        // 返回新写入的数据 id
                        return space.getId();
                    });
                    return Optional.ofNullable(newSpaceId).orElse(-1L);
                } finally {
                    lock.unlock();
                }
            } else {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取锁失败");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取锁时线程中断", e);
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
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }
        // 对象列表 => 封装对象列表
        List<SpaceVO> spaceVOList = spaceList.stream()
                .map(SpaceVO::objToVo)
                .collect(Collectors.toList());
        // 1. 关联查询用户信息
        // 1,2,3,4
        Set<Long> userIdSet = spaceList.stream().map(Space::getUserId).collect(Collectors.toSet());
        // 1 => user1, 2 => user2
        Map<Long, List<User>> userIdUserListMap = userApplicationService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            spaceVO.setUser(userApplicationService.getUserVO(user));
        });
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
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