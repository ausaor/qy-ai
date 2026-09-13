package com.qy.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.qy.entity.User;
import com.qy.mapper.UserMapper;
import com.qy.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
}
