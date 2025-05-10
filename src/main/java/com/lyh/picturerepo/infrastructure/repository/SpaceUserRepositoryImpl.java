package com.lyh.picturerepo.infrastructure.repository;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lyh.picturerepo.domain.space.entity.SpaceUser;
import com.lyh.picturerepo.domain.space.repository.SpaceUserRepository;
import com.lyh.picturerepo.infrastructure.mapper.SpaceUserMapper;
import org.springframework.stereotype.Service;

@Service
public class SpaceUserRepositoryImpl extends ServiceImpl<SpaceUserMapper, SpaceUser> implements SpaceUserRepository {
}
