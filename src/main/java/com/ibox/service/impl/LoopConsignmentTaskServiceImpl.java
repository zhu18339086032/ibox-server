package com.ibox.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ibox.entity.LoopConsignmentTaskEntity;
import com.ibox.mapper.LoopConsignmentTaskMapper;
import com.ibox.service.LoopConsignmentTaskService;
import org.springframework.stereotype.Service;

/**
 * 循环上下架任务 Service 实现
 */
@Service
public class LoopConsignmentTaskServiceImpl extends ServiceImpl<LoopConsignmentTaskMapper, LoopConsignmentTaskEntity> implements LoopConsignmentTaskService {
}
