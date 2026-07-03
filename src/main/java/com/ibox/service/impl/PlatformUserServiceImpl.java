package com.ibox.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ibox.entity.PlatformUserEntity;
import com.ibox.mapper.PlatformUserMapper;
import com.ibox.service.PlatformUserService;
import org.springframework.stereotype.Service;

/**
 * 平台用户Service实现
 */
@Service
public class PlatformUserServiceImpl extends ServiceImpl<PlatformUserMapper, PlatformUserEntity> implements PlatformUserService {
}
