package com.wshake.infra.videogen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wshake.common.exception.BizException;
import com.wshake.service.port.VideoGenerationPort.VideoGenCommand;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

class XaiVideoAdapterTest {

    @Test
    void generate_pollsUntilDone() {
        AtomicInteger polls = new AtomicInteger();
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request req = chain.request();
                    String path = req.url().encodedPath();
                    if ("POST".equals(req.method()) && path.endsWith("/videos/generations")) {
                        return json(req, 200, "{\"request_id\":\"rid-1\"}");
                    }
                    if ("GET".equals(req.method()) && path.contains("/videos/")) {
                        if (polls.incrementAndGet() < 2) {
                            return json(req, 200, "{\"status\":\"pending\"}");
                        }
                        return json(
                                req,
                                200,
                                "{\"status\":\"done\",\"video\":{\"url\":\"https://cdn.x.ai/out.mp4\",\"duration\":5}}");
                    }
                    return json(req, 404, "{}");
                })
                .build();
        VideoGenProperties props = new VideoGenProperties();
        props.setPollInterval(Duration.ofMillis(200));
        props.setPollTimeout(Duration.ofSeconds(10));
        XaiVideoAdapter adapter = new XaiVideoAdapter(client, new ObjectMapper(), props);

        var result = adapter.generate(new VideoGenCommand(
                "openai-compatible",
                "https://api.x.ai/v1",
                "grok-imagine-video-1.5",
                "sk",
                "a cat runs",
                5,
                "16:9",
                "720p",
                null));

        assertThat(result.requestId()).isEqualTo("rid-1");
        assertThat(result.url()).isEqualTo("https://cdn.x.ai/out.mp4");
        assertThat(result.durationSeconds()).isEqualTo(5);
        assertThat(polls.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void generate_failedStatusMapsError() {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request req = chain.request();
                    if ("POST".equals(req.method())) {
                        return json(req, 200, "{\"request_id\":\"rid-2\"}");
                    }
                    return json(
                            req,
                            200,
                            "{\"status\":\"failed\",\"error\":{\"code\":\"invalid_argument\",\"message\":\"bad prompt\"}}");
                })
                .build();
        VideoGenProperties props = new VideoGenProperties();
        props.setPollInterval(Duration.ofMillis(200));
        XaiVideoAdapter adapter = new XaiVideoAdapter(client, new ObjectMapper(), props);

        assertThatThrownBy(() -> adapter.generate(new VideoGenCommand(
                        "openai-compatible",
                        "https://api.x.ai/v1",
                        "grok-imagine-video-1.5",
                        "sk",
                        "x",
                        5,
                        null,
                        null,
                        null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("bad prompt");
    }

    private static Response json(Request req, int code, String body) {
        return new Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("OK")
                .body(ResponseBody.create(body, MediaType.get("application/json")))
                .build();
    }
}
