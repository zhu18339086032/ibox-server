package com.ibox.manager;

import com.alibaba.fastjson.JSONObject;
import com.ibox.model.req.web.MarketCollectionPageReq;
import com.ibox.model.res.web.MarketCollectionListRes;
import com.ibox.model.res.web.PageResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 市场藏品服务测试
 */
@Slf4j
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MarketCollectionManagerTest {

    /**
     * 市场藏品服务
     */
    @Autowired
    private MarketCollectionManager marketCollectionManager;

    /**
     * 刷新市场列表测试
     */
    @Test
    @Order(1)
    void testRefresh() {
        Integer count = marketCollectionManager.refresh();
        log.info("【刷新市场列表测试】刷新数量:{}", count);
        assertNotNull(count, "刷新数量不能为空");
        assertTrue(count > 0, "刷新数量应大于0");
    }

    /**
     * 获取市场列表测试
     */
    @Test
    @Order(2)
    void testList() {
        
    }
}
