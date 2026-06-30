package com.ibox.manager;

import com.ibox.model.req.web.ConsignmentOrderAnalysisReq;
import com.ibox.model.res.web.ConsignmentOrderAnalysisRes;
import com.ibox.model.res.web.ConsignmentOrderAnalysisTaskRes;

/**
 * 市场分析管理接口
 */
public interface MarketAnalysisManager {
    ConsignmentOrderAnalysisRes analyzeConsignmentOrders(ConsignmentOrderAnalysisReq req);

    ConsignmentOrderAnalysisTaskRes startAnalyzeConsignmentOrders(ConsignmentOrderAnalysisReq req);

    ConsignmentOrderAnalysisTaskRes getAnalyzeTask(String taskId);
}
