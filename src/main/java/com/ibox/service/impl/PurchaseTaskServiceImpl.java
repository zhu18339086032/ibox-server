package com.ibox.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ibox.entity.PurchaseTaskEntity;
import com.ibox.mapper.PurchaseTaskMapper;
import com.ibox.service.PurchaseTaskService;
import org.springframework.stereotype.Service;

/**
 * 抢购任务Service业务层处理
 */
@Service
public class PurchaseTaskServiceImpl extends ServiceImpl<PurchaseTaskMapper, PurchaseTaskEntity> implements PurchaseTaskService {
}
