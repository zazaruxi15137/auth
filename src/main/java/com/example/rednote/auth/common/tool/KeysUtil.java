package com.example.rednote.auth.common.tool;

public class KeysUtil {
    public static String redisInboxKey(long userId) { return "feed:inbox:" + userId; }
    public static String redisOutboxKey(long authorId) { return "feed:outbox:" + authorId; }
    public static String redisFollowedBigVKey(long userId) { return "follow:bigV:" + userId; }
    public static String redisPushDedupKey(long noteId, long userId) { return "feed:dedup:" + noteId + ":" + userId; }
    public static String redisRetryKey(String stream, String recordId) {return "reclaim:" + stream + ":" + recordId;}
}
