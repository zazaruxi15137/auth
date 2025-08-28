-- KEYS[1] = bucketKey {hash}
-- KEYS[2] = confKey   {hash}
-- ARGV[1] = now_ms
-- ARGV[2] = cost (default 1)
-- ARGV[3] = def_cap
-- ARGV[4] = def_rate_per_sec
-- ARGV[5] = def_idle_ttl_sec

local bkey = KEYS[1]
local ckey = KEYS[2]

local now   = tonumber(ARGV[1])
local cost  = tonumber(ARGV[2]) or 1
local def_c = tonumber(ARGV[3]) or 100
local def_r = tonumber(ARGV[4]) or 50
local def_i = tonumber(ARGV[5]) or 600

-- read conf once
local cvals = redis.call('HMGET', ckey, 'cap', 'rate', 'idle_ttl')
local cap   = tonumber(cvals[1]) or def_c
local rate  = tonumber(cvals[2]) or def_r
local idle  = tonumber(cvals[3]) or def_i

-- guard
if rate <= 0 then rate = def_r end
if cap  <= 0 then
  -- 永远不允许
  return { 0, 0, -1, 0, rate }
end

-- clamp 请求成本到 cap（或者你也可以在 cost>cap 时直接返回 wait_ms=-1）
if cost > cap then cost = cap end

local bvals  = redis.call('HMGET', bkey, 'tokens', 'ts')
local tokens = tonumber(bvals[1])
local ts     = tonumber(bvals[2])

if not tokens or not ts then
  tokens = cap
  ts = now
end

local elapsed = now - ts
if elapsed < 0 then elapsed = 0 end

-- 补桶：使用整数令牌
local to_add = math.floor(elapsed * rate / 1000.0)
if to_add > 0 then
  tokens = math.min(cap, tokens + to_add)
end

local allowed = 0
local wait_ms = 0

if tokens >= cost then
  tokens = tokens - cost
  allowed = 1
else
  local missing = cost - tokens
  wait_ms = math.ceil(missing * 1000.0 / rate)
end

-- 写回（把 ts 统一写 now，更直观）
redis.call('HSET', bkey, 'tokens', tokens, 'ts', now)
if idle and idle > 0 then
  redis.call('EXPIRE', bkey, idle)
else
  redis.call('PERSIST', bkey)
end

return { allowed, tokens, wait_ms, cap, rate }