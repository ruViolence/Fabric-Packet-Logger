package dev.redstone.packetlogger.logger;

import dev.redstone.packetlogger.config.ModConfig;
import dev.redstone.packetlogger.logger.unpacker.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Deep Packet Logger - Loggt alle Netzwerk-Pakete mit vollständigen Daten.
 */
public class PacketLogger {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    
    private static Path currentLogFile = null;
    private static String currentSessionId = null;
    private static boolean wasLoggingEnabled = false;
    
    private static final Map<Class<?>, PacketUnpacker<?>> UNPACKERS = new HashMap<>();
    
    static {
        registerUnpackers();
        PacketRegistry.initialize();
    }
    
    private static void registerUnpackers() {
        try {
            registerUnpacker("InventoryS2CPacket", new InventoryS2CUnpacker());
            registerUnpacker("ScreenHandlerSlotUpdateS2CPacket", new SlotUpdateS2CUnpacker());
            registerUnpacker("CreativeInventoryActionC2SPacket", new CreativeInventoryC2SUnpacker());
            registerUnpacker("ClickSlotC2SPacket", new ClickSlotC2SUnpacker());
            registerUnpacker("BlockEntityUpdateS2CPacket", new BlockEntityUpdateS2CUnpacker());
            registerUnpacker("BlockUpdateS2CPacket", new BlockUpdateS2CUnpacker());
            registerUnpacker("ChunkDeltaUpdateS2CPacket", new ChunkDeltaUpdateS2CUnpacker());
            registerUnpacker("EntityTrackerUpdateS2CPacket", new EntityTrackerUpdateS2CUnpacker());
            registerUnpacker("EntityAttributesS2CPacket", new EntityAttributesS2CUnpacker());
            registerUnpacker("EntitySpawnS2CPacket", new EntitySpawnS2CUnpacker());
            registerUnpacker("ChunkDataS2CPacket", new ChunkDataS2CUnpacker());
            registerUnpacker("NbtQueryResponseS2CPacket", new NbtQueryResponseS2CUnpacker());
            registerUnpacker("CustomPayloadS2CPacket", new CustomPayloadS2CUnpacker());
            registerUnpacker("CustomPayloadC2SPacket", new CustomPayloadC2SUnpacker());
        } catch (Exception e) {
            System.err.println("[PacketLogger] Error registering unpackers: " + e.getMessage());
        }
    }
    
    private static void registerUnpacker(String packetName, PacketUnpacker<?> unpacker) {
        for (Class<?> clazz : PacketRegistry.getAllPacketClasses()) {
            if (PacketRegistry.getPacketName(clazz).equals(packetName)) {
                UNPACKERS.put(clazz, unpacker);
                return;
            }
        }
    }
    
    public static void onWorldJoin(String worldName) {
        if (ModConfig.getInstance().logMode == ModConfig.LogMode.FILE) {
            currentSessionId = null;
            currentLogFile = null;
            System.out.println("[PacketLogger] New session started: " + worldName);
        }
    }
    
