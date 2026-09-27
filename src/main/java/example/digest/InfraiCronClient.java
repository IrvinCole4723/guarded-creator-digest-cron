package example.digest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiCronClient {
    private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
    private static final Pattern JOB_ID = Pattern.compile("\\\"job_id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern ERROR = Pattern.compile("\\\"error\\\"\\s*:\\s*(\\{.*?\\}|null)", Pattern.DOTALL);
    private final HttpClient http;
    private final DigestConfiguration configuration;
    private final Sleeper sleeper;

    public InfraiCronClient(DigestConfiguration configuration) {
        this(configuration, HttpClient.newHttpClient(), Thread::sleep);
    }

    InfraiCronClient(DigestConfiguration configuration, HttpClient http, Sleeper sleeper) {
        this.configuration = configuration;
        this.http = http;
        this.sleeper = sleeper;
    }

    /** Canonical call: infrai.cron.create */
    public String create(String cronExpression, URI task) throws IOException, InterruptedException {
        String body = "{\"cron_expr\":" + quote(cronExpression) + ",\"task\":" + quote(task.toString()) + "}";
        String idempotencyKey = digest(cronExpression + "\n" + task);

        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(configuration.apiBase().resolve("/v1/cron/create"))
                    .timeout(configuration.requestDeadline())
                    .header("Authorization", "Bearer " + configuration.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .method("POST", HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            Envelope envelope = decode(response.body());

            if (response.statusCode() == 429 && attempt < 3) {
                sleeper.sleep(retryDelayMillis(response, attempt));
                continue;
            }
            if (!envelope.ok()) {
                throw new InfraiException(response.statusCode(), envelope.error());
            }
            if (response.statusCode() >= 500) {
                throw new IOException("Infrai transport response: HTTP " + response.statusCode());
            }
            if (envelope.jobId() == null || envelope.jobId().isBlank()) {
                throw new IOException("Infrai response omitted job identifier");
            }
            return envelope.jobId();
        }
        throw new IOException("Infrai rate limit retry budget exhausted");
    }

    private static Envelope decode(String json) throws IOException {
        Matcher ok = OK.matcher(json);
        if (!ok.find()) {
            throw new IOException("Response is not an Infrai envelope");
        }
        Matcher job = JOB_ID.matcher(json);
        Matcher error = ERROR.matcher(json);
        return new Envelope(Boolean.parseBoolean(ok.group(1)), job.find() ? job.group(1) : null,
                error.find() ? error.group(1) : "null");
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .flatMap(value -> {
                    try { return java.util.Optional.of(Duration.ofSeconds(Long.parseLong(value)).toMillis()); }
                    catch (NumberFormatException ignored) { return java.util.Optional.empty(); }
                })
                .orElse(250L * (1L << attempt));
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record Envelope(boolean ok, String jobId, String error) {}

    @FunctionalInterface
    interface Sleeper { void sleep(long millis) throws InterruptedException; }

    public static final class InfraiException extends IOException {
        private final int status;
        InfraiException(int status, String detail) {
            super("Infrai rejected the request (HTTP " + status + "): " + detail);
            this.status = status;
        }
        public int status() { return status; }
    }
}
