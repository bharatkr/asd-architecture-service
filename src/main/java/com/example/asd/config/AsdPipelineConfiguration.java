package com.example.asd.config;

import com.example.asd.mcp.McpTool;
import com.example.asd.mcp.tools.AsdDocxAssembleTool;
import com.example.asd.mcp.tools.GitCloneTool;
import com.example.asd.mcp.tools.MavenModuleGraphTool;
import com.example.asd.mcp.tools.OpenApiIngestTool;
import com.example.asd.mcp.tools.RepositoryInventoryTool;
import com.example.asd.mcp.tools.SpringStereotypeScanTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AsdPipelineConfiguration {

  @Bean
  List<McpTool> asdMcpPipeline(
      GitCloneTool gitCloneTool,
      RepositoryInventoryTool repositoryInventoryTool,
      MavenModuleGraphTool mavenModuleGraphTool,
      SpringStereotypeScanTool springStereotypeScanTool,
      OpenApiIngestTool openApiIngestTool,
      AsdDocxAssembleTool asdDocxAssembleTool
  ) {
    return List.of(
        gitCloneTool,
        repositoryInventoryTool,
        mavenModuleGraphTool,
        springStereotypeScanTool,
        openApiIngestTool,
        asdDocxAssembleTool
    );
  }
}
