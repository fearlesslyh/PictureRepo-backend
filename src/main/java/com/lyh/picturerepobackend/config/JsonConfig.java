package com.lyh.picturerepobackend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.jackson.JsonComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/11 16:14
 */

/**
 * Spring MVC Json 配置类
 *
 * 这个类使用 Spring 的 `@JsonComponent` 注解，表明它是一个与 JSON 处理相关的组件，
 * 并且会被 Spring 容器自动扫描和管理。
 */
@JsonComponent
public class JsonConfig {
    /**
     * 配置 Jackson ObjectMapper，解决 Long 类型转换为 JSON 时的精度丢失问题。
     *
     * JavaScript 的 Number 类型无法精确表示过大的 Long 型数值，会导致末尾数字变为 0。
     * 这个方法通过自定义 Jackson 的序列化器，将 Long 类型的数据在转换为 JSON 字符串时
     * 显式地转换为 String 类型，从而避免精度丢失。
     *
     * @param builder Spring Boot 提供的 Jackson2ObjectMapperBuilder，用于构建 ObjectMapper。
     * @return 配置好的 ObjectMapper 实例。
     */
    @Bean
    public ObjectMapper jsonMapper(Jackson2ObjectMapperBuilder builder) {
        // 使用 builder 创建 ObjectMapper 实例，这里禁用了 XML 处理，专注于 JSON。
        ObjectMapper build = builder.createXmlMapper(false).build();
        // 创建一个简单的模块，用于注册自定义的序列化器。
        SimpleModule simpleModule = new SimpleModule();

        // 为 Long 类注册 ToStringSerializer。
        // ToStringSerializer 是 Jackson 内置的序列化器，它会将任何对象转换为其 toString() 方法的返回值。
        // 对于 Long 对象，toString() 会返回其数值的字符串表示。
        simpleModule.addSerializer(Long.class, ToStringSerializer.instance);

        // 同时为基本类型 long 也注册 ToStringSerializer。
        // 这样做可以确保无论是使用 Long 对象还是 long 基本类型，都能被正确地序列化为字符串。
        simpleModule.addSerializer(Long.TYPE, ToStringSerializer.instance);

        // 将配置好的模块注册到 ObjectMapper 中，使其生效。
        build.registerModule(simpleModule);
        // 返回配置好的 ObjectMapper 实例，Spring 会使用这个 ObjectMapper 来处理 JSON 序列化。

        return build;
    }
}
