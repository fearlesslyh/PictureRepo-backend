package com.lyh.picturerepo.infrastructure.repository;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lyh.picturerepo.domain.picture.entity.Picture;
import com.lyh.picturerepo.domain.picture.repository.PictureRepository;
import com.lyh.picturerepo.infrastructure.mapper.PictureMapper;
import org.springframework.stereotype.Service;

@Service
public class PictureRepositoryImpl extends ServiceImpl<PictureMapper, Picture> implements PictureRepository {
}
