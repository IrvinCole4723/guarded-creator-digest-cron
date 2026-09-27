package example.digest;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class CreatorDigestSchedulerTest {
    public static void main(String[] args) throws Exception {
        AtomicInteger registrations = new AtomicInteger();
        CreatorDigestScheduler scheduler = new CreatorDigestScheduler((expression, task) -> {
            registrations.incrementAndGet();
            require("0 9 * * 1".equals(expression), "weekly schedule changed");
            require(task.equals(URI.create("https://creator.example/hooks/weekly-digest")), "task changed");
            return "job_weekly_42";
        }, URI.create("https://creator.example/hooks/weekly-digest"), "0 9 * * 1");

        var held = scheduler.schedule(new CreatorDigestScheduler.DigestReadiness(List.of(), 240, true));
        require(!held.scheduled(), "empty asset set must be held");
        require(registrations.get() == 0, "held digest must not register a cron");

        var scheduled = scheduler.schedule(new CreatorDigestScheduler.DigestReadiness(
                List.of("creator-template-pack"), 240, true));
        require(scheduled.scheduled(), "ready digest must be scheduled");
        require("job_weekly_42".equals(scheduled.jobId()), "job identifier must be retained");
        require(registrations.get() == 1, "ready digest must register once");
        System.out.println("PASS: readiness gates registration and retains the job identifier");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
