package com.nianliu.opshub.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ImportResource;

/**
 * OpsHub 启动入口（编码规范 §2.7.1）：全工程唯一可运行入口。
 *
 * <p>装配策略为混合装配：
 * <ul>
 *     <li>注解装配：组件扫描限定在 {@code com.nianliu.opshub} 根包，覆盖接口实现、领域、基础设施、横切能力；</li>
 *     <li>XML 装配：{@code classpath:spring/datasource.xml} 装配数据源与事务模板等基础设施 Bean。</li>
 * </ul>
 *
 * <p>主类刻意保持零业务逻辑，仅承担引导职责。
 */
@SpringBootApplication(scanBasePackages = "com.nianliu.opshub")
@EnableConfigurationProperties
@ImportResource(locations = {"classpath:spring/datasource.xml"})
public class OpsHubApplication {

    /**
     * 启动入口。
     *
     * @param args 启动参数，支持 {@code --spring.profiles.active=dev|test|prod} 覆盖默认环境
     */
    public static void run(String... args) {
        SpringApplication.run(OpsHubApplication.class, args);
    }

    public static void main(String[] args) {
        run(args);
    }
}