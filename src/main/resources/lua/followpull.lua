-- KEYS[1] = authorOutbox (ZSET)
-- KEYS[2] = followerInbox (ZSET)
-- KEYS[3] = followerAuthorIndex (SET) 例如 inbox:{followerId}:byAuthor:{authorId}

-- ARGV[1] = fetchCount        (number)  例如 20
-- ARGV[2] = maxSize           (number)  <=0 表示不裁剪
-- ARGV[3] = expireDays        (number)  <=0 表示不清理时间窗
-- ARGV[4] = timeUnit          ("ms"|"s") 默认 "ms"

local outboxKey   = KEYS[1]
local inboxKey    = KEYS[2]

local fetchCount  = tonumber(ARGV[1]) or 20
local maxSize     = tonumber(ARGV[2]) or 0
local expireDays  = tonumber(ARGV[3]) or 0
local timeUnit    = ARGV[4] or "ms"

-- 1) 取作者 outbox 最新 N 条 (member, score 成对返回)
local res = redis.call('ZREVRANGE', outboxKey, 0, fetchCount - 1, 'WITHSCORES')

-- 2) 批量 ZADD 到 inbox
if #res > 0 then
  local args = {inboxKey}
  local members = {}  -- 用于写入索引集合

  for i = 1, #res, 2 do
    local member = res[i]
    local score  = tonumber(res[i+1])
    table.insert(args, score)
    table.insert(args, member)
    -- table.insert(members, member)
  end

  redis.call('ZADD', unpack(args))
--   -- 3) 记录作者索引集合（用于取关快速删除）
--   if #members > 0 then
--     redis.call('SADD', indexSetKey, unpack(members))
--   end
end

-- 5) 按时间窗清理（以当前时间为基准）
if expireDays > 0 then
  local t = redis.call('TIME')  -- {sec, usec}
  local now_ms = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
  local cutoff
  if timeUnit == "s" then
    cutoff = math.floor(now_ms / 1000) - expireDays * 86400
  else
    cutoff = now_ms - expireDays * 86400000
  end
  redis.call('ZREMRANGEBYSCORE', inboxKey, '-inf', '(' .. cutoff)
end

-- 4) 按 rank 裁剪 inbox（只保留最新 maxSize 条）
if maxSize > 0 then
  local size = redis.call('ZCARD', inboxKey)
  if size > maxSize then
    local removeCount = size - maxSize
    redis.call('ZREMRANGEBYRANK', inboxKey, 0, removeCount - 1)
  end
end



return #res / 2  -- 返回实际拉取的条数
