package com.guying;

import cn.xuyanwu.spring.file.storage.EnableFileStorage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFileStorage
@EnableScheduling
@EnableAsync
public class AiCourceAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiCourceAssistantApplication.class, args);
    }

}
