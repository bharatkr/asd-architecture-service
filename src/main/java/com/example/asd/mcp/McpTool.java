package com.example.asd.mcp;

/**
 * In-process MCP-style tool. A real MCP server would expose these over JSON-RPC;
 * here the orchestrator invokes them directly after the REST layer receives the request.
 */
public interface McpTool {

  String name();

  String description();

  /** Run the tool; mutate {@code ctx} with outputs. */
  void execute(McpToolContext ctx) throws Exception;
}
