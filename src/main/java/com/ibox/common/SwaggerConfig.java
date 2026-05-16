package com.ibox.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger配置
 */
@Configuration
public class SwaggerConfig {

    /**
     * OpenAPI配置
     *
     * @return OpenAPI
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ibox-server API")
                        .description("ibox-server 接口文档")
                        .version("1.0.0")
                        .contact(new Contact().name("ibox-server"))
                        .license(new License().name("Apache 2.0")));
    }
}
