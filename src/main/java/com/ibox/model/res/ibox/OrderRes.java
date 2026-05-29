package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class OrderRes {

    private String orderUuid;

    private BigDecimal price;

    private ProductPreview productPreview;
}
