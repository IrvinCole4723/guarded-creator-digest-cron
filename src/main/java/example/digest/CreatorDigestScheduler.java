package example.digest;

import java.io.IOException;
import java.net.URI;
import java.util.List;

public final class CreatorDigestScheduler {
    private final CronRegistration cronRegistration;
    private final URI digestWebhook;
    private final String weeklyCron;

    public CreatorDigestScheduler(CronRegistration cronRegistration, URI digestWebhook, String weeklyCron) {
        this.cronRegistration = cronRegistration;
        this.digestWebhook = digestWebhook;
        this.weeklyCron = weeklyCron;
    }

    public ScheduleDecision schedule(DigestReadiness readiness) throws IOException, InterruptedException {
        if (readiness.deliverableAssets().isEmpty()) {
            return new ScheduleDecision(false, null, "No deliverable assets are ready");
        }
        if (readiness.activeSubscriberCount() < 1) {
            return new ScheduleDecision(false, null, "No active subscribers are eligible");
        }
        if (!readiness.contentProcessingComplete()) {
            return new ScheduleDecision(false, null, "Content processing is incomplete");
        }
        String jobId = cronRegistration.create(weeklyCron, digestWebhook);
        return new ScheduleDecision(true, jobId, "Weekly creator digest scheduled");
    }

    public record DigestReadiness(List<String> deliverableAssets, int activeSubscriberCount,
                                  boolean contentProcessingComplete) {
        public DigestReadiness {
            deliverableAssets = List.copyOf(deliverableAssets);
            if (activeSubscriberCount < 0) throw new IllegalArgumentException("subscriber count must be non-negative");
        }
    }

    public record ScheduleDecision(boolean scheduled, String jobId, String reason) {}

    @FunctionalInterface
    public interface CronRegistration {
        String create(String cronExpression, URI task) throws IOException, InterruptedException;
    }
}
