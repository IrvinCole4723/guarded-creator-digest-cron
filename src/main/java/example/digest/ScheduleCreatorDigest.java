package example.digest;

import java.util.List;

public final class ScheduleCreatorDigest {
    private ScheduleCreatorDigest() {}

    public static void main(String[] args) throws Exception {
        DigestConfiguration configuration = DigestConfiguration.fromEnvironment();
        InfraiCronClient client = new InfraiCronClient(configuration);
        CreatorDigestScheduler scheduler = new CreatorDigestScheduler(
                client::create, configuration.digestWebhook(), configuration.weeklyCron());

        CreatorDigestScheduler.DigestReadiness input = new CreatorDigestScheduler.DigestReadiness(
                List.of("creator-template-pack"), 240, true);
        CreatorDigestScheduler.ScheduleDecision decision = scheduler.schedule(input);
        System.out.println("scheduled=" + decision.scheduled()
                + " job_id=" + decision.jobId()
                + " reason=\"" + decision.reason() + "\"");
    }
}
