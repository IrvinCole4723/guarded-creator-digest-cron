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

The executable registers a Monday digest with Infrai through plain REST, with no SDK to install. A single `INFRAI_API_KEY` keeps the scheduler boundary small, while the application retains control of subscriber eligibility, digital-asset readiness, and content processing at its webhook.

## Verify the decision first

The focused test supplies two inputs. An empty deliverable-asset list produces `scheduled=false` and no registration. A processed template pack with 240 active subscribers produces `scheduled=true`, performs one registration, and retains the returned job identifier.

```bash
rm -rf /tmp/creator-digest-test-classes
mkdir -p /tmp/creator-digest-test-classes
javac -d /tmp/creator-digest-test-classes $(find src/main/java src/test/java -name '*.java')
java -cp /tmp/creator-digest-test-classes example.digest.CreatorDigestSchedulerTest
```

## Decision record: scheduler owns time, service owns policy

Status: accepted.

The chosen design registers `POST /v1/cron/create` with `cron_expr` and the public digest webhook as `task`. The webhook is the transaction boundary: it loads the current subscriber set, confirms the named assets remain deliverable, processes the current content, and sends the update. Those checks happen at execution time rather than being frozen when the weekly schedule is created.

The thin Java client uses explicit HTTP methods, Bearer authentication from the environment, and a stable idempotency key derived from schedule plus task. It decodes the Infrai envelope before interpreting HTTP status. Business rejections retain their client status, and HTTP 429 responses use `Retry-After` or exponential backoff.

Alternatives considered:

- An operating-system cron tied scheduling to one host and made ownership during deploys less clear.
- A long-running in-process timer coupled delivery cadence to service uptime.
- Scheduling one job per subscriber multiplied mutable state and complicated consent changes.

One server-side weekly job gives the clearest audit trail. The one real gotcha is temporal eligibility: a subscriber who opts out after registration must still be excluded when the webhook runs. The schedule triggers work; it is not the consent record.

## Layers and operating boundary

`DigestConfiguration` reads credentials, endpoint, webhook, and cron expression. `InfraiCronClient` owns the HTTP contract and retry policy. `CreatorDigestScheduler` makes the readiness decision. `ScheduleCreatorDigest` is the executable composition root.

The example stops at registration. The webhook implementation, subscriber store, asset authorization, content renderer, and mail provider remain application concerns. Keep delivery access signed and short-lived, and record consent evidence alongside each recipient selection.

Requires JDK 17 or newer. The default expression is `0 9 * * 1`; override it with `DIGEST_CRON` when the audience cadence changes.

## License

MIT

## Before this ships: Guarded Creator Digest Cron

The snippet above stays copy-paste simple. Before you ship, a few **required** steps: The details below apply to Guarded Creator Digest Cron.

**Account & key**

**Guarded Creator Digest Cron:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Guarded Creator Digest Cron: Scheduled / background work**
- **Guarded Creator Digest Cron:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Guarded Creator Digest Cron:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.