    public static void onWorldLeave() {
        if (currentLogFile != null && ModConfig.getInstance().logMode == ModConfig.LogMode.FILE) {
            try {
                synchronized (PacketLogger.class) {
                    try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(currentLogFile.toFile(), true)))) {
                        writer.println();
                        writer.println("=== Session ended: " + LocalDateTime.now().format(FILE_DATE_FORMAT) + " ===");
                    }
                }
            } catch (IOException e) { }
        }
        currentSessionId = null;
        currentLogFile = null;
    }
    
    public static void logIncoming(Packet<?> packet) {
        if (isBundlePacket(packet)) {
            unpackAndLogBundle(packet);
        } else {
            logPacket(packet, true);
        }
    }
    
    public static void logOutgoing(Packet<?> packet) {
        logPacket(packet, false);
    }

    private static void logPacket(Packet<?> packet, boolean incoming) {
        ModConfig config = ModConfig.getInstance();
        
        if (config.logPackets && !wasLoggingEnabled && config.logMode == ModConfig.LogMode.FILE) {
            currentSessionId = null;
            currentLogFile = null;
        }
        wasLoggingEnabled = config.logPackets;
        
        if (!config.logPackets) return;
        
        String simpleName = getDeobfuscatedName(packet);
        
        if (incoming) {
            if (!shouldLogS2C(simpleName, config)) return;
        } else {
            if (!shouldLogC2S(simpleName, config)) return;
        }
        
        String timestamp = LocalTime.now().format(TIME_FORMAT);
        String direction = incoming ? "S2C" : "C2S";
        String packetData = unpackPacket(packet);
        
        if (config.logMode == ModConfig.LogMode.CHAT) {
            logToChat(timestamp, direction, incoming, simpleName, packetData);
        } else {
            logToFile(timestamp, direction, simpleName, packetData);
        }
    }
    
    private static boolean isBundlePacket(Packet<?> packet) {
        String name = PacketRegistry.getPacketName(packet.getClass());
        return name.equals("BundleS2CPacket");
    }
    
    private static void unpackAndLogBundle(Packet<?> bundlePacket) {
        try {
            BundleS2CPacket bundle = (BundleS2CPacket) bundlePacket;
            Iterable<Packet<? super ClientPlayPacketListener>> packets = bundle.getPackets();
            
            for (Packet<? super ClientPlayPacketListener> innerPacket : packets) {
                logPacket(innerPacket, true);
            }
        } catch (Exception e) {
            System.err.println("[PacketLogger] Failed to unpack bundle: " + e.getMessage());
            logPacket(bundlePacket, true);
        }
    }
    
    private static String getDeobfuscatedName(Packet<?> packet) {
        return PacketRegistry.getPacketName(packet.getClass());
    }
    
    private static boolean shouldLogS2C(String simpleName, ModConfig config) {
        if (config.selectedS2CPackets.isEmpty()) return false;
        for (String selected : config.selectedS2CPackets) {
            if (simpleName.equals(selected) || simpleName.endsWith(selected)) return true;
        }
        return false;
    }
    
    private static boolean shouldLogC2S(String simpleName, ModConfig config) {
        if (config.selectedC2SPackets.isEmpty()) return false;
        for (String selected : config.selectedC2SPackets) {
            if (simpleName.equals(selected) || simpleName.endsWith(selected)) return true;
        }
        return false;
    }
    
    @SuppressWarnings("unchecked")
    private static String unpackPacket(Packet<?> packet) {
        try {
            PacketUnpacker<Packet<?>> unpacker = (PacketUnpacker<Packet<?>>) UNPACKERS.get(packet.getClass());
            if (unpacker != null) return unpacker.unpack(packet);
            return ReflectionUnpacker.unpackWithReflection(packet);
        } catch (Exception e) {
            return "{error: \"" + e.getMessage() + "\"}";
        }
    }

    private static void logToChat(String timestamp, String direction, boolean incoming, String packetName, String packetData) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud == null || client.inGameHud.getChatHud() == null) return;
        
        MutableText timeText = Text.literal("[" + timestamp + "] ").formatted(Formatting.GRAY);
        MutableText dirText = Text.literal("[" + direction + "] ").formatted(incoming ? Formatting.GREEN : Formatting.RED);
        MutableText nameText = Text.literal(packetName + " ").formatted(Formatting.YELLOW);
        String shortData = packetData.length() > 300 ? packetData.substring(0, 300) + "..." : packetData;
        MutableText dataText = Text.literal(shortData).formatted(Formatting.WHITE);
        
        MutableText fullMessage = Text.empty().append(timeText).append(dirText).append(nameText).append(dataText);
        
        // Run on main thread to avoid concurrent modification issues with chat mods like ChatPlus
        client.execute(() -> {
            if (client.inGameHud != null && client.inGameHud.getChatHud() != null) {
                client.inGameHud.getChatHud().addMessage(fullMessage);
            }
        });
    }
    
    private static void logToFile(String timestamp, String direction, String packetName, String packetData) {
        try {
            Path logFile = getLogFile();
            String logLine = String.format("[%s] [%s] %s %s%n", timestamp, direction, packetName, packetData);
            synchronized (PacketLogger.class) {
                try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(logFile.toFile(), true)))) {
                    writer.print(logLine);
                }
            }
        } catch (IOException e) {
            System.err.println("[PacketLogger] Error writing to log file: " + e.getMessage());
        }
    }
    
    private static Path getLogFile() throws IOException {
        if (currentSessionId == null || currentLogFile == null) {
            currentSessionId = LocalDateTime.now().format(FILE_DATE_FORMAT);
            Path gameDir = FabricLoader.getInstance().getGameDir();
            Path logDir = gameDir.resolve("packet-logger");
            Files.createDirectories(logDir);
            
            String worldName = getWorldName();
            String fileName = "packets_" + currentSessionId + "_" + worldName + ".log";
            currentLogFile = logDir.resolve(fileName);
            
            try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(currentLogFile.toFile())))) {
                writer.println("=== Deep Packet Logger ===");
                writer.println("Session: " + currentSessionId);
                writer.println("World: " + worldName);
                writer.println("Format: [TIME] [DIRECTION] PacketName {deep_data}");
                writer.println("==========================================");
                writer.println();
            }
            System.out.println("[PacketLogger] Created new log file: " + fileName);
        }
        return currentLogFile;
    }
    
    private static String getWorldName() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                if (client.getCurrentServerEntry() != null) {
                    return sanitizeFileName(client.getCurrentServerEntry().address);
                }
                if (client.getServer() != null && client.getServer().getSaveProperties() != null) {
                    return sanitizeFileName(client.getServer().getSaveProperties().getLevelName());
                }
            }
        } catch (Exception e) { }
        return "unknown";
    }
    
    private static String sanitizeFileName(String name) {
        if (name == null) return "unknown";
        String sanitized = name.replaceAll("[^a-zA-Z0-9._-]", "_").replaceAll("_+", "_");
        return sanitized.substring(0, Math.min(sanitized.length(), 50));
    }
}
