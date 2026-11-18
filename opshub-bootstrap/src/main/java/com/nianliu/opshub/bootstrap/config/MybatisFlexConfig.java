package com.nianliu.opshub.bootstrap.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Flex 持久层装配（编码规范 §2.7.1 混合装配 / §20 数据访问）：
 *
 * <p>数据源与事务管理器由 {@code classpath:spring/datasource.xml} 装配，本类只负责
 * Mapper 接口的扫描注册，使 DAO 接口在容器启动时生成代理并注册为 Bean。
 *
 * <p>扫描范围限定在基础设施层 DAL 的 DAO 接口包，避免误扫业务包。
 * Mapper XML 统一放在 {@code opshub-infra-dal/src/main/resources/sqlmap} 下，
 * 与 {@code mybatis-flex.mapper-locations} 配置保持一致。
 */
@Configuration
@MapperScan(basePackages = "com.nianliu.opshub.infra.dal.daointerface")
public class MybatisFlexConfig {

}