-- KEYS:
-- 1: inbox zset key
-- 2: dup key
-- 3: index zset key                (ZSET，作者索引：inbox:{uid}:byAuthor:{aid})
local inbox     = KEYS[1]
local dup       = KEYS[2]
local indexKey  = KEYS[3]

-- ARGV:
-- 1: score (number; 与 timeUnit 一致)
-- 2: member (string)
-- 3: maxSize (number)
-- 4: dupTtl (number; 与 dupTtlUnit 一致)
-- 5: expireDays (number; 可为 0)
-- 6: timeUnit ("s" | "ms")  -- score 与一天的单位
-- 7: dupTtlUnit ("s" | "ms") -- 可选，默认 "s"

local score       = tonumber(ARGV[1])
local member      = ARGV[2]
local maxSize     = tonumber(ARGV[3])
local dupTtl      = tonumber(ARGV[4])
local expireDays  = tonumber(ARGV[5]) or 0
local timeUnit    = ARGV[6] or "ms"
local dupTtlUnit  = ARGV[7] or "s"

-- 参数基本校验
if not inbox or inbox == "" then return redis.error_reply("inbox key required") end
if not dup or dup == "" then return redis.error_reply("dup key required") end
if not indexKey or indexKey == ""     then return redis.error_reply("index key required") end
if not score then return redis.error_reply("score must be a number") end
if not member or member == "" then return redis.error_reply("member required") end
if not maxSize or maxSize <= 0 then return redis.error_reply("maxSize must be > 0") end
if not dupTtl or dupTtl <= 0 then return redis.error_reply("dupTtl must be > 0") end

-- 去重：根据单位选择 EX/PX
local ok
if dupTtlUnit == "ms" then
  ok = redis.call('SET', dup, 1, 'NX', 'PX', dupTtl)
else
  ok = redis.call('SET', dup, 1, 'NX', 'EX', dupTtl)
end
if not ok then
  return 0
end

-- 先按 score 裁剪过期数据（保证 score 单位与 timeUnit 一致）
if expireDays > 0 then
  local t = redis.call('TIME')               -- {sec, usec}
  local now_ms = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
  local oneDay = (timeUnit == "s") and 86400 or 86400000
  local now    = (timeUnit == "s") and math.floor(now_ms / 1000) or now_ms
  local cutoff = now - expireDays * oneDay
  -- 严格小于 cutoff 的都删掉
  redis.call('ZREMRANGEBYSCORE', inbox, '-inf', '(' .. tostring(cutoff))
  redis.call('ZREMRANGEBYSCORE', indexKey, '-inf', '(' .. tostring(cutoff))

  -- 为整个 zset 设置 TTL（单位与 Redis 命令对应）
  -- 注意：过期天数对集合整体生效，新的写入会刷新 TTL（你现在的写法每次都会刷新）
  local ttl = expireDays * ((timeUnit == "s") and 86400 or 86400000)
  if timeUnit == "ms" then
    redis.call('PEXPIRE', inbox, ttl)
    redis.call('PEXPIRE', indexKey, ttl)
  else
    redis.call('EXPIRE', inbox, ttl)
    redis.call('EXPIRE', indexKey, ttl)
  end
end

-- 写入并按容量裁剪
redis.call('ZADD', inbox, score, member)
redis.call('ZADD', indexKey, score, member)
local size = redis.call('ZCARD', inbox)
if size > maxSize then
  local removeCount = size - maxSize
  -- 从最旧的开始删（分数最小的）
  redis.call('ZREMRANGEBYRANK', inbox, 0, removeCount - 1)
end

return 1