package com.wms.check;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableAsync：SecondVerificationListener 上的 @Async 要靠它才生效，不然事件监听方法
// 还是在触发提交的那个线程里同步跑，起不到"不阻塞扫码请求"的作用。
// @EnableScheduling：SecondVerificationRetryJob 上的 @Scheduled 定时补偿任务要靠它才会被调度。
// @ConfigurationPropertiesScan：让 ai.* 配置能绑定到 AiProperties 这个类型安全的 Bean 上，
// 不用在每个用到的地方写 @Value("${ai.xxx}")。
@SpringBootApplication
@EnableFeignClients
@EnableAsync
@EnableScheduling
@ConfigurationPropertiesScan
public class CheckServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CheckServiceApplication.class, args);
    }

}
