package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 列表返回明细模型
 */
import java.math.BigDecimal;
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDigitalCollectionItemRes {
    private Integer consignmentStatus;
    private String coverPicUrl;
    private Integer digitalCollectionStatus;
    private Integer enableFreeMarket;
    private Integer enableUpgrade;
    private String fileUrl;
    private String headerPicUrl;
    private String holdAt;
    private Integer holdDay;
    private Long id;
    private Integer inkScreenStatus;
    private Integer level;
    private String name;
    private Long orderId;
    private String ownedAt;
    private Integer platformId;
    private BigDecimal price;
    private Integer syntheticStatus;
    private Integer thirdTransfer;
    private String thirdTransferTip;
    private Long tokenId;
    private Integer upgrade;
}
