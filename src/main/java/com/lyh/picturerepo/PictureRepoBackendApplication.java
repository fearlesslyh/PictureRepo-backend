package com.lyh.picturerepo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan("com.lyh.picturerepo.infrastructure.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
@EnableAsync
public class PictureRepoBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PictureRepoBackendApplication.class, args);
    }

}
