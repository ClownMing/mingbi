package com.yupi.springbootinit.bizmq;

import com.rabbitmq.client.Channel;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.manager.AiManager;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.service.ChartService;
import com.yupi.springbootinit.utils.ExcelUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.annotations.Result;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import static com.yupi.springbootinit.config.MqConfig.BI_QUEUE_NAME;
import static com.yupi.springbootinit.constant.CommonConstant.BI_MODEL_ID;


/**
 * @author clownMing
 * @version 1.0
 * @description 生产者代码
 * @date 2023/6/19 16:40
 */
@Slf4j
@Component
public class BIMessageConsumer {

    @Resource
    private ChartService chartService;

    @Resource
    private AiManager aiManager;


    @SneakyThrows(value = Exception.class)
    @RabbitListener(queues = BI_QUEUE_NAME, ackMode = "MANUAL")
    public void receive(String message,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        if (StringUtils.isBlank(message)) {
            // 手动进行消息 nack
            channel.basicNack(deliveryTag, false, false);
            throw  new BusinessException(ErrorCode.SYSTEM_ERROR, "消息为空");
        }
        long chartId = Long.parseLong(message);
        Chart chart = chartService.getById(chartId);
        if (chart == null) {
            channel.basicNack(deliveryTag, false, false);
            throw  new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图表为空");
        }
        // 先修改图表任务状态为 "执行中" 等执行成功后，修改为 "已完成" 保存执行结果 执行失败后，状态修改为失败，记录任务失败信息
        Chart updateChart = new Chart();
        updateChart.setId(chart.getId());
        updateChart.setStatus("running");
        boolean b = chartService.updateById(updateChart);
        if (!b) {
            channel.basicNack(deliveryTag, false, false);
            handleChartUpdateError(chart.getId(), "更新图表执行中状态失败");
        }
        // 调用AI
        String answer = aiManager.doChat(BI_MODEL_ID, buildUserInput(chart));
        String[] splits = answer.split("【【【【【");
        if (splits.length < 3) {
            channel.basicNack(deliveryTag, false, false);
            handleChartUpdateError(chart.getId(), "AI生成错误");
            return;
        }
        String genChart = splits[1].trim();
        String genResult = splits[2].trim();

        Chart updateChart2 = new Chart();
        updateChart2.setId(chart.getId());
        updateChart2.setStatus("succeed");
        updateChart2.setGenChart(genChart);
        updateChart2.setGenResult(genResult);
        boolean b2 = chartService.updateById(updateChart2);
        if (!b2) {
            channel.basicNack(deliveryTag, false, false);
            handleChartUpdateError(chart.getId(), "更新图表成功状态失败");
        }

        // 手动进行ack
        channel.basicAck(deliveryTag, false);
    }


    private void handleChartUpdateError(long chartId, String execMessage) {
        Chart updateChart2 = new Chart();
        updateChart2.setId(chartId);
        updateChart2.setStatus("failed");
        updateChart2.setExecMessage(execMessage);
        boolean b2 = chartService.updateById(updateChart2);
        if (!b2) {
            log.error("更新图表失败状态失败" + chartId + "," + execMessage);
        }
    }


    /**
     * 构造用户输入
     * @param chart
     * @return
     */
    private String buildUserInput(Chart chart) {

        String goal = chart.getGoal();
        String chartType = chart.getChartType();
        String csvData = chart.getChartData();
        // 构造用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求:").append("\n");
        // 拼接分析目标
        String userGoal = goal;
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += ",请使用" + chartType;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据:").append("\n");
        // 压缩后的数据
        userInput.append(csvData).append("\n");
        return userInput.toString();
    }

}
