package com.example.rednote.auth.model.feed.service.impl;

import com.example.rednote.auth.common.tool.KeysUtil;
import com.example.rednote.auth.model.feed.dto.FeedRespond;
import com.example.rednote.auth.model.feed.service.FeedService;
import com.example.rednote.auth.model.notes.dto.NoteRespondDto;
import com.example.rednote.auth.model.notes.entity.Note;
import com.example.rednote.auth.model.notes.service.NoteService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service 
@Slf4j
@RequiredArgsConstructor
public class FeedServiceImpl implements FeedService {

    private final StringRedisTemplate redis;
    private final NoteService  noteService;
    private DefaultRedisScript<List> script;
    private DefaultRedisScript<Long> purgeInboxScript;

    @Value("${app.feed.bigv-threshold}") private long bigvThreshold;
    @Value("${app.feed.bigV-pull-size}") private double bigVPullSize;
    @Value("${app.shard}")private int shard;
    @PostConstruct
    public void preloadLua() {
        DefaultRedisScript<List> script=new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/feedpullscripts.lua")));
        script.setResultType(List.class);
        this.script=script;

        DefaultRedisScript<Long> purgeInboxScript=new DefaultRedisScript<>();
        purgeInboxScript.setScriptText(
        "local args={KEYS[1]}; " +
        "for i=1,#ARGV do table.insert(args,ARGV[i]); end " +
        "return redis.call('ZREM', unpack(args))"
        );
        purgeInboxScript.setResultType(Long.class);
        this.purgeInboxScript=purgeInboxScript;
    }


record FeedItem(long noteId, long score) {}

private static List<FeedItem> parseInboxFlat(List<String> flat) {
    List<FeedItem> items = new ArrayList<>(flat.size() / 2);
    for (int i = 0; i + 1 < flat.size(); i += 2) {
        String m = flat.get(i);
        String s = flat.get(i + 1);
        try {
            long nid = Long.parseLong(m);
            long sc  = Long.parseLong(s);
            items.add(new FeedItem(nid, sc));
        } catch (NumberFormatException ignore) { /* 跳过脏数据 */ }
    }
    return items;
}

private static List<FeedItem> parseBigVFlat(List<String> bigvFlat) {
    List<FeedItem> items = new ArrayList<>();
    int i = 0;
    while (i < bigvFlat.size()) {
        if (i + 1 >= bigvFlat.size()) break;
        String outboxKey = bigvFlat.get(i++); // 可用于打点
        String cntStr    = bigvFlat.get(i++);
        int count;
        try { count = Integer.parseInt(cntStr); } catch (Exception e) { break; }
        for (int j = 0; j < count && i + 1 < bigvFlat.size(); j++) {
            try {
                long nid = Long.parseLong(bigvFlat.get(i++));
                long sc  = Long.parseLong(bigvFlat.get(i++));
                items.add(new FeedItem(nid, sc));
            } catch (NumberFormatException ignore) { /* 跳过脏数据 */ }
        }
    }
    return items;
}

private static List<String> argv(String limit, String cursorScore, String cursorMember, String batch, String perOutboxLimit) {
    return Arrays.asList(limit, cursorScore, cursorMember, batch, perOutboxLimit);
}
// ====== 关键方法：考虑缺失/无权限的 getFeed ======
@Transactional
public FeedRespond<NoteRespondDto> getFeed(long userId, long cursorExclusive, int size, long noteId) {

    // 1) 准备游标与参数（首页传空串）
    String cursorScoreStr  = (cursorExclusive <= 0) ? "" : String.valueOf(cursorExclusive);
    String cursorMemberStr = (noteId == 0) ? "" : String.valueOf(noteId);

    // 2) 计算每个大V拉取条数
    Set<String> bigVKeys = redis.opsForSet().members(KeysUtil.redisFollowedBigVKey(userId));
    int bigvCnt = (bigVKeys == null) ? 0 : bigVKeys.size();
    int perOutboxLimit = (bigvCnt == 0) ? 0 : Math.max(2, (int) Math.ceil((double) size / Math.max(1, bigvCnt)));
    int limitWithRedundancy = size + (int) Math.floor(0.25 * size); // 冗余 25%
    int batch = 256;

    // 3) 组 KEYS
    List<String> keys = new ArrayList<>();
    String inboxKey = KeysUtil.redisInboxKey(userId, userId % shard);
    keys.add(inboxKey); // KEYS[1]
    if (bigvCnt > 0) {
        for (String aid : bigVKeys) {
            keys.add(KeysUtil.redisOutboxKey(Long.parseLong(aid) % shard, Long.parseLong(aid))); // KEYS[2..N]
        }
    }

    // 4) 补拉循环：最多 3 轮，直到凑满 size 或源头确无更多
    List<FeedItem> buffer = new ArrayList<>(limitWithRedundancy * 2);
    boolean sourceHasMore = false; // 记录源侧是否还有更多
    String nextScore = cursorScoreStr, nextMember = cursorMemberStr;
    int rounds = 0;

    while (buffer.size() < size && rounds < 3) {
        List<String> argv = argv(
            String.valueOf(limitWithRedundancy),
            nextScore,
            nextMember,
            String.valueOf(batch),
            String.valueOf(perOutboxLimit)
        );

        // 执行 Lua（确保 DefaultRedisScript<List>）
        Object resp = redis.execute(script, keys, argv.toArray(new String[0]));
        if (!(resp instanceof List<?> top)) {
            log.error("fetch script returned non-list: {}", (resp == null ? "null" : resp.getClass()));
            break; // 异常保护
        }

        @SuppressWarnings("unchecked")
        List<String> inboxFlat = (List<String>) top.get(0);
        String ns = (String) top.get(1); // 下一页 score
        String nm = (String) top.get(2); // 下一页 member
        @SuppressWarnings("unchecked")
        List<String> bigvFlat = (List<String>) top.get(3);

        // 解析、合并、去重（保序）
        List<FeedItem> roundItems = new ArrayList<>();
        roundItems.addAll(parseInboxFlat(inboxFlat));
        roundItems.addAll(parseBigVFlat(bigvFlat));

        // 去重：按 (noteId) 去重，保留先到的项
        LinkedHashMap<Long, FeedItem> uniq = new LinkedHashMap<>();
        for (FeedItem it : roundItems) uniq.putIfAbsent(it.noteId(), it);

        buffer.addAll(uniq.values());

        // 源侧是否还有下一页
        sourceHasMore = (ns != null && !ns.isEmpty());
        nextScore = ns;
        nextMember = nm;
        if (!sourceHasMore) break;

        rounds++;
    }

    // 5) 统一按 (score desc, noteId desc) 排序，保持与 Lua 的倒序一致
    buffer.sort(Comparator
        .comparingLong(FeedItem::score)
        .thenComparingLong(FeedItem::noteId).reversed()
    );

    // 6) 截取前 size 的候选 id（其余是冗余）
    List<Long> candidateIds = buffer.stream()
        .map(FeedItem::noteId)
        .distinct()
        .limit(size * 3L) // 多给一点给权限/缺失过滤后填充
        .toList();

    // 7) 查库 + 权限过滤（请实现该方法；或用 findAllByIds 并过滤 null）
    Map<Long, Note> allowedMap = noteService.findReadableMap(userId, candidateIds);

    // 8) 构造本页 noteId 列表（按 buffer 顺序，最多 size 条）
    List<Long> pageNoteIds = new ArrayList<>(size);
    for (FeedItem it : buffer) {
        if (pageNoteIds.size() >= size) break;
        if (allowedMap.containsKey(it.noteId())) {
            pageNoteIds.add(it.noteId());
        }
    }

    // 9) 懒清理：把缺失/无权限的 nid 从 inbox 移除，减少下次空洞
    List<Long> missingOrForbidden = candidateIds.stream()
        .filter(id -> !allowedMap.containsKey(id))
        .toList();
    if (!missingOrForbidden.isEmpty()) {
        try {
            redis.execute(
                purgeInboxScript,                       // DefaultRedisScript<Long>
                List.of(inboxKey),
                missingOrForbidden.stream().map(String::valueOf).toArray(String[]::new)
            );
        } catch (Exception e) {
            log.warn("lazy purge failed, ids={}", missingOrForbidden.size(), e);
        }
    }

    // 10) 组装 DTO（按 pageNoteIds 顺序输出）
    List<NoteRespondDto> page = new ArrayList<>(pageNoteIds.size());
    for (Long id : pageNoteIds) {
        Note n = allowedMap.get(id);
        if (n != null) page.add(n.toNoteRespondDto());
    }

    // 11) 计算 hasMore / nextCursor / nextNoteId：直接用 Lua 返回的游标
    boolean hasMore = sourceHasMore;
    Long nextCursor = hasMore ? Long.valueOf(nextScore) : null;
    Long nextNoteId = (hasMore && nextMember != null && !nextMember.isEmpty()) ? Long.valueOf(nextMember) : null;
    FeedRespond<NoteRespondDto> res=new FeedRespond<>(page, hasMore, nextCursor, nextNoteId, page.size());

    return res;
}
}