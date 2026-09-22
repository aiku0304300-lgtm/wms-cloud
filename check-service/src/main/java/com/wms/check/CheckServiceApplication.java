package com.wms.check;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableAsync：SecondVerificationListener 上的 @Async 要靠它才生效，不然事件监听方法
// 还是在触发提交的那个线程里同步跑，起不到"不阻塞扫码请求"的作用。
// @EnableScheduling：SecondVerificationRetryJob 上的 @Scheduled 定时补偿任务要靠它才会被调度。
@SpringBootApplication
@EnableFeignClients
@EnableAsync
@EnableScheduling
public class CheckServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CheckServiceApplication.class, args);
    }

}
