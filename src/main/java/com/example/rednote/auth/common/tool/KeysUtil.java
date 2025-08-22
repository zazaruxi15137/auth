package com.example.rednote.auth.common.tool;

public class KeysUtil {
    public static String redisInboxKey(long userId) { return "feed:inbox:" + userId; }
    public static String redisOutboxKey(int shard, long authorId) { return "feed:{"+authorId%shard+"}:outbox:" + authorId; }
    public static String redisOutboxKey( long authorId) { return "feed:outbox:" + authorId; }
    public static String redisFollowedBigVKey(long userId) { return "follow:bigV:" + userId; }
    public static String redisPushDedupKey(long noteId, long userId) { return "feed:dedup:" + noteId + ":" + userId; }
    public static String redisRetryKey(String stream, String recordId) {return "reclaim:" + stream + ":" + recordId;}
    public static String redisNoteNullKey(Long noteId){return "note:null:"+noteId;}
    public static String redisNotePageCacheKey(long userId,int page,int size){return "user:notes:"+userId+":"+page+":"+size;}
    public static String redisNoteSingleCacheKey(long noteId){return "note:data:"+noteId;}
    public static String redisUserFeedCacheKey(long userId, long couer, int size){return "note:feed:"+userId+":"+couer+":"+size;}
}
