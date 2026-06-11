package com.ibox.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ibox.entity.DesignatedPurchaseTaskEntity;
import com.ibox.mapper.DesignatedPurchaseTaskMapper;
import com.ibox.service.DesignatedPurchaseTaskService;
import org.springframework.stereotype.Service;

/**
 * 指定购买任务 Service 实现
 */
@Service
public class DesignatedPurchaseTaskServiceImpl extends ServiceImpl<DesignatedPurchaseTaskMapper, DesignatedPurchaseTaskEntity> implements DesignatedPurchaseTaskService {
}
