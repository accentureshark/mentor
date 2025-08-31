package org.shark.mentor.mcp.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.shark.mentor.mcp.application.service.formatting.ResponseFormatterService;
import org.shark.mentor.mcp.application.service.i18n.I18nService;
import org.shark.mentor.mcp.domain.model.McpServer;
import org.shark.mentor.mcp.infraestructure.config.UiProperties;

/**
 * Demonstration class showing the before/after formatting for the Polenta MCP Server schema response.
 * This addresses the problem statement about making schema responses more user-friendly.
 */
public class SchemaFormattingDemo {
    
    public static void main(String[] args) throws Exception {
        // Setup
        UiProperties uiProperties = new UiProperties();
        uiProperties.setLocale("en");
        I18nService i18nService = new I18nService(uiProperties);
        ResponseFormatterService responseFormatter = new ResponseFormatterService(i18nService);
        ObjectMapper objectMapper = new ObjectMapper();
        
        McpServer polentaServer = McpServer.builder()
                .id("polenta-local")
                .name("Polenta MCP Server (Local)")
                .url("http://localhost:3000")
                .status("CONNECTED")
                .build();
        
        // The exact raw JSON response from the problem statement
        String rawPolentaResponse = """
        {
          "result" : {
            "schemas" : [ "information_schema", "sf1", "sf100", "sf1000", "sf10000", "sf100000", "sf300", "sf3000", "sf30000", "tiny" ],
            "status" : "success"
          },
          "trace_id" : "ef058002-d944-4f31-aa28-75db894dd11f",
          "id" : "8138be7a-e0aa-483c-968b-d1dcc7f461cd",
          "jsonrpc" : "2.0"
        }
        """;

        String userMessage = "listame los esquemas";

        System.out.println("==== BEFORE: Raw JSON Response (Technical) ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                objectMapper.readTree(rawPolentaResponse)));

        System.out.println("\n==== AFTER: Enhanced User-Friendly Response ====");
        String formattedResponse = responseFormatter.tryFormatWithoutLlm(rawPolentaResponse, userMessage, polentaServer);
        System.out.println(formattedResponse);

        System.out.println("\n==== Summary of Improvements ====");
        System.out.println("✅ Removed technical metadata (trace_id, jsonrpc, id)");
        System.out.println("✅ Added clear visual icons and structure");
        System.out.println("✅ Enhanced readability for non-technical users");
        System.out.println("✅ Preserved all important information (schema names)");
        System.out.println("✅ Added context about what schemas are");
        System.out.println("✅ No LLM required - template-based formatting");
    }
}