package org.shark.mentor.controller;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;
import org.shark.mentor.mcp.model.McpServer;

import java.util.UUID;

import static org.hamcrest.Matchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McpServerControllerIT {

    private final String baseUrl = "/api/mcp/servers";

    @BeforeAll
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = 3000;
        RestAssured.defaultParser = io.restassured.parsing.Parser.JSON;
    }

    private McpServer createServer(String name) {
        McpServer server = new McpServer();
        server.setId(UUID.randomUUID().toString());
        server.setName(name);
        server.setStatus("OFFLINE");
        return server;
    }

    @Test
    void testAddAndGetServer() {
        McpServer server = createServer("TestServer");
        String id = RestAssured.given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(server)
                .post(baseUrl)
                .then()
                .statusCode(200)
                .body("name", equalTo("TestServer"))
                .extract().path("id");

        RestAssured.given()
                .accept(ContentType.JSON)
                .get(baseUrl + "/" + id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id));
    }

    @Test
    void testGetAllServers() {
        RestAssured.given()
                .accept(ContentType.JSON)
                .get(baseUrl)
                .then()
                .statusCode(200)
                .body("$", isA(java.util.List.class));
    }

    @Test
    void testUpdateServerStatus() {
        McpServer server = createServer("StatusServer");
        String id = RestAssured.given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(server)
                .post(baseUrl)
                .then()
                .extract().path("id");

        RestAssured.given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("\"ONLINE\"")
                .put(baseUrl + "/" + id + "/status")
                .then()
                .statusCode(200)
                .body("status", equalTo("ONLINE"));
    }

    @Test
    void testConnectAndDisconnectServer() {
        McpServer server = createServer("ConnectServer");
        String id = RestAssured.given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(server)
                .post(baseUrl)
                .then()
                .extract().path("id");

        RestAssured.given()
                .accept(ContentType.JSON)
                .post(baseUrl + "/" + id + "/connect")
                .then()
                .statusCode(200);

        RestAssured.given()
                .accept(ContentType.JSON)
                .post(baseUrl + "/" + id + "/disconnect")
                .then()
                .statusCode(200);
    }

    @Test
    void testRemoveServer() {
        McpServer server = createServer("RemoveServer");
        String id = RestAssured.given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(server)
                .post(baseUrl)
                .then()
                .extract().path("id");

        RestAssured.given()
                .accept(ContentType.JSON)
                .delete(baseUrl + "/" + id)
                .then()
                .statusCode(200);

        RestAssured.given()
                .accept(ContentType.JSON)
                .get(baseUrl + "/" + id)
                .then()
                .statusCode(404);
    }

    @Test
    void testGetConnectionStatus() {
        RestAssured.given()
                .get(baseUrl + "/status")
                .then()
                .statusCode(200)
                .contentType("text/plain")
                .body(containsString("Connected:"));
    }
}