package com.yupi.springbootinit.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author clownMing
 * @version 1.0
 * @description TODO
 * @date 2023/6/19 17:06
 */
@Configuration
public class MqConfig {
    /**
     * 交换价
     */
    public static final String BI_EXCHANGE_NAME = "bi_exchange";

    /**
     * 队列
     */
    public static final String BI_QUEUE_NAME = "bi_queue";

    /**
     * routingKey
     */
    public static final String BI_ROUTING_KEY = "bi_routing_key";


    /**
     * 声明交换机
     */
    @Bean("biExchange")
    public DirectExchange biExchange() {
        return ExchangeBuilder.directExchange(BI_EXCHANGE_NAME).durable(true).build();
    }

    /**
     * 声明队列
     */
    @Bean("biQueue")
    public Queue biQueue() {
        return QueueBuilder.durable(BI_QUEUE_NAME).build();
    }

    /**
     * 进行绑定
     */
    @Bean
    public Binding queueBindingExchange(@Qualifier("biExchange") DirectExchange exchange,
                                        @Qualifier("biQueue") Queue queue) {
        return BindingBuilder.bind(queue).to(exchange).with(BI_ROUTING_KEY);
    }

}
