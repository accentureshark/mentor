package org.shark.mentor.mcp.controller;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shark.mentor.mcp.IntegrationTestConfig;
import org.shark.mentor.mcp.McpClientApplication;
import org.shark.mentor.mcp.model.McpRequest;
import org.shark.mentor.mcp.model.McpServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@SpringBootTest(classes = McpClientApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(IntegrationTestConfig.class)
@Tag("integration")
class ChatControllerIntegrationTest {

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void sendMessageAndRetrieveConversation() {
        String serverId = "chat-stdio";
        McpServer server = new McpServer(serverId, "Chat Server", "desc", "stdio://cat", "DISCONNECTED");

        given()
            .contentType("application/json")
            .body(server)
        .when()
            .post("/api/mcp/servers")
        .then()
            .statusCode(200);

        given()
        .when()
            .post("/api/mcp/servers/{id}/connect", serverId)
        .then()
            .statusCode(200)
            .body("status", equalTo("CONNECTED"));

        McpRequest request = McpRequest.builder()
                .serverId(serverId)
                .message("hello")
                .conversationId("conv1")
                .build();

        given()
            .contentType("application/json")
            .body(request)
        .when()
            .post("/api/mcp/chat/send")
        .then()
            .statusCode(200)
            .body("role", equalTo("ASSISTANT"))
            .body("serverId", equalTo(serverId))
            .body("content", equalTo("test-response"));

        given()
        .when()
            .get("/api/mcp/chat/conversations/conv1")
        .then()
            .statusCode(200)
            .body("size()", equalTo(2));
    }
}
