package dev.redstone.packetlogger.logger;

import dev.redstone.packetlogger.logger.unpacker.MappingResolver;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.packet.Packet;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class PacketRegistry {
    
    private static final Map<Class<?>, String> packetNames = new HashMap<>();
    private static boolean initialized = false;
    
    public static void initialize() {
        if (initialized) return;
        
        try {
            Set<Class<?>> packetClasses = loadFromCacheOrDiscover();
            
            for (Class<?> packetClass : packetClasses) {
                String resolvedName = resolvePacketName(packetClass);
                packetNames.put(packetClass, resolvedName);
            }
            
            initialized = true;
            System.out.println("[PacketLogger] Loaded " + packetNames.size() + " packet classes");
            
        } catch (Exception e) {
            System.err.println("[PacketLogger] Failed to initialize packets: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static Set<Class<?>> loadFromCacheOrDiscover() {
        try {
            String mcVersion = getMinecraftVersion();
            Path cacheFile = getCacheFile(mcVersion);
            
            if (Files.exists(cacheFile)) {
                Set<Class<?>> cached = loadFromCache(cacheFile);
                if (!cached.isEmpty()) {
                    System.out.println("[PacketLogger] Loaded " + cached.size() + " packets from cache for MC " + mcVersion);
                    return cached;
                }
            }
            
            System.out.println("[PacketLogger] Discovering packets for MC " + mcVersion + "...");
            Set<Class<?>> discovered = findAllPacketClasses();
            saveToCache(cacheFile, discovered);
            System.out.println("[PacketLogger] Cached " + discovered.size() + " packets");
            return discovered;
            
        } catch (Exception e) {
            System.err.println("[PacketLogger] Cache error, falling back to discovery: " + e.getMessage());
            return findAllPacketClasses();
        }
    }
    
    private static String getMinecraftVersion() {
        return FabricLoader.getInstance()
            .getModContainer("minecraft")
            .orElseThrow(() -> new RuntimeException("Minecraft not found"))
            .getMetadata()
            .getVersion()
            .getFriendlyString();
    }
    
    private static Path getCacheFile(String mcVersion) throws IOException {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path cacheDir = gameDir.resolve("packet-logger").resolve("cache");
        Files.createDirectories(cacheDir);
        return cacheDir.resolve("packets_" + mcVersion.replace(".", "_") + ".cache");
    }
    
    private static Set<Class<?>> loadFromCache(Path cacheFile) {
        Set<Class<?>> packets = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(cacheFile.toFile()))) {
            String line;
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                try {
                    Class<?> clazz = Class.forName(line, false, classLoader);
                    packets.add(clazz);
                } catch (ClassNotFoundException e) {
                    // Class no longer exists, cache is stale
                    return new HashSet<>();
                }
            }
        } catch (IOException e) {
            return new HashSet<>();
        }
        return packets;
    }
    
    private static void saveToCache(Path cacheFile, Set<Class<?>> packets) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(cacheFile.toFile()))) {
            writer.write("# Packet Logger - Cached packet classes\n");
            writer.write("# Generated: " + new java.util.Date() + "\n");
            writer.write("# Do not edit manually\n");
            for (Class<?> clazz : packets) {
                writer.write(clazz.getName());
                writer.write("\n");
            }
        } catch (IOException e) {
            System.err.println("[PacketLogger] Failed to save cache: " + e.getMessage());
        }
    }
    
    private static Set<Class<?>> findAllPacketClasses() {
        Set<Class<?>> packets = new HashSet<>();
        
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            int consecutiveNotFound = 0;
            int maxConsecutiveNotFound = 500;
            
            // Brute force scan class_XXXX range - Fabric remaps these at runtime
            for (int i = 0; i < 20000; i++) {
                try {
                    String className = "net.minecraft.class_" + i;
                    Class<?> clazz = Class.forName(className, false, classLoader);
                    consecutiveNotFound = 0;
                    
                    // Check if this class is a Packet
                    if (Packet.class.isAssignableFrom(clazz) && !clazz.isInterface()) {
                        // Add if concrete
                        if (!java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                            packets.add(clazz);
                        }
                        
                        // Scan declared inner classes (e.g., PlayerMoveC2SPacket.Full)
                        try {
                            Class<?>[] innerClasses = clazz.getDeclaredClasses();
                            for (Class<?> innerClass : innerClasses) {
                                if (Packet.class.isAssignableFrom(innerClass) && 
                                    !innerClass.isInterface() && 
                                    !java.lang.reflect.Modifier.isAbstract(innerClass.getModifiers())) {
                                    packets.add(innerClass);
                                }
                            }
                        } catch (Exception e) {
                            // Can't access inner classes, continue
                        }
                    }
                    
                } catch (ClassNotFoundException e) {
                    consecutiveNotFound++;
                    if (consecutiveNotFound >= maxConsecutiveNotFound) {
                        break;
                    }
                } catch (LinkageError e) {
                    consecutiveNotFound = 0;
                }
            }
            
        } catch (Exception e) {
            System.err.println("[PacketLogger] Error scanning for packet classes: " + e.getMessage());
        }
        
        return packets;
    }
    
    
    
    private static String resolvePacketName(Class<?> packetClass) {
        String className = packetClass.getName();
        String simpleName = packetClass.getSimpleName();
        
        MappingResolver resolver = MappingResolver.getInstance();
        if (!resolver.isLoaded()) {
            return simpleName.isEmpty() ? className : simpleName;
        }
        
        String namedClass = resolver.resolveClassName(className);
        if (namedClass != null && !namedClass.equals(className)) {
            int lastDot = namedClass.lastIndexOf('.');
            if (lastDot >= 0) {
                return namedClass.substring(lastDot + 1);
            }
            return namedClass;
        }
        
        if (!simpleName.isEmpty()) {
            return simpleName;
        }
        
        int lastDot = className.lastIndexOf('.');
        if (lastDot >= 0) {
            return className.substring(lastDot + 1);
        }
        
        return className;
    }
    
    public static String getPacketName(Class<?> packetClass) {
        if (!initialized) {
            initialize();
        }
        
        String name = packetNames.get(packetClass);
        if (name != null) {
            return name;
        }
        
        String simpleName = packetClass.getSimpleName();
        return simpleName.isEmpty() ? packetClass.getName() : simpleName;
    }
    
    public static Set<String> getAllPacketNames() {
        if (!initialized) {
            initialize();
        }
        return new HashSet<>(packetNames.values());
    }
    
    public static Set<Class<?>> getAllPacketClasses() {
        if (!initialized) {
            initialize();
        }
        return new HashSet<>(packetNames.keySet());
    }
}
