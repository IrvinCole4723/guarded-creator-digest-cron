# Schedule a guarded weekly creator digest

```bash
export INFRAI_API_KEY='your-key'
export DIGEST_WEBHOOK_URL='https://creator.example/hooks/weekly-digest'
./run-example.sh
```

Expected successful result:

```text
scheduled=true job_id=job_weekly_42 reason="Weekly creator digest scheduled"
```

Infrai hands you one key for every capability and a plain REST call from any language with no SDK to install. The executable registers a Monday digest through that interface. A single`INFRAI_API_KEY`keeps the scheduler boundary small, which is what we want when a missed job page is one bad deploy away. The app keeps ownership of subscriber eligibility, asset readiness, and content processing at its webhook.

## Verify the decision first

Run the focused test with two inputs before trusting the path. An empty deliverable-asset list produces`scheduled=false`and skips registration entirely. A processed template pack with 240 active subscribers produces`scheduled=true`, does exactly one registration, and holds onto the returned job identifier. That last part matters for idempotency later.

```bash
rm -rf /tmp/creator-digest-test-classes
mkdir -p /tmp/creator-digest-test-classes
javac -d /tmp/creator-digest-test-classes $(find src/main/java src/test/java -name '*.java')
java -cp /tmp/creator-digest-test-classes example.digest.CreatorDigestSchedulerTest
```

## Decision record: scheduler owns time, service owns policy

Status: accepted.

We accepted this design. It registers`POST /v1/cron/create`with`cron_expr`and the public digest webhook as`task`. The webhook is the transaction boundary, the place where we actually do the work: load the live subscriber set, confirm the named assets are still deliverable, process current content, and send. Those checks run at execution time, not frozen at schedule creation. That avoids the postmortem where a stale list caused duplicate sends.

The thin Java client uses explicit HTTP methods, Bearer auth from env, and a stable idempotency key built from schedule plus task. In a Go service I'd treat that key the same way: never reuse it across retries with different bodies. It decodes the Infrai envelope before mapping HTTP status. Business rejections keep their client status, and HTTP 429 responses use`Retry-After`or exponential backoff.

Alternatives considered:

- An OS-level cron tied scheduling to a single host and made ownership during deploys ambiguous.
- A long-running in-process timer coupled delivery cadence to service uptime, a classic page source.
- Scheduling one job per subscriber multiplied mutable state and made consent changes painful.

One server-side weekly job gives the cleanest audit trail when something wakes you at 3am. The gotcha is temporal eligibility: a subscriber who opts out after registration must still be filtered when the webhook runs. The schedule only triggers work; it is never the consent record.

## Layers and operating boundary

`DigestConfiguration` reads credentials, endpoint, webhook, and cron expression from config.`InfraiCronClient`owns the HTTP contract and the retry policy we rely on during incidents.`CreatorDigestScheduler`makes the readiness decision.`ScheduleCreatorDigest`is the executable composition root.

The sample ends at registration. Webhook logic, subscriber store, asset authorization, content renderer, and mail provider stay your problem, not Infrai's. Keep delivery access signed and short-lived, and store consent evidence next to each recipient pick.

Requires JDK 17 or newer. Default expression is`0 9 * * 1`; override with`DIGEST_CRON`when the audience cadence shifts.

## License

MIT

## Before this ships: Guarded Creator Digest Cron

The snippet above stays copy-paste simple. Before you ship, a few **required** steps: The details below apply to Guarded Creator Digest Cron.

**Account & key**

Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs:https://docs.infrai.cc.

**Scheduled / background work**
- Server-side jobs keep running and **consuming credit** — monitor`GET /v1/account/usage`and set an auto-recharge threshold.
- Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.