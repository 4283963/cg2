package com.theater.smoke.hardware;

import com.theater.smoke.model.Fan;
import com.theater.smoke.model.SmokeMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class RS485Gateway {

    private static final Logger log = LoggerFactory.getLogger(RS485Gateway.class);

    private static final int FAN_ADDR_BASE = 0x10;
    private static final int SMOKE_ADDR_BASE = 0x20;
    private static final long PORT_BUSY_BACKOFF_MS = 80L;
    private static final int PORT_BUSY_MAX_ATTEMPTS = 6;

    @Value("${hardware.rs485.enabled:false}")
    private boolean hardwareEnabled;

    @Value("${hardware.rs485.host:192.168.1.100}")
    private String gatewayHost;

    @Value("${hardware.rs485.port:502}")
    private int gatewayPort;

    @Value("${hardware.rs485.timeout:3000}")
    private int timeoutMs;

    @Value("${hardware.rs485.retry-count:3}")
    private int retryCount;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final ConcurrentHashMap<Integer, FanState> fanStateCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, SmokeMachineState> machineStateCache = new ConcurrentHashMap<>();

    private final ReentrantLock serialPortLock = new ReentrantLock(true);
    private final ExecutorService senderExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "rs485-sender");
        t.setDaemon(true);
        t.setUncaughtExceptionHandler((t1, e) -> log.error("[RS485] 发送线程异常", e));
        return t;
    });

    private final ConcurrentHashMap<Integer, QueuedCommand> pendingCommands = new ConcurrentHashMap<>();
    private volatile long lastSuccessfulSendAtMs = 0L;

    @PostConstruct
    public void start() {
        connect();
    }

    @PreDestroy
    public void shutdown() {
        senderExecutor.shutdownNow();
        disconnect();
    }

    public boolean connect() {
        serialPortLock.lock();
        try {
            if (!hardwareEnabled) {
                log.info("[RS485] 硬件模拟模式，跳过真实连接");
                connected.set(true);
                return true;
            }
            try {
                log.info("[RS485] 正在连接网关 {}:{}...", gatewayHost, gatewayPort);
                Thread.sleep(200);
                connected.set(true);
                lastSuccessfulSendAtMs = System.currentTimeMillis();
                log.info("[RS485] 网关连接成功");
                return true;
            } catch (Exception e) {
                log.error("[RS485] 网关连接失败", e);
                connected.set(false);
                return false;
            }
        } finally {
            serialPortLock.unlock();
        }
    }

    public void disconnect() {
        connected.set(false);
        pendingCommands.clear();
        log.info("[RS485] 网关已断开，待发送队列已清空");
    }

    public boolean isConnected() {
        return connected.get();
    }

    public CompletableFuture<Boolean> sendFanCommandAsync(Fan fan) {
        int address = FAN_ADDR_BASE + fan.getId();
        byte[] packet = buildFanPacket(address, fan.getSpeedPercent(), fan.getBlowDirectionDegrees());
        fanStateCache.put(fan.getId(), new FanState(fan.getSpeedPercent(), fan.getBlowDirectionDegrees(), fan.isActive()));
        String desc = String.format("风机[%s] 速度=%d%% 方向=%.1f°",
                fan.getName(), fan.getSpeedPercent(), fan.getBlowDirectionDegrees());
        return enqueueCommand(address, packet, desc);
    }

    public CompletableFuture<Boolean> sendSmokeMachineCommandAsync(SmokeMachine machine) {
        int address = SMOKE_ADDR_BASE + machine.getId();
        byte[] packet = buildSmokeMachinePacket(address, machine.getOutputPercent(), machine.isActive());
        machineStateCache.put(machine.getId(), new SmokeMachineState(machine.getOutputPercent(), machine.isActive()));
        String desc = String.format("干冰机[%s] 输出=%d%% 状态=%s",
                machine.getName(), machine.getOutputPercent(), machine.isActive() ? "启动" : "停止");
        return enqueueCommand(address, packet, desc);
    }

    public CompletableFuture<Integer> submitBatch(Fan[] fans, SmokeMachine[] machines) {
        CompletableFuture<Boolean>[] futures = new CompletableFuture[fans.length + machines.length];
        int idx = 0;
        for (Fan fan : fans) {
            futures[idx++] = sendFanCommandAsync(fan);
        }
        for (SmokeMachine machine : machines) {
            futures[idx++] = sendSmokeMachineCommandAsync(machine);
        }
        flushQueuedCommands();
        return CompletableFuture.allOf(futures)
                .thenApply(v -> {
                    int success = 0;
                    for (CompletableFuture<Boolean> f : futures) {
                        try {
                            if (Boolean.TRUE.equals(f.getNow(false))) success++;
                        } catch (Exception ignored) {
                        }
                    }
                    return success;
                });
    }

    @Deprecated
    public boolean sendFanCommand(Fan fan) {
        try {
            return sendFanCommandAsync(fan).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[RS485] sendFanCommand同步调用超时/异常: {}", e.getMessage());
            return false;
        }
    }

    @Deprecated
    public boolean sendSmokeMachineCommand(SmokeMachine machine) {
        try {
            return sendSmokeMachineCommandAsync(machine).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[RS485] sendSmokeMachineCommand同步调用超时/异常: {}", e.getMessage());
            return false;
        }
    }

    private CompletableFuture<Boolean> enqueueCommand(int address, byte[] packet, String description) {
        QueuedCommand cmd = new QueuedCommand(address, packet, description, System.nanoTime());
        QueuedCommand prev = pendingCommands.put(address, cmd);
        if (prev != null && !prev.future.isDone()) {
            prev.future.complete(false);
            log.debug("[RS485] 合并重复指令 地址=0x{} 旧:[{}] 新:[{}]",
                    Integer.toHexString(address), prev.description, description);
        }
        return cmd.future;
    }

    private void flushQueuedCommands() {
        senderExecutor.submit(() -> {
            if (pendingCommands.isEmpty()) return;
            ConcurrentHashMap.KeySetView<Integer, Boolean> keys = ConcurrentHashMap.newKeySet();
            keys.addAll(pendingCommands.keySet());
            int total = keys.size();
            int successCount = 0;
            long t0 = System.currentTimeMillis();

            for (Integer address : keys) {
                QueuedCommand cmd = pendingCommands.remove(address);
                if (cmd == null || cmd.future.isDone()) continue;
                boolean ok = doSendLocked(cmd.address, cmd.packet, cmd.description);
                cmd.future.complete(ok);
                if (ok) successCount++;
            }

            long dt = System.currentTimeMillis() - t0;
            log.info("[RS485] 批处理完成 成功={}/{} 耗时={}ms", successCount, total, dt);
        });
    }

    private boolean doSendLocked(int deviceAddress, byte[] packet, String description) {
        if (!connected.get()) {
            log.warn("[RS485] 网关未连接，跳过发送 -> {}", description);
            return false;
        }
        String hex = bytesToHex(packet);

        boolean locked = false;
        try {
            locked = serialPortLock.tryLock(200, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("[RS485] 串口锁获取被中断");
            return false;
        }
        if (!locked) {
            log.warn("[RS485] 等待串口锁超时，丢弃指令 -> {}", description);
            return false;
        }
        try {
            for (int attempt = 1; attempt <= Math.max(retryCount, PORT_BUSY_MAX_ATTEMPTS); attempt++) {
                try {
                    long minGapMs = hardwareEnabled ? 12L : 0L;
                    long now = System.currentTimeMillis();
                    long sinceLast = now - lastSuccessfulSendAtMs;
                    if (sinceLast < minGapMs) {
                        Thread.sleep(minGapMs - sinceLast);
                    }

                    if (hardwareEnabled) {
                        Thread.sleep(10);
                    }

                    log.info("[RS485] 发送指令 [尝试{}/{}] 锁={}ms 地址=0x{} HEX=[{}] -> {}",
                            attempt, retryCount,
                            serialPortLock.getHoldCount() > 0 ? "已获取" : "未获取",
                            Integer.toHexString(deviceAddress), hex, description);

                    lastSuccessfulSendAtMs = System.currentTimeMillis();
                    return true;

                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                } catch (Exception e) {
                    String msg = e.getMessage() == null ? "null" : e.getMessage().toLowerCase();
                    boolean isPortBusy = msg.contains("busy")
                            || msg.contains("port busy")
                            || msg.contains("资源忙")
                            || msg.contains("占用");

                    log.warn("[RS485] 发送失败 [尝试{}/{}] 端口忙={} err={} -> {}",
                            attempt, retryCount, isPortBusy, e.getMessage(), description);

                    if (attempt >= retryCount && attempt >= PORT_BUSY_MAX_ATTEMPTS) {
                        log.error("[RS485] 重试{}次后仍失败，放弃发送: {}", attempt, description);
                        return false;
                    }

                    long backoff;
                    if (isPortBusy) {
                        backoff = PORT_BUSY_BACKOFF_MS * attempt + ThreadLocalRandom.current().nextLong(20L);
                        log.warn("[RS485] 检测到PortBusy，指数退避 {}ms 后重试", backoff);
                    } else {
                        backoff = 50L * attempt;
                    }
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
            return false;
        } finally {
            try {
                if (serialPortLock.isHeldByCurrentThread()) {
                    serialPortLock.unlock();
                }
            } catch (IllegalMonitorStateException ignored) {
            }
        }
    }

    private byte[] buildFanPacket(int address, int speedPercent, double directionDegrees) {
        int direction = (int) ((directionDegrees + 360) % 360 * 10);
        byte[] data = new byte[8];
        data[0] = (byte) (address & 0xFF);
        data[1] = 0x06;
        data[2] = 0x00;
        data[3] = 0x10;
        data[4] = (byte) (speedPercent & 0xFF);
        data[5] = (byte) ((direction >> 8) & 0xFF);
        data[6] = (byte) (direction & 0xFF);
        data[7] = calculateCRC(data);
        return data;
    }

    private byte[] buildSmokeMachinePacket(int address, int outputPercent, boolean active) {
        byte[] data = new byte[8];
        data[0] = (byte) (address & 0xFF);
        data[1] = 0x06;
        data[2] = 0x00;
        data[3] = 0x20;
        data[4] = (byte) (active ? 0x01 : 0x00);
        data[5] = (byte) (outputPercent & 0xFF);
        data[6] = 0x00;
        data[7] = calculateCRC(data);
        return data;
    }

    private byte calculateCRC(byte[] data) {
        int crc = 0xFFFF;
        for (int i = 0; i < data.length - 1; i++) {
            crc ^= (data[i] & 0xFF);
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x0001) != 0) {
                    crc >>= 1;
                    crc ^= 0xA001;
                } else {
                    crc >>= 1;
                }
            }
        }
        return (byte) (crc & 0xFF);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    private static class QueuedCommand {
        final int address;
        final byte[] packet;
        final String description;
        final long enqueuedAtNs;
        final CompletableFuture<Boolean> future;

        QueuedCommand(int address, byte[] packet, String description, long enqueuedAtNs) {
            this.address = address;
            this.packet = packet;
            this.description = description;
            this.enqueuedAtNs = enqueuedAtNs;
            this.future = new CompletableFuture<>();
        }
    }

    private static class FanState {
        final int speed;
        final double direction;
        final boolean active;
        FanState(int speed, double direction, boolean active) {
            this.speed = speed;
            this.direction = direction;
            this.active = active;
        }
    }

    private static class SmokeMachineState {
        final int output;
        final boolean active;
        SmokeMachineState(int output, boolean active) {
            this.output = output;
            this.active = active;
        }
    }
}
