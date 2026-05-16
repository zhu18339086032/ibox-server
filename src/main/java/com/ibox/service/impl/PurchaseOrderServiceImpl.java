package com.ibox.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ibox.entity.PurchaseOrderEntity;
import com.ibox.mapper.PurchaseOrderMapper;
import com.ibox.service.PurchaseOrderService;
import org.springframework.stereotype.Service;

/**
 * 抢购订单Service业务层处理
 */
@Service
public class PurchaseOrderServiceImpl extends ServiceImpl<PurchaseOrderMapper, PurchaseOrderEntity> implements PurchaseOrderService {
}
