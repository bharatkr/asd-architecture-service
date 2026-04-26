package com.example.asd.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiDocConfiguration {

  @Bean
  public OpenAPI asdOpenApi() {
    return new OpenAPI()
        .info(new Info()
            .title("ASD Architecture Service")
            .description("REST API documented in Swagger. Runs MCP-style tools and returns a Word (.docx) ASD. See docs/DESIGN.md.")
            .version("v1"));
  }
}
