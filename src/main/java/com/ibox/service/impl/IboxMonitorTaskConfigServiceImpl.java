package com.ibox.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ibox.entity.IboxMonitorTaskConfigEntity;
import com.ibox.mapper.IboxMonitorTaskConfigMapper;
import com.ibox.service.IboxMonitorTaskConfigService;
import org.springframework.stereotype.Service;

/**
 * 监控任务配置Service业务层处理
 */
@Service
public class IboxMonitorTaskConfigServiceImpl extends ServiceImpl<IboxMonitorTaskConfigMapper, IboxMonitorTaskConfigEntity> implements IboxMonitorTaskConfigService {
}
