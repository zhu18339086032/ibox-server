package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 寄售请求模型
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateConsignmentOrderReq {
    private Long digitalCollectionId;
    private BigDecimal price;
    private List<Integer> paymentPlatformCodes;
    private String consignPassword;

}
