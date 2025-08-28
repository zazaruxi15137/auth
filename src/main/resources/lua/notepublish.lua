-- KEYS[1] = zset key（例如 outbox）
-- ARGV[1] = member（字符串，如 noteId）
-- ARGV[2] = score（数字，时间戳：秒或毫秒）
-- ARGV[3] = maxSize（数字，最大保留条数）
-- ARGV[4] = expireDays（数字，按天清理；<=0 表示不按天清理）
-- ARGV[5] = timeUnit（"ms" 或 "s"，默认 "ms"）

local outbox   = KEYS[1]
local member    = ARGV[1]
local score     = tonumber(ARGV[2])
local maxSize   = tonumber(ARGV[3])
local expireDays= tonumber(ARGV[4]) or 0
local timeUnit  = ARGV[5] or "ms"

-- 1) 写入
redis.call('ZADD', outbox, score, member)

-- 3) 按 redis服务器当前时间裁剪（清理超过 expireDays 的旧数据）
if expireDays > 0 then
  local t = redis.call('TIME')               -- {sec, usec}
  local now_ms = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
  local oneDay = (timeUnit == "s") and 86400 or 86400000
  local now    = (timeUnit == "s") and math.floor(now_ms / 1000) or now_ms
  local cutoff = now - expireDays * oneDay
  -- 删掉 score < cutoff 的元素（严格小于）
  redis.call('ZREMRANGEBYSCORE', outbox, '-inf', '(' .. cutoff)
  -- redis.call('EXPIRE', inbox, expireDays * oneDay)
end
-- 2) 按 rank 裁剪（只保留最新 maxSize 条）
if maxSize and maxSize > 0 then
  local size = redis.call('ZCARD', outbox)
  if size > maxSize then
    local removeCount = size - maxSize
    -- 从最小的开始删掉多余的
    redis.call('ZREMRANGEBYRANK', outbox, 0, removeCount - 1)
  end
end

return 1
