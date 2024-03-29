package net.sony.util;

import net.sony.dpt.network.SimpleHttpClient;
import net.sony.dpt.network.UncheckedHttpClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.MockServerRule;

import java.io.IOException;
import java.util.HashMap;

import static net.sony.util.HttpUtils.ok;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class SimpleHttpClientTest {

    private static final String JUNIT = "JUNIT";
    private static final String JSON = "{\"test\": \"junit\"}";
    @Rule
    public final MockServerRule mockServerRule = new MockServerRule(this);
    private MockServerClient mockServerClient;
    private String baseUrl;

    @Before
    public void setup() {
        baseUrl = "http://localhost:" + mockServerRule.getPort();
        mockServerClient
                .when(
                        request()
                                .withMethod("GET").withPath("/junit")
                ).respond(
                response()
                        .withBody(JUNIT)
        );

        mockServerClient
                .when(request()
                        .withMethod("PUT")
                        .withPath("/junitPut")
                ).respond(response().withBody(JSON)
        );
    }

    @Test
    public void verifyGetSendsStringBody() throws IOException, InterruptedException {
        SimpleHttpClient simpleHttpClient = UncheckedHttpClient.insecure();
        String response = simpleHttpClient.get(baseUrl + "/junit");

        assertThat(response, is(JUNIT));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void verifyOk() {
        for (int i = 0; i < 200; i++) {
            java.net.http.HttpResponse<String> mockResponse = mock(java.net.http.HttpResponse.class);
            when(mockResponse.statusCode()).thenReturn(i);
            assertThat(ok(mockResponse), is(false));
        }
        for (int i = 200; i < 300; i++) {
            java.net.http.HttpResponse<String> mockResponse = mock(java.net.http.HttpResponse.class);
            when(mockResponse.statusCode()).thenReturn(i);
            assertThat(ok(mockResponse), is(true));
        }
        for (int i = 300; i < 1000; i++) {
            java.net.http.HttpResponse<String> mockResponse = mock(java.net.http.HttpResponse.class);
            when(mockResponse.statusCode()).thenReturn(i);
            assertThat(ok(mockResponse), is(false));
        }
    }

    @Test
    public void verifyGetWithResponseReturnsResponse() throws IOException, InterruptedException {
        SimpleHttpClient simpleHttpClient = UncheckedHttpClient.insecure();
        java.net.http.HttpResponse<String> response = simpleHttpClient.getWithResponse(baseUrl + "/junit");

        assertThat(response.body(), is(JUNIT));
    }

    @Test
    public void verifyPutWithBodyReturnsStringBody() throws IOException, InterruptedException {
        SimpleHttpClient simpleHttpClient = UncheckedHttpClient.insecure();
        String response = simpleHttpClient.put(baseUrl + "/junitPut", new HashMap<>() {{
            put("param1", "value1");
        }});

        assertThat(response, is(JSON));
    }

    @Test
    public void verifyPutReturnsStringBody() throws IOException, InterruptedException {
        SimpleHttpClient simpleHttpClient = UncheckedHttpClient.insecure();
        String response = simpleHttpClient.put(baseUrl + "/junitPut");

        assertThat(response, is(JSON));
    }

}
