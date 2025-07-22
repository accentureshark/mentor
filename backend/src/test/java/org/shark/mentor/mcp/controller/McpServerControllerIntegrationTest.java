package org.shark.mentor.mcp.controller;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shark.mentor.mcp.IntegrationTestConfig;
import org.shark.mentor.mcp.McpClientApplication;
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
class McpServerControllerIntegrationTest {

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void addConnectAndRemoveServer() {
        String serverId = "test-stdio";
        McpServer server = new McpServer(serverId, "Test Server", "desc", "stdio://cat", "DISCONNECTED");

        given()
            .contentType("application/json")
            .body(server)
        .when()
            .post("/api/mcp/servers")
        .then()
            .statusCode(200)
            .body("id", equalTo(serverId));

        given()
        .when()
            .post("/api/mcp/servers/{id}/connect", serverId)
        .then()
            .statusCode(200)
            .body("status", equalTo("CONNECTED"));

        given()
        .when()
            .post("/api/mcp/servers/{id}/disconnect", serverId)
        .then()
            .statusCode(200)
            .body("status", equalTo("DISCONNECTED"));

        given()
        .when()
            .delete("/api/mcp/servers/{id}", serverId)
        .then()
            .statusCode(200);
    }
}
