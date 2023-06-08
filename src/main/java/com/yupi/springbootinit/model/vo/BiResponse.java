package com.yupi.springbootinit.model.vo;

import lombok.Data;

/**
 * @description Bi 的返回结果
 * @author clownMing
 * @date 2023/6/4 22:27
 * @version 1.0
*/
@Data
public class BiResponse {

    private String genChart;


    private String genResult;

    private Long chartId;
}
