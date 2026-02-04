package dev.redstone.packetlogger.logger.unpacker;

import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MappingDownloader {
    
    private static final String MAVEN_BASE_URL = "https://maven.fabricmc.net/net/fabricmc/yarn/";
    private static final String CACHE_DIR = "packet-logger/mappings";
    
    public static InputStream getMappings(String yarnVersion) throws IOException {
        Path cacheDir = getCacheDirectory();
        Path cachedFile = cacheDir.resolve(yarnVersion + ".tiny");
        
        if (Files.exists(cachedFile)) {
            System.out.println("[PacketLogger] Using cached mappings for " + yarnVersion);
            return Files.newInputStream(cachedFile);
        }
        
        System.out.println("[PacketLogger] Downloading mappings for " + yarnVersion + "...");
        
        String jarUrl = MAVEN_BASE_URL + yarnVersion + "/yarn-" + yarnVersion + "-v2.jar";
        
        try {
            InputStream mappingsStream = downloadAndExtractMappings(jarUrl);
            
            Files.createDirectories(cacheDir);
            Path tempFile = cachedFile.getParent().resolve(cachedFile.getFileName() + ".tmp");
            
            try (InputStream source = mappingsStream;
                 OutputStream dest = Files.newOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = source.read(buffer)) != -1) {
                    dest.write(buffer, 0, bytesRead);
                }
            }
            
            Files.move(tempFile, cachedFile, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[PacketLogger] Mappings cached successfully");
            
            return Files.newInputStream(cachedFile);
            
        } catch (IOException e) {
            System.err.println("[PacketLogger] Failed to download mappings: " + e.getMessage());
            throw e;
        }
    }
    
    private static InputStream downloadAndExtractMappings(String jarUrl) throws IOException {
        URL url = new URL(jarUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "Fabric-Packet-Logger/1.0");
        
        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("Failed to download mappings: HTTP " + responseCode);
        }
        
        try (ZipInputStream zipIn = new ZipInputStream(connection.getInputStream())) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if (entry.getName().equals("mappings/mappings.tiny")) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = zipIn.read(buffer)) != -1) {
                        baos.write(buffer, 0, bytesRead);
                    }
                    return new ByteArrayInputStream(baos.toByteArray());
                }
                zipIn.closeEntry();
            }
        }
        
        throw new IOException("mappings.tiny not found in JAR");
    }
    
    private static Path getCacheDirectory() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        return gameDir.resolve(CACHE_DIR);
    }
    
    public static String getYarnVersion(String minecraftVersion) {
        return switch (minecraftVersion) {
            case "1.20" -> "1.20+build.1";
            case "1.20.1" -> "1.20.1+build.10";
            case "1.20.2" -> "1.20.2+build.4";
            case "1.20.3" -> "1.20.3+build.1";
            case "1.20.4" -> "1.20.4+build.3";
            case "1.20.5" -> "1.20.5+build.1";
            case "1.20.6" -> "1.20.6+build.3";
            case "1.21" -> "1.21+build.9";
            case "1.21.1" -> "1.21.1+build.3";
            case "1.21.2" -> "1.21.2+build.1";
            case "1.21.3" -> "1.21.3+build.2";
            case "1.21.4" -> "1.21.4+build.8";
            case "1.21.5" -> "1.21.5+build.1";
            case "1.21.6" -> "1.21.6+build.1";
            case "1.21.7" -> "1.21.7+build.8";
            case "1.21.8" -> "1.21.8+build.1";
            case "1.21.9" -> "1.21.9+build.1";
            case "1.21.10" -> "1.21.10+build.3";
            case "1.21.11" -> "1.21.11+build.4";
            default -> minecraftVersion + "+build.1";
        };
    }
}
