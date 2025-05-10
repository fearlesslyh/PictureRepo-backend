package com.lyh.picturerepo.infrastructure.repository;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lyh.picturerepo.domain.space.entity.Space;
import com.lyh.picturerepo.domain.space.repository.SpaceRepository;
import com.lyh.picturerepo.infrastructure.mapper.SpaceMapper;
import org.springframework.stereotype.Service;

@Service
public class SpaceRepositoryImpl extends ServiceImpl<SpaceMapper, Space> implements SpaceRepository {
}
