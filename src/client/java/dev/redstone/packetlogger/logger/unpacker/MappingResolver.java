package dev.redstone.packetlogger.logger.unpacker;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class MappingResolver {
    
    private static final MappingResolver INSTANCE = new MappingResolver();
    
    private final Map<String, Map<String, String>> fieldMappings = new HashMap<>();
    private final Map<String, String> namedToIntermediary = new HashMap<>();
    private boolean loaded = false;
    
    private MappingResolver() {
        loadMappings();
    }
    
    public static MappingResolver getInstance() {
        return INSTANCE;
    }
    
    private void loadMappings() {
        try {
            String minecraftVersion = FabricLoader.getInstance()
                .getModContainer("minecraft")
                .orElseThrow(() -> new RuntimeException("Minecraft not found"))
                .getMetadata()
                .getVersion()
                .getFriendlyString();
            
            String yarnVersion = MappingDownloader.getYarnVersion(minecraftVersion);
            InputStream is = MappingDownloader.getMappings(yarnVersion);
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            String currentClass = null;
            int fieldCount = 0;
            
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                String[] parts = line.split("\t", -1);
                
                if (parts.length < 2) {
                    continue;
                }
                
                // Class: c<TAB>intermediary<TAB>named
                if (parts[0].equals("c")) {
                    if (parts.length >= 3) {
                        String intermediary = parts[1];
                        String named = parts[2];
                        currentClass = intermediary;
                        namedToIntermediary.put(named.replace('/', '.'), intermediary);
                    }
                // Field: <TAB>f<TAB>descriptor<TAB>intermediary<TAB>named  
                } else if (line.startsWith("\t") && parts.length >= 2 && parts[1].equals("f")) {
                    if (currentClass != null && parts.length >= 5) {
                        String intermediary = parts[3];
                        String named = parts[4];
                        
                        fieldMappings
                            .computeIfAbsent(currentClass, k -> new HashMap<>())
                            .put(intermediary, named);
                        fieldCount++;
                    }
                }
            }
            
            loaded = true;
            System.out.println("[PacketLogger] Loaded " + fieldCount + " field mappings from " + fieldMappings.size() + " classes");
            
        } catch (Exception e) {
            System.err.println("[PacketLogger] Failed to load mappings: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public String resolveFieldName(Class<?> clazz, String fieldName) {
        if (!loaded || clazz == null || fieldName == null) {
            return fieldName;
        }

        String intermediaryClassName = clazz.getName().replace('.', '/');
        Map<String, String> classMappings = fieldMappings.get(intermediaryClassName);
        
        if (classMappings != null) {
            String mapped = classMappings.get(fieldName);
            if (mapped != null) {
                return mapped;
            }
        }

        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            return resolveFieldName(superClass, fieldName);
        }

        return fieldName;
    }
    
    public boolean isLoaded() {
        return loaded;
    }
    
    public String resolveClassName(String className) {
        if (!loaded || className == null) {
            return className;
        }
        
        String intermediaryClassName = className.replace('.', '/');
        
        for (Map.Entry<String, String> entry : namedToIntermediary.entrySet()) {
            if (entry.getValue().equals(intermediaryClassName)) {
                return entry.getKey();
            }
        }
        
        return className;
    }
}
