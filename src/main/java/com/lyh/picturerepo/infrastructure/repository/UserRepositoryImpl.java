package com.lyh.picturerepo.infrastructure.repository;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lyh.picturerepo.domain.user.entity.User;
import com.lyh.picturerepo.domain.user.repository.UserRepository;
import com.lyh.picturerepo.infrastructure.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class UserRepositoryImpl extends ServiceImpl<UserMapper, User> implements UserRepository {
}
