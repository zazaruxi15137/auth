-- KEYS:
-- 1: inbox ZSET key
-- 2..N: 可选，一组大V outbox 的 ZSET key（可为 0 个）

-- ARGV:
-- 1: limit                 (number) inbox 本次最多返回的条数
-- 2: cursorScore           (string，"" 表示从最新开始)
-- 3: cursorMember          (string，"" 表示从最新开始；与 cursorScore 配合使用)
-- 4: batch                 (number，内部扫描批大小，默认 256，建议 200~500)
-- 5: bigvPerKeyLimit       (number，每个 outbox 的拉取上限；<=0 表示不拉取大V)

local inboxKey      = KEYS[1]
local limit         = tonumber(ARGV[1]) or 20
local cScoreStr     = ARGV[2] or ""
local cMember       = ARGV[3] or ""
local batch         = tonumber(ARGV[4]) or 256
local perOutbox     = tonumber(ARGV[5]) or 0

-- 工具：从单个 ZSET 用 (score, member) 双游标拉取最多 limit 条（倒序：score 降序，score 相同按 member 降序）
local function fetch_from_zset(zkey, limitN, cScoreStr, cMember, batchN)
  local out = {}
  local taken = 0
  local lastScore = nil
  local lastMember = nil

  -- 阶段A：如果有 cursorScore，先处理“等分数”的桶（只取 member < cursorMember）
  if cScoreStr ~= "" then
    local eq = redis.call('ZRANGEBYSCORE', zkey, cScoreStr, cScoreStr) -- 同分数，按 member 升序
    -- 我们需要倒序，所以从后往前扫，只取小于 cursorMember 的（实现“严格排除”边界）
    for i = #eq, 1, -1 do
      local m = eq[i]
      if cMember == "" or m < cMember then
        table.insert(out, m)
        table.insert(out, cScoreStr)
        taken = taken + 1
        lastScore = cScoreStr
        lastMember = m
        if taken >= limitN then
          return out, lastScore or "", lastMember or ""
        end
      end
    end
  end

  -- 阶段B：拉取更小分数的内容（严格小于 cursorScore）
  local maxv = (cScoreStr ~= "" and '(' .. cScoreStr) or '+inf'
  local minv = '-inf'
  while taken < limitN do
    local slice = redis.call('ZREVRANGEBYSCORE', zkey, maxv, minv, 'WITHSCORES', 'LIMIT', 0, batchN)
    if #slice == 0 then break end
    local i = 1
    while i <= #slice and taken < limitN do
      local m = slice[i]
      local s = slice[i+1]
      table.insert(out, m)
      table.insert(out, tostring(s))
      taken = taken + 1
      lastScore = tostring(s)
      lastMember = m
      i = i + 2
    end
    -- 继续往下走
    if #slice > 0 then
      local lastS = slice[#slice]  -- 最后一个分数
      maxv = '(' .. tostring(lastS)
    end
    if #slice < batchN then break end
  end

  return out, lastScore or "", lastMember or ""
end

-- 1) 拉取 inbox
local inboxFlat, nextScore, nextMember = fetch_from_zset(inboxKey, limit, cScoreStr, cMember, batch)

-- 2) 可选：拉取多个大V outbox（每个不返回游标，只做“从当前游标向下”的一页）
local bigvFlat = {}
if perOutbox > 0 and #KEYS >= 2 then
  for k = 2, #KEYS do
    local zkey = KEYS[k]
    local flat, _, _ = fetch_from_zset(zkey, perOutbox, cScoreStr, cMember, batch)
    -- 编码方式：[outboxKey, count, nid1, score1, nid2, score2, ...]
    table.insert(bigvFlat, zkey)
    table.insert(bigvFlat, tostring(#flat / 2))
    for i = 1, #flat do
      table.insert(bigvFlat, flat[i])
    end
  end
end

-- 返回：
-- 1) inbox 扁平列表  [nid1, score1, nid2, score2, ...]
-- 2) nextScore（字符串；为空表示无更多）
-- 3) nextMember（字符串；为空表示无更多）
-- 4) bigV 扁平结果   ["outboxKeyA","countA", nid,score, ..., "outboxKeyB","countB", ...]
return { inboxFlat, nextScore, nextMember, bigvFlat }
