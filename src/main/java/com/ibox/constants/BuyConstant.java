package com.ibox.constants;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BuyConstant {
    // id-价格
    public static Map<Long, BigDecimal> buyMap = new ConcurrentHashMap<>();

    // 图片地址-id
    public static Map<String, Long> picMap = new ConcurrentHashMap<>();
}
