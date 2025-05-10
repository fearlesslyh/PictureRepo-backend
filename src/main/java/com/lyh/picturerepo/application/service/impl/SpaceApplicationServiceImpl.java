package com.lyh.picturerepo.application.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lyh.picturerepo.application.service.SpaceApplicationService;
import com.lyh.picturerepo.application.service.UserApplicationService;
import com.lyh.picturerepo.domain.space.entity.Space;
import com.lyh.picturerepo.domain.space.entity.SpaceUser;
import com.lyh.picturerepo.domain.space.service.SpaceDomainService;
import com.lyh.picturerepo.domain.space.service.SpaceUserDomainService;
import com.lyh.picturerepo.domain.space.valueObject.SpaceLevelEnum;
import com.lyh.picturerepo.domain.space.valueObject.SpaceRoleEnum;
import com.lyh.picturerepo.domain.space.valueObject.SpaceTypeEnum;
import com.lyh.picturerepo.domain.user.entity.User;
import com.lyh.picturerepo.infrastructure.exception.BusinessException;
import com.lyh.picturerepo.infrastructure.exception.ErrorCode;
import com.lyh.picturerepo.infrastructure.exception.ThrowUtils;
import com.lyh.picturerepo.infrastructure.mapper.SpaceMapper;
import com.lyh.picturerepo.interfaces.dto.space.SpaceAdd;
import com.lyh.picturerepo.interfaces.dto.space.SpaceQuery;
import com.lyh.picturerepo.interfaces.vo.space.SpaceVO;
import com.lyh.picturerepo.interfaces.vo.user.UserVO;
import com.lyh.picturerepobackend.manager.sharding.DynamicShardingManager;
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
public class SpaceApplicationServiceImpl implements SpaceApplicationService {

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

    @Resource
    private SpaceDomainService spaceDomainService;


    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        spaceDomainService.fillSpaceBySpaceLevel(space);
    }

    @Override
    public long addSpace(SpaceAdd spaceAdd, User loginUser) {
        Space space = new Space();
        BeanUtils.copyProperties(spaceAdd, space);
        // 默认值
        // 填充容量和大小
        this.fillSpaceBySpaceLevel(space);
        // 2. 校验参数
        space.validSpace(true);
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
                        boolean exists = spaceDomainService.lambdaQuery()
                                .eq(Space::getUserId, userId)
                                .eq(Space::getSpaceType, space.getSpaceType())
                                .exists();
                        // 如果已有空间，就不能再创建
                        ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "每个用户每类空间只能创建一个");
                        // 创建
                        boolean result = spaceDomainService.save(space);
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
        spaceDomainService.checkSpaceAuth(loginUser, space);
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
        return spaceDomainService.getQueryWrapper(spaceQuery);
    }
}