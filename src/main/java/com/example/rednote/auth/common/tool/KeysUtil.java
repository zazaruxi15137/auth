package com.example.rednote.auth.common.tool;

public class KeysUtil {
    public static String redisInboxKey(long userId) { return "feed:inbox:" + userId; }
    public static String redisAuthorKey(long authorId) { return "feed:author:" + authorId; }
    public static String redisFollowersCacheKey(long authorId) { return "follow:followers:" + authorId; }
    public static String redisPushDedupKey(long noteId, long userId) { return "feed:dedup:" + noteId + ":" + userId; }
    public static String redisRetryKey(String stream, String recordId) {return "reclaim:" + stream + ":" + recordId;}
}
