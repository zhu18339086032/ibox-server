package com.ibox.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ibox.entity.ConsignmentTaskEntity;
import com.ibox.mapper.ConsignmentTaskMapper;
import com.ibox.service.ConsignmentTaskService;
import org.springframework.stereotype.Service;

/**
 * 寄售任务服务实现
 */
@Service
public class ConsignmentTaskServiceImpl extends ServiceImpl<ConsignmentTaskMapper, ConsignmentTaskEntity> implements ConsignmentTaskService {
}
