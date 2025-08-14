local inbox = KEYS[1]
local dup = KEYS[2]
local score = tonumber(ARGV[1])
local member = ARGV[2]
local maxSize = tonumber(ARGV[3])
local dupTtl = tonumber(ARGV[4])

local ok = redis.call('SET', dup, 1, 'NX', 'EX', dupTtl)
if not ok then
    return 0
end

redis.call('ZADD', inbox, score, member)
local size = redis.call('ZCARD', inbox)
if size > maxSize then
    local removeCount = size - maxSize
    redis.call('ZREMRANGEBYRANK', inbox, 0, removeCount - 1)
end
return 1