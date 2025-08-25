package com.example.rednote.auth.common.tool;

public final class MetricsNames {
  public static final String LUA_EXEC_TIMER = "lua.exec";                 // 计时
  public static final String LUA_EXEC_SUCCESS = "lua.exec.success";       // 成功计数
  public static final String LUA_EXEC_FAIL = "lua.exec.fail";             // 失败计数

  public static final String PIPLINE_EXEC_TIMER = "pipline.exec";                 // 计时
  public static final String PIPLINE_EXEC_SUCCESS = "pipline.exec.success";       // 成功计数
  public static final String PIPLINE_EXEC_FAIL = "pipline.exec.fail";             // 失败计数

  public static final String FEED_TIMELINE_TIMER = "feed.timeline";       // 拉流
  public static final String SOCIAL_FOLLOW_TIMER = "social.follow";       // 关注
  public static final String SOCIAL_UNFOLLOW_TIMER = "social.unfollow";   // 取关
  public static final String NOTE_PUBLISH_TIMER = "note.publish";         // 发笔记
  public static final String NOTE_UPDATE_TIMER = "note.update";
  public static final String NOTE_PAGE_QUERY = "note.page.query";
  public static final String NOTE_SINGLE_QUERY = "note.single.query";
  public static final String IDEMPOTENCY_CACHE_SKIP_TOTAL = "idempotency.cache.skip.total";
  public static final String APP_RETRY_COUNTER = "app.retries.total";     // 重试次数
  public static final String AUTH_DENY_COUNTER = "app.authz.denied";      // 鉴权/越权拒绝
  public static final String AUTH_FAIL_TOTAL = "app.authz.fail";
  public static final String IDEMPOTENT_HIT = "app.idempotent.hit";       // 幂等命中

  public static final String IDEMPOTENCY_REQUESTS_TOTAL="idempotency.requests.total";
  public static final String IDEMPOTENCY_FIRST_LATENCY_TIMER="idempotency.first.latency";
  public static final String IDEMPOTENCY_RESULT_TOTAL="idempotency.result.total";

  public static final String INBOX_BACKLOG_GAUGE = "feed.inbox.backlog.age.ms"; // 最老消息“年龄”
  public static final  String STREAM_LENGTH = "stream_length";                 // Gauge: XLEN
  public static final  String STREAM_PENDING = "stream_pending";               // Gauge: XPENDING 总数
  public static final  String INBOX_STAY_MS = "inbox_stay_ms";                 // DistributionSummary: 停留时长分布（ms）
  public static final  String INBOX_AVG_STAY_MS = "inbox_avg_stay_ms";         // Gauge: 停留时长简单平均（ms）
  public static final  String INBOX_SAMPLED_USERS = "inbox_sampled_users";     // Gauge
  public static final  String INBOX_SAMPLED_MESSAGES = "inbox_sampled_messages"; // Gauge
  public static final  String METRICS_PROBE_TIMER = "metrics_probe_timer";     // Timer: 任务耗时
  public static final  String METRICS_PROBE_ERRORS_TOTAL = "metrics_probe_errors_total"; // Counter: 异常计数

  public static final String LOCAL_BUCKET_AVAILABLE = "local_bucket_available";
  public static final String RATELIMIT_LOCAL_WAIT_MS = "ratelimit_local_wait_ms";
  public static final String RATELIMIT_LOCAL_TIMEOUT_TOTAL= "ratelimit_local_timeout_total";
  public static final String RATELIMIT_LOCAL_ALLOWED_TOTAL = "ratelimit_local_allowed_total";
  public static final String RATELIMIT_REDIS_BLICKED_TOTAL = "ratelimit_redis_blocked_total";
  public static final String RATELIMIT_REDIS_ALLOWED_TOTAL = "ratelimit_redis_allowed_total";

}
