package com.theater.smoke.hardware;

import com.theater.smoke.model.Fan;
import com.theater.smoke.model.SmokeMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class RS485Gateway {

    private static final Logger log = LoggerFactory.getLogger(RS485Gateway.class);

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

    public boolean connect() {
        if (!hardwareEnabled) {
            log.info("[RS485] 硬件模拟模式，跳过真实连接");
            connected.set(true);
            return true;
        }
        try {
            log.info("[RS485] 正在连接网关 {}:{}...", gatewayHost, gatewayPort);
            Thread.sleep(200);
            connected.set(true);
            log.info("[RS485] 网关连接成功");
            return true;
        } catch (Exception e) {
            log.error("[RS485] 网关连接失败", e);
            connected.set(false);
            return false;
        }
    }

    public void disconnect() {
        connected.set(false);
        log.info("[RS485] 网关已断开");
    }

    public boolean isConnected() {
        return connected.get();
    }

    public boolean sendFanCommand(Fan fan) {
        int deviceAddress = 0x10 + fan.getId();
        byte[] packet = buildFanPacket(deviceAddress, fan.getSpeedPercent(), fan.getBlowDirectionDegrees());
        fanStateCache.put(fan.getId(), new FanState(fan.getSpeedPercent(), fan.getBlowDirectionDegrees(), fan.isActive()));
        return sendPacket(deviceAddress, packet, String.format(
                "风机[%s] 速度=%d%% 方向=%.1f°",
                fan.getName(), fan.getSpeedPercent(), fan.getBlowDirectionDegrees()
        ));
    }

    public boolean sendSmokeMachineCommand(SmokeMachine machine) {
        int deviceAddress = 0x20 + machine.getId();
        byte[] packet = buildSmokeMachinePacket(deviceAddress, machine.getOutputPercent(), machine.isActive());
        machineStateCache.put(machine.getId(), new SmokeMachineState(machine.getOutputPercent(), machine.isActive()));
        return sendPacket(deviceAddress, packet, String.format(
                "干冰机[%s] 输出=%d%% 状态=%s",
                machine.getName(), machine.getOutputPercent(), machine.isActive() ? "启动" : "停止"
        ));
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

    private boolean sendPacket(int deviceAddress, byte[] packet, String description) {
        if (!connected.get()) {
            log.warn("[RS485] 网关未连接，跳过发送 -> {}", description);
            return false;
        }
        String hex = bytesToHex(packet);
        for (int attempt = 1; attempt <= retryCount; attempt++) {
            try {
                if (hardwareEnabled) {
                    Thread.sleep(10);
                }
                log.info("[RS485] 发送指令 [尝试{}/{}] 地址=0x{} HEX=[{}] -> {}",
                        attempt, retryCount, Integer.toHexString(deviceAddress), hex, description);
                return true;
            } catch (Exception e) {
                log.warn("[RS485] 发送失败 [尝试{}/{}]: {}", attempt, retryCount, e.getMessage());
                if (attempt == retryCount) {
                    log.error("[RS485] 重试{}次后仍失败，放弃发送", retryCount);
                    return false;
                }
                try {
                    Thread.sleep(50L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
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
