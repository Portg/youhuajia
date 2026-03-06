package com.youhua;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.youhua.**.mapper")
@EnableScheduling
public class YouHuaJiaApplication {

    public static void main(String[] args) {
        SpringApplication.run(YouHuaJiaApplication.class, args);
    }
}
