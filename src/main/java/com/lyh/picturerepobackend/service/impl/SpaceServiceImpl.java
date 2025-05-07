package com.lyh.picturerepobackend.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lyh.picturerepobackend.exception.BusinessException;
import com.lyh.picturerepobackend.exception.ErrorCode;
import com.lyh.picturerepobackend.exception.ThrowUtils;
import com.lyh.picturerepobackend.mapper.SpaceMapper;
import com.lyh.picturerepobackend.model.dto.space.SpaceAdd;
import com.lyh.picturerepobackend.model.entity.Space;
import com.lyh.picturerepobackend.model.entity.User;
import com.lyh.picturerepobackend.model.enums.SpaceLevelEnum;
import com.lyh.picturerepobackend.service.SpaceService;
import com.lyh.picturerepobackend.service.UserService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @author RAOYAO
 * @description 针对表【space(空间)】的数据库操作Service实现
 * @createDate 2025-04-27 21:01:44
 */
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space> implements SpaceService {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private UserService userService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR, "空间不能为空");
        //从对象取值
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        if (add) {
            if (StrUtil.isBlank(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            }
            if (spaceLevel == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            }
        }
        // 修改数据时，如果要改空间级别
        if (spaceLevel != null && spaceLevelEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
        if (StrUtil.isNotBlank(spaceName) && spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称过长");
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
        validSpace(space, true);
        fillSpaceBySpaceLevel(space);
        // 默认值
        if (StrUtil.isBlank(spaceAdd.getSpaceName())) {
            space.setSpaceName("默认空间");
        }
        if (spaceAdd.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        Long userId = loginUser.getId();
        space.setUserId(userId);
        if (SpaceLevelEnum.COMMON.getValue() != spaceAdd.getSpaceLevel() && userService.isAdmin(loginUser)) {
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
                    // Spring的编程式事务管理器，封装跟数据库有关的查询和更新。
                    // 不使用@Transactional注解是因为，保证事务的提交在锁的范围内
                    // 使用了redisson的分布式锁，保证事务的原子性
                    Long execute = transactionTemplate.execute(status -> {
                        boolean exists = this.lambdaQuery()
                                .eq(Space::getUserId, userId)
                                .exists();
                        ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "空间已存在");
                        // 保存数据
                        boolean save = this.save(space);
                        ThrowUtils.throwIf(!save, ErrorCode.OPERATION_ERROR, "空间创建失败");
                        return space.getId();
                    });
                    return Optional.ofNullable(execute).orElse(-1L);
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
        if (!space.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }
}