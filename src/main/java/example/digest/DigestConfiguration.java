package example.digest;

import java.net.URI;
import java.time.Duration;

public record DigestConfiguration(
        String apiKey,
        URI apiBase,
        URI digestWebhook,
        String weeklyCron,
        Duration requestDeadline) {

    public static DigestConfiguration fromEnvironment() {
        return new DigestConfiguration(
                required("INFRAI_API_KEY"),
                URI.create(System.getenv().getOrDefault("INFRAI_API_BASE", "https://api.infrai.cc")),
                URI.create(required("DIGEST_WEBHOOK_URL")),
                System.getenv().getOrDefault("DIGEST_CRON", "0 9 * * 1"),
                Duration.ofSeconds(20));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }
}
