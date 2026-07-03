package com.ibox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 平台用户实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ibox_platform_user")
public class PlatformUserEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 平台用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 仓库是否可见：1-可见，0-不可见
     */
    @TableField("warehouse_visible")
    private Integer warehouseVisible;
}
