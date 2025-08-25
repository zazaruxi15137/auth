package com.example.rednote.auth.common.service;



import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import lombok.extern.slf4j.Slf4j;

/**
 * 阻塞式令牌桶（支持批量原子获取）：
 * - Semaphore(公平) 做批量原子获取/退款
 * - 后台线程按 rate × elapsed 定时补给（小数累积 carry）
 * - setCapacity/setRatePerSecond 动态调整
 */
@Slf4j
public class BlockingTokenBucket implements AutoCloseable {

    /** 令牌上限（容量） */
    private volatile int capacity;
    /** 生成速率：令牌/秒（可小数） */
    private volatile double ratePerSecond;
    /** 补给周期（毫秒） */
    private volatile long refillPeriodMillis;

    /** 公平信号量，用于批量原子获取 */
    private final Semaphore sem;
    /** 定时补给线程 */
    private final ScheduledExecutorService scheduler;

    /** 补给与容量相关操作的互斥锁，避免超过 capacity */
    private final ReentrantLock tokenLock = new ReentrantLock();
    /** 配置类操作的互斥（可与 tokenLock 合并，这里分离便于阅读） */
    private final ReentrantLock cfgLock = new ReentrantLock();

    private volatile boolean running = true;
    private volatile long lastRefillNanos = System.nanoTime();
    private double carry = 0.0; // 小数令牌累计

    // ================== 构造 ==================
    public BlockingTokenBucket(int capacity, double ratePerSecond) {
        this(capacity, ratePerSecond, 100);
    }
    public BlockingTokenBucket(int capacity, double ratePerSecond, long refillPeriodMillis) {
        if (capacity <= 0 || ratePerSecond <= 0) {
            throw new IllegalArgumentException("capacity/rate must be > 0");
        }
        this.capacity = capacity;
        this.ratePerSecond = ratePerSecond;
        this.refillPeriodMillis = Math.max(5, refillPeriodMillis);

        // 初始“装满”= 有 capacity 个可用令牌
        this.sem = new Semaphore(capacity, /*fair*/ true);

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "token-bucket-refill");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleAtFixedRate(this::refill, this.refillPeriodMillis, this.refillPeriodMillis, TimeUnit.MILLISECONDS);
    }

    // ================== 获取（批量原子） ==================

    /** 阻塞直到取得 permits 个令牌；可被中断 */
    public void acquire(int permits) throws InterruptedException {
        if (permits <= 0) return;
        // 注意：Semaphore#acquire(int) 是可中断阻塞，原子获取
        sem.acquire(permits);
    }

    /** 最长等待 timeout，拿不到返回 false；等待中被中断则抛 InterruptedException */
    public int acquire(Duration timeout, int permits) throws InterruptedException {
        if (permits <= 0) return -1;
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            acquire(permits); // 等价于无限等待
            return -11;
        }
        long ms = timeout.toMillis();
        if(sem.tryAcquire(permits, ms, TimeUnit.MILLISECONDS)){
            return -1;
        }
        return permits - sem.availablePermits();
    }


    // ================== 退款（批量） ==================

    /** 归还 n 个令牌（饱和，不超过 capacity） */
    public void release(int permits) {
        if (permits <= 0) return;
        tokenLock.lock();
        try {
            int avail = sem.availablePermits();
            int space = capacity - avail;
            int toRelease = Math.min(space, permits);
            if (toRelease > 0) {
                sem.release(toRelease);
            }
            // 如果没有空间，丢弃多余退款（与原 ArrayBlockingQueue.offer 饱和语义一致）
        } finally {
            tokenLock.unlock();
        }
    }

    /** 兼容你原有的单个 release() */
    public void release() { release(1); }

    // ================== 补给（定时线程） ==================

    /** 周期补给：按 rate × elapsed 累计，小数通过 carry 进位；永不超过 capacity */
    private void refill() {
        if (!running) return;
        try {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            lastRefillNanos = now;

            double tokens = ratePerSecond * elapsedSeconds;
            carry += tokens;
            int whole = (int) Math.floor(carry);
            if (whole <= 0) return;
            carry -= whole;

            tokenLock.lock();
            try {
                int avail = sem.availablePermits();
                int space = capacity - avail;
                int toOffer = Math.min(space, whole);
                if (toOffer > 0) {
                    sem.release(toOffer);
                }
            } finally {
                tokenLock.unlock();
            }
        } catch (Throwable ignore) {
        }
    }

    // ================== 动态配置 ==================

    /** 动态调整容量（缩容不影响已借出的令牌，仅限制可用上限） */
    public void setCapacity(int newCap) {
        if (newCap <= 0) throw new IllegalArgumentException("capacity must be > 0");
        cfgLock.lock();
        tokenLock.lock();
        try {
            if (newCap == this.capacity) return;

            // 当前在用 = 旧容量 - 可用
            int curAvail = sem.availablePermits();
            int inUse = Math.max(0, this.capacity - curAvail);

            this.capacity = newCap;

            // 目标可用 = max(0, 新容量 - 在用)
            int targetAvail = Math.max(0, newCap - inUse);

            // 通过 drain + release 精准设置当前可用为 targetAvail
            int drained = sem.drainPermits();
            int toRelease = Math.max(0, targetAvail);
            if (toRelease > 0) sem.release(toRelease);

            // carry 不变（让后续补给自然对齐）
        } finally {
            tokenLock.unlock();
            cfgLock.unlock();
        }
    }

    /** 动态调整速率（令牌/秒） */
    public void setRatePerSecond(double newRate) {
        if (newRate <= 0) throw new IllegalArgumentException("rate must be > 0");
        cfgLock.lock();
        try {
            this.ratePerSecond = newRate;
        } finally {
            cfgLock.unlock();
        }
    }

    /** 动态调整补给周期（毫秒） */
    public void setRefillPeriodMillis(long millis) {
        cfgLock.lock();
        try {
            this.refillPeriodMillis = Math.max(5, millis);
            // 简化处理：下个周期自然按新的 period 触发（scheduleAtFixedRate 的 period 不变，
            // 但我们用 elapsedSeconds 计算真实补给量，所以 period 改不改影响不大；
            // 若要严格按新 period 触发，可重建 scheduler。）
        } finally {
            cfgLock.unlock();
        }
    }

    /** 近似可用令牌数（仅用于观测） */
    public int availableTokens() { return sem.availablePermits(); }

    // ================== 生命周期 ==================
    @Override
    public void close() {
        running = false;
        scheduler.shutdownNow();
    }
}