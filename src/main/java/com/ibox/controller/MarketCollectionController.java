package com.ibox.controller;

import com.ibox.manager.MarketCollectionManager;
import com.ibox.model.req.web.MarketCollectionPageReq;
import com.ibox.model.res.web.MarketCollectionListRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.model.res.web.WebResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 市场藏品控制器
 */
@RestController
@RequestMapping("/ibox/market")
@Slf4j
@Tag(name = "市场藏品")
public class MarketCollectionController {

    @Autowired
    private MarketCollectionManager marketCollectionManager;

    /**
     * 全量获取市场列表
     *
     * @return 市场列表
     */
    @GetMapping("/list")
    @Operation(summary = "全量获取市场列表")
    public WebResponse<List<MarketCollectionListRes>> list() {
        log.info("【全量获取市场列表】开始执行");
        return WebResponse.success(marketCollectionManager.list());
    }

    /**
     * 分页获取市场列表
     *
     * @param req 分页查询参数
     * @return 分页结果
     */
    @GetMapping("/page")
    @Operation(summary = "分页获取市场列表")
    public WebResponse<PageResponse<MarketCollectionListRes>> page(MarketCollectionPageReq req) {
        log.info("【分页获取市场列表】请求参数: pageNum={}, pageSize={}, name={}", req.getPageNum(), req.getPageSize(), req.getName());
        return WebResponse.success(marketCollectionManager.page(req));
    }

    /**
     * 刷新市场列表
     *
     * @return 刷新数量
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新市场列表")
    public WebResponse<Integer> refresh() {
        log.info("【刷新市场列表】开始执行");
        return WebResponse.success(marketCollectionManager.refresh());
    }
}
