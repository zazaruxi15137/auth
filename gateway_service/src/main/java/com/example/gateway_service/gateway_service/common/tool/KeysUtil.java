package com.example.gateway_service.gateway_service.common.tool;

public class KeysUtil {
    public static String redisInboxKey(long userId,long shard) { return "feed:{"+shard+"}:inbox:" + userId; }
    public static String redisOutboxKey(long shard, long authorId) { return "feed:{"+shard+"}:outbox:" + authorId; }
    public static String redisInboxAuthorIndex(long userId, long authorId){return "inbox:{"+userId+"}:byAuthor:{"+authorId+"}";}
    public static String redisFollowedBigVKey(long userId) { return "follow:bigV:" + userId; }
    public static String redisPushDedupKey(long noteId, long userId) { return "feed:dedup:" + noteId + ":" + userId; }
    public static String redisRetryKey(String stream, String recordId) {return "reclaim:" + stream + ":" + recordId;}
    //查询缓存key
    public static String redisNoteNullKey(Long noteId){return "note:null:"+noteId;}
    public static String redisNoteSingleCacheKey(long noteId){return "note:data:"+noteId;}
    public static String redisNotePageCacheKey(long userId,int page,int size){return "user:notes:"+userId+":"+page+":"+size;}
    public static String redisNotePageCacheKeySet(long userId){return "user:notes:"+userId;}
    public static String redisUserFeedCacheKey(long userId, long couer, int size){return "note:feed:"+userId+":"+couer+":"+size;}
    public static String redisUserFeedCacheKeySet(long userId){return "note:feed:"+userId;}

        // 令牌桶状态/配置（带相同 hash tag）
    public static String bucketKeyByUser(long userId) { return "rl:{u:" + userId + "}:bucket"; }
    public static String confKeyByUser(long userId)   { return "rl:{u:" + userId + "}:conf"; }

    public static String bucketKeyByIp(String ip) { return "rl:{ip:" + ip + "}:bucket"; }
    public static String confKeyByIp(String ip)   { return "rl:{ip:" + ip + "}:conf"; }

    // 黑名单（单 key，TTL）
    public static String blacklistUser(long userId) { return "bl:{u:" + userId + "}"; }
    public static String blacklistIp(String ip)     { return "bl:{ip:" + ip + "}"; }

    // 若需按“接口维度”限流：把接口签名加入 hash tag，注意 bucket/conf 两个 key 的 tag 必须一致
    public static String bucketKeyByUserAndApi(long userId, String apiTag) {
        return "rl:{u:" + userId + "|" + apiTag + "}:bucket";
    }
    public static String confKeyByUserAndApi(long userId, String apiTag) {
        return "rl:{u:" + userId + "|" + apiTag + "}:conf";
    }
    public static String bucketKeyByIpAndApi(String ip, String apiTag) {
        return "rl:{ip:" + ip + "|" + apiTag + "}:bucket";
    }
    public static String confKeyByIpAndApi(String ip, String apiTag) {
        return "rl:{ip:" + ip + "|" + apiTag + "}:conf";
    }
}
