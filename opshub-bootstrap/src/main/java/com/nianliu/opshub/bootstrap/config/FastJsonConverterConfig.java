package com.nianliu.opshub.bootstrap.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.http.converter.HttpMessageConverter;
import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;
import com.alibaba.fastjson2.support.config.FastJsonConfig;
import com.alibaba.fastjson2.JSONWriter;
import java.util.List;
@Configuration public class FastJsonConverterConfig implements WebMvcConfigurer {
 public void configureMessageConverters(List<HttpMessageConverter<?>> converters) { var converter=new FastJsonHttpMessageConverter(); var config=new FastJsonConfig(); config.setCharset(java.nio.charset.StandardCharsets.UTF_8);config.setDateFormat("yyyy-MM-dd HH:mm:ss");config.setWriterFeatures(JSONWriter.Feature.WriteLongAsString,JSONWriter.Feature.WriteMapNullValue);converter.setFastJsonConfig(config);converter.setSupportedMediaTypes(List.of(org.springframework.http.MediaType.APPLICATION_JSON,new org.springframework.http.MediaType("application","*+json")));converters.add(0,converter); }
}