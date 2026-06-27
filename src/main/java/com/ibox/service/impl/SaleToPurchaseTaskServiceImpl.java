package com.ibox.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ibox.entity.SaleToPurchaseTaskEntity;
import com.ibox.mapper.SaleToPurchaseTaskMapper;
import com.ibox.service.SaleToPurchaseTaskService;
import org.springframework.stereotype.Service;

/**
 * 出售求购任务 Service 实现
 */
@Service
public class SaleToPurchaseTaskServiceImpl extends ServiceImpl<SaleToPurchaseTaskMapper, SaleToPurchaseTaskEntity> implements SaleToPurchaseTaskService {
}
