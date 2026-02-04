package dev.redstone.packetlogger.mixin.client;

import dev.redstone.packetlogger.PacketLoggerClient;
import dev.redstone.packetlogger.screen.SimpleConfigScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiplayerScreen.class)
public class MultiplayerScreenMixin {
    
    @Inject(method = "keyPressed(III)Z", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        KeyBinding configKey = PacketLoggerClient.getConfigKeyBinding();
        if (configKey != null && configKey.matchesKey(keyCode, scanCode)) {
            MultiplayerScreen screen = (MultiplayerScreen)(Object)this;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                client.setScreen(new SimpleConfigScreen(screen));
                cir.setReturnValue(true);
            }
        }
    }
}
