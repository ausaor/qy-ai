package com.qy;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan(basePackages = {"com.qy.mapper"})
@SpringBootApplication
public class QyAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(QyAiApplication.class, args);
    }

}
