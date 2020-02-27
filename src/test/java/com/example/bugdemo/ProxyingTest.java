package com.example.bugdemo;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
// RANDOM_PORT => we want the full "experience" to be sure that everything works even on the "wire"
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ProxyingTest {

    @LocalServerPort
    private int port;
    private static WireMockServer wireMockServer;

    @SneakyThrows
    @BeforeAll
    public static void setupClass() {
        wireMockServer = new WireMockServer(48080);
        wireMockServer.start();
        configureFor("localhost", 48080);
        Thread.sleep(2875); // NOSONAR wiremock takes some time to come really up sometimes (WTF!)
    }

    @SneakyThrows
    @AfterEach
    public void cleanupAfterTest() {
        wireMockServer.resetAll();
    }

    @AfterAll
    public static void teardownClass() {
        wireMockServer.stop();
    }

    String baseUri() {
        return "http://localhost:" + port;
    }

    @Test
    void serverIsAlive() {
        ExtractableResponse<Response> response = given()
                .when()
                .get(baseUri() + "/internal/info")
                .then()
                .extract();
        assertThat(response.statusCode()).isEqualTo(200);
    }

    ExtractableResponse<Response> postPayloadToEndpoint(String uri, String body) {
        return given().log().all()
                .contentType("application/json; charset=UTF-8")
                .body(body)
                .when()
                .post(baseUri() + uri).prettyPeek()
                .then()
                .extract();
    }

    @Test
    void correlationIdIsAdded() {
        String responseBody = "{\n  \"status\": \"need coffee\"\n}";
        stubFor(post(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withBody(responseBody)
                )
        );
        System.out.println("S1: " + wireMockServer.getRecordingStatus().getStatus());

        String body = "{\"goal\":\"world-domination\"}";
        ExtractableResponse<Response> response = postPayloadToEndpoint("/my-api", body);

        assertThat(response.statusCode()).isEqualTo(200);
        verify(postRequestedFor(urlMatching("/"))
                .withRequestBody(equalTo(body))
                .withHeader("X-CORRELATION-ID", matching(".+")));
    }
}
