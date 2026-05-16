package com.ibox;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.ibox.mapper")
@EnableScheduling
public class IboxServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(IboxServerApplication.class, args);
    }

}
