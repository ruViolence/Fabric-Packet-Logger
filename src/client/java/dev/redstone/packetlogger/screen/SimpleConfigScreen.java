package dev.redstone.packetlogger.screen;

import dev.redstone.packetlogger.config.ModConfig;
import dev.redstone.packetlogger.config.ModConfig.LogMode;
import dev.redstone.packetlogger.logger.PacketRegistry;
import dev.redstone.packetlogger.screen.widget.DualListSelectorWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class SimpleConfigScreen extends Screen {
    private final Screen parent;
    private final ModConfig config;
    
    // Widgets
    private ButtonWidget logPacketsButton;
    private ButtonWidget logModeButton;
    private DualListSelectorWidget s2cSelector;
    private DualListSelectorWidget c2sSelector;
    
    private boolean logPacketsEnabled;
    private LogMode currentLogMode;
    
    // Lazy-loaded packet lists to avoid early initialization
    private static List<String> s2cPackages = null;
    private static List<String> c2sPackages = null;
    
    private static List<String> getS2CPackages() {
        if (s2cPackages == null) {
            s2cPackages = PacketRegistry.getAllPacketNames().stream()
                .filter(name -> name.contains("S2CPacket"))
                .sorted()
                .toList();
        }
        return s2cPackages;
    }
    
    private static List<String> getC2SPackages() {
        if (c2sPackages == null) {
            c2sPackages = PacketRegistry.getAllPacketNames().stream()
                .filter(name -> name.contains("C2SPacket"))
                .sorted()
                .toList();
        }
        return c2sPackages;
    }

    public SimpleConfigScreen(Screen parent) {
        super(Text.literal("Packet Logger"));
        this.parent = parent;
        this.config = ModConfig.getInstance();
        this.logPacketsEnabled = config.logPackets;
        this.currentLogMode = config.logMode;
    }
    
    @Override
    protected void init() {
        super.init();
        
        int panelWidth = Math.min(500, this.width - 40);
        int panelX = (this.width - panelWidth) / 2;
        int panelY = 25;
        
        int buttonWidth = (panelWidth - 10) / 2;
        int y = panelY + 5;
        
        // Log Packages Toggle Button
        this.logPacketsButton = ButtonWidget.builder(
            Text.literal("Logging: " + (logPacketsEnabled ? "§aON" : "§cOFF")),
            button -> {
                logPacketsEnabled = !logPacketsEnabled;
                button.setMessage(Text.literal("Logging: " + (logPacketsEnabled ? "§aON" : "§cOFF")));
            })
            .dimensions(panelX, y, buttonWidth, 20)
            .build();
        this.addDrawableChild(logPacketsButton);
        
        // Log Mode Toggle Button
        this.logModeButton = ButtonWidget.builder(
            Text.literal("Output: " + currentLogMode.getDisplayName()),
            button -> {
                currentLogMode = currentLogMode.next();
                button.setMessage(Text.literal("Output: " + currentLogMode.getDisplayName()));
            })
            .dimensions(panelX + buttonWidth + 10, y, buttonWidth, 20)
            .build();
        this.addDrawableChild(logModeButton);
        
        y += 30;
        
        int selectorHeight = (this.height - y - 50) / 2 - 5;
        
        // S2C Selector
        this.s2cSelector = new DualListSelectorWidget(
            panelX, y, panelWidth, selectorHeight,
            "S2C Packets (Server → Client)",
            getS2CPackages(),
            new HashSet<>(config.selectedS2CPackets),
            selection -> {}
        );
        this.addDrawableChild(s2cSelector);
        
        y += selectorHeight + 10;
        
        // C2S Selector
        this.c2sSelector = new DualListSelectorWidget(
            panelX, y, panelWidth, selectorHeight,
            "C2S Packets (Client → Server)",
            getC2SPackages(),
            new HashSet<>(config.selectedC2SPackets),
            selection -> {}
        );
        this.addDrawableChild(c2sSelector);
        
        int bottomY = this.height - 28;
        int bottomButtonWidth = 100;
        
        this.addDrawableChild(
            ButtonWidget.builder(Text.literal("Save"), button -> this.saveAndClose())
                .dimensions(this.width / 2 - bottomButtonWidth - 5, bottomY, bottomButtonWidth, 20)
                .build()
        );
        
        this.addDrawableChild(
            ButtonWidget.builder(Text.literal("Cancel"), button -> this.close())
                .dimensions(this.width / 2 + 5, bottomY, bottomButtonWidth, 20)
                .build()
        );
    }
    
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, 0xA0101010, 0xB0101010);
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        
        int panelWidth = Math.min(500, this.width - 40);
        int panelX = (this.width - panelWidth) / 2;
        int panelY = 20;
        int panelHeight = this.height - 55;
        
        context.fill(panelX - 2, panelY - 2, panelX + panelWidth + 2, panelY + panelHeight + 2, 0xFF2A2A2A);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE0181818);
        
        super.render(context, mouseX, mouseY, delta);
        
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);
    }
    
    private void saveAndClose() {
        config.logPackets = logPacketsEnabled;
        config.logMode = currentLogMode;
        config.selectedS2CPackets = new ArrayList<>(s2cSelector.getSelectedPackages());
        config.selectedC2SPackets = new ArrayList<>(c2sSelector.getSelectedPackages());
        config.save();
        this.close();
    }
    
    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (s2cSelector != null && s2cSelector.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (c2sSelector != null && c2sSelector.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (s2cSelector != null && s2cSelector.charTyped(chr, modifiers)) {
            return true;
        }
        if (c2sSelector != null && c2sSelector.charTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }
}
