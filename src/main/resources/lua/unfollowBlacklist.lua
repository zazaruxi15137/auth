-- KEYS[1] = followerInbox (ZSET)               eg. inbox:{uid}
-- KEYS[2] = followerAuthorIndex (SET or ZSET)  eg. inbox:{uid}:byAuthor:{aid}
--
-- ARGV[1] = batchSize   (可选，默认 500)
-- ARGV[2] = dropIndex   ("1" 删除索引key；"0" 不删；默认 "1")
--
-- 返回：实际从 inbox 删除的条数

local inboxKey  = KEYS[1]
local indexKey  = KEYS[2]

local batch     = tonumber(ARGV[1]) or 500
local dropIndex = (ARGV[2] or "1") == "1"

-- 检测索引类型
local t = redis.call('TYPE', indexKey)
local idxType = (t and (t.ok or t['type'])) or 'none'
if idxType == 'none' then
  return 0
end

local cursor = "0"
local totalRemoved = 0

repeat
  local scan = redis.call('ZSCAN', indexKey, cursor, 'COUNT', batch)

  cursor = scan[1]
  local arr = scan[2]

  if #arr > 0 then
    -- 提取成员列表
    local members = {}
    for i = 1, #arr, 2 do table.insert(members, arr[i]) end -- ZSCAN 返回 [member, score, ...]

    -- 从 inbox 批量删除
    if #members > 0 then
      -- 从 inbox 批量删除
      local zremArgs = { inboxKey }
      for i = 1, #members do zremArgs[#zremArgs+1] = members[i] end
      local removed = redis.call('ZREM', unpack(zremArgs))
      totalRemoved = totalRemoved + (removed or 0)
    -- 从索引集合移除这些成员，避免重复处理
      if not dropIndex then
      local remIdxArgs = { indexKey }
      for i = 1, #members do remIdxArgs[#remIdxArgs+1] = members[i] end
      redis.call('ZREM', unpack(remIdxArgs))
    end
  end
end
until cursor == "0"

-- 索引清理完，按需删除 key（幂等）
if dropIndex then
  redis.call('DEL', indexKey)
end

return totalRemoved
