package com.ibox.model.res.web;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 通用分页响应对象
 *
 * @param <T> 列表数据类型
 */
@Data
public class PageResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前页码
     */
    private Long pageNum;

    /**
     * 每页条数
     */
    private Long pageSize;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 数据列表
     */
    private List<T> records;

    public static <T> PageResponse<T> of(Long pageNum, Long pageSize, Long total, List<T> records) {
        PageResponse<T> response = new PageResponse<>();
        response.setPageNum(pageNum);
        response.setPageSize(pageSize);
        response.setTotal(total);
        response.setRecords(records);
        return response;
    }

    public static <T> PageResponse<T> empty(Long pageNum, Long pageSize) {
        PageResponse<T> response = new PageResponse<>();
        response.setPageNum(pageNum);
        response.setPageSize(pageSize);
        response.setTotal(0L);
        response.setRecords(Collections.emptyList());
        return response;
    }
}
