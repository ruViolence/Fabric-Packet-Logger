package dev.redstone.packetlogger.logger.unpacker;

import net.minecraft.component.ComponentChanges;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Formatiert ItemStacks mit allen Components/NBT-Daten im JSON-ähnlichen Format.
 */
public class ItemStackFormatter {
    
    /**
     * Formatiert einen ItemStack im Minecraft-NBT-Stil.
     * Format: {id:"minecraft:diamond_sword",count:1,components:{...}}
     */
    public static String format(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "{id:\"minecraft:air\",count:0}";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        
        // ID
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        sb.append("id:\"").append(itemId).append("\"");
        
        // Count
        sb.append(",count:").append(stack.getCount());
        
        // Components
        String components = formatComponents(stack);
        if (!components.isEmpty()) {
            sb.append(",components:{").append(components).append("}");
        }
        
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Formatiert einen ItemStack für Container-Slot-Format.
     * Format: {item:{id:"...",count:...},slot:X}
     */
    public static String formatForSlot(ItemStack stack, int slot) {
        if (stack == null || stack.isEmpty()) {
            return null; // Leere Slots werden ignoriert
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("{item:").append(format(stack));
        sb.append(",slot:").append(slot).append("}");
        return sb.toString();
    }
    
    private static String formatComponents(ItemStack stack) {
        List<String> parts = new ArrayList<>();
        ComponentChanges changes = stack.getComponentChanges();

        try {
            log: {
                Optional<? extends Text> opt = changes.get(DataComponentTypes.CUSTOM_NAME);
                if (opt == null) break log;
                
                Text name = opt.orElse(null);
                if (name != null) {
                    parts.add("\"minecraft:custom_name\":\"" + escapeString(name.getString()) + "\"");
                }
            }
            log: {
                Optional<? extends Text> opt = changes.get(DataComponentTypes.ITEM_NAME);
                if (opt == null) break log;
                
                Text name = opt.orElse(null);
                if (name != null) {
                    parts.add("\"minecraft:item_name\":\"" + escapeString(name.getString()) + "\"");
                }
            }
            log: {
                Optional<? extends Integer> opt = changes.get(DataComponentTypes.DAMAGE);
                if (opt == null) break log;
                
                Integer damage = opt.orElse(null);
                if (damage != null && damage > 0) {
                    parts.add("\"minecraft:damage\":" + damage);
                }
            }
            log: {
                Optional<? extends Integer> opt = changes.get(DataComponentTypes.MAX_DAMAGE);
                if (opt == null) break log;
                
                Integer maxDamage = opt.orElse(null);
                if (maxDamage != null) {
                    parts.add("\"minecraft:max_damage\":" + maxDamage);
                }
            }
            log: {
                Optional<? extends ItemEnchantmentsComponent> opt = changes.get(DataComponentTypes.ENCHANTMENTS);
                if (opt == null) break log;
                
                ItemEnchantmentsComponent enchants = opt.orElse(null);
                if (enchants != null && !enchants.isEmpty()) {
                    StringBuilder enchantSb = new StringBuilder("\"minecraft:enchantments\":{levels:{");
                    List<String> enchantList = new ArrayList<>();
                    for (RegistryEntry<Enchantment> entry : enchants.getEnchantments()) {
                        int level = enchants.getLevel(entry);
                        enchantList.add("\"" + entry.getIdAsString() + "\":" + level);
                    }
                    enchantSb.append(String.join(",", enchantList));
                    enchantSb.append("}}");
                    parts.add(enchantSb.toString());
                }
            }
            log: {
                Optional<? extends ItemEnchantmentsComponent> opt = changes.get(DataComponentTypes.STORED_ENCHANTMENTS);
                if (opt == null) break log;
                
                ItemEnchantmentsComponent enchants = opt.orElse(null);
                if (enchants != null && !enchants.isEmpty()) {
                    StringBuilder enchantSb = new StringBuilder("\"minecraft:stored_enchantments\":{levels:{");
                    List<String> enchantList = new ArrayList<>();
                    for (RegistryEntry<Enchantment> entry : enchants.getEnchantments()) {
                        int level = enchants.getLevel(entry);
                        enchantList.add("\"" + entry.getIdAsString() + "\":" + level);
                    }
                    enchantSb.append(String.join(",", enchantList));
                    enchantSb.append("}}");
                    parts.add(enchantSb.toString());
                }
            }
            log: {
                Optional<? extends LoreComponent> opt = changes.get(DataComponentTypes.LORE);
                if (opt == null) break log;
                
                LoreComponent lore = opt.orElse(null);
                if (lore != null && !lore.lines().isEmpty()) {
                    StringBuilder loreSb = new StringBuilder("\"minecraft:lore\":[");
                    List<String> loreLines = new ArrayList<>();
                    for (Text line : lore.lines()) {
                        loreLines.add("\"" + escapeString(line.getString()) + "\"");
                    }
                    loreSb.append(String.join(",", loreLines));
                    loreSb.append("]");
                    parts.add(loreSb.toString());
                }
            }
            log: {
                Optional<? extends UnbreakableComponent> opt = changes.get(DataComponentTypes.UNBREAKABLE);
                if (opt == null) break log;
                
                UnbreakableComponent unbreakable = opt.orElse(null);
                if (unbreakable != null) {
                    parts.add("\"minecraft:unbreakable\":{}");
                }
            }
            log: {
                Optional<? extends CustomModelDataComponent> opt = changes.get(DataComponentTypes.CUSTOM_MODEL_DATA);
                if (opt == null) break log;
                
                CustomModelDataComponent cmd = opt.orElse(null);
                if (cmd != null) {
                    parts.add("\"minecraft:custom_model_data\":" + cmd.toString());
                }
            }
            log: {
                Optional<? extends PotionContentsComponent> opt = changes.get(DataComponentTypes.POTION_CONTENTS);
                if (opt == null) break log;
                
                PotionContentsComponent potion = opt.orElse(null);
                if (potion != null) {
                    StringBuilder potionSb = new StringBuilder("\"minecraft:potion_contents\":{");
                    if (potion.potion().isPresent()) {
                        potionSb.append("potion:\"").append(potion.potion().get().getIdAsString()).append("\"");
                    }
                    potionSb.append("}");
                    parts.add(potionSb.toString());
                }
            }
            log: {
                Optional<? extends DyedColorComponent> opt = changes.get(DataComponentTypes.DYED_COLOR);
                if (opt == null) break log;
                
                DyedColorComponent color = opt.orElse(null);
                if (color != null) {
                    parts.add("\"minecraft:dyed_color\":{rgb:" + color.rgb() + "}");
                }
            }
            log: {
                Optional<? extends NbtComponent> opt = changes.get(DataComponentTypes.CUSTOM_DATA);
                if (opt == null) break log;
                
                NbtComponent customData = opt.orElse(null);
                if (customData != null) {
                    NbtCompound nbt = customData.copyNbt();
                    if (!nbt.isEmpty()) {
                        parts.add("\"minecraft:custom_data\":" + nbt.asString());
                    }
                }
            }
            log: {
                Optional<? extends AttributeModifiersComponent> opt = changes.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
                if (opt == null) break log;
                
                AttributeModifiersComponent attrs = opt.orElse(null);
                if (attrs != null && !attrs.modifiers().isEmpty()) {
                    StringBuilder attrSb = new StringBuilder("\"minecraft:attribute_modifiers\":{modifiers:[");
                    List<String> attrList = new ArrayList<>();
                    for (AttributeModifiersComponent.Entry entry : attrs.modifiers()) {
                        attrList.add("{type:\"" + entry.attribute().getIdAsString() + 
                                    "\",id:\"" + entry.modifier().id() + 
                                    "\",amount:" + entry.modifier().value() + 
                                    ",operation:\"" + entry.modifier().operation().name() + "\"}");
                    }
                    attrSb.append(String.join(",", attrList));
                    attrSb.append("]}");
                    parts.add(attrSb.toString());
                }
            }
            log: {
                Optional<? extends ContainerComponent> opt = changes.get(DataComponentTypes.CONTAINER);
                if (opt == null) break log;
                
                ContainerComponent container = opt.orElse(null);
                if (container != null) {
                    StringBuilder contSb = new StringBuilder("\"minecraft:container\":[");
                    List<String> items = new ArrayList<>();
                    int slot = 0;
                    for (ItemStack item : container.iterateNonEmpty()) {
                        items.add("{slot:" + slot + ",item:" + format(item) + "}");
                        slot++;
                    }
                    contSb.append(String.join(",", items));
                    contSb.append("]");
                    parts.add(contSb.toString());
                }
            }
            log: {
                Optional<? extends BundleContentsComponent> opt = changes.get(DataComponentTypes.BUNDLE_CONTENTS);
                if (opt == null) break log;
                
                BundleContentsComponent bundle = opt.orElse(null);
                if (bundle != null && !bundle.isEmpty()) {
                    StringBuilder bundleSb = new StringBuilder("\"minecraft:bundle_contents\":[");
                    List<String> items = new ArrayList<>();
                    for (ItemStack item : bundle.iterate()) {
                        items.add(format(item));
                    }
                    bundleSb.append(String.join(",", items));
                    bundleSb.append("]");
                    parts.add(bundleSb.toString());
                }
            }
            log: {
                Optional<? extends WrittenBookContentComponent> opt = changes.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
                if (opt == null) break log;
                
                WrittenBookContentComponent book = opt.orElse(null);
                if (book != null) {
                    StringBuilder bookSb = new StringBuilder("\"minecraft:written_book_content\":{");
                    bookSb.append("title:\"").append(escapeString(book.title().raw())).append("\"");
                    bookSb.append(",author:\"").append(escapeString(book.author())).append("\"");
                    bookSb.append(",generation:").append(book.generation());
                    bookSb.append("}");
                    parts.add(bookSb.toString());
                }
            }
            log: {
                Optional<? extends WritableBookContentComponent> opt = changes.get(DataComponentTypes.WRITABLE_BOOK_CONTENT);
                if (opt == null) break log;
                
                WritableBookContentComponent book = opt.orElse(null);
                if (book != null && !book.pages().isEmpty()) {
                    StringBuilder bookSb = new StringBuilder("\"minecraft:writable_book_content\":{pages:[");
                    List<String> pages = new ArrayList<>();
                    for (var page : book.pages()) {
                        pages.add("\"" + escapeString(page.raw()) + "\"");
                    }
                    bookSb.append(String.join(",", pages));
                    bookSb.append("]}");
                    parts.add(bookSb.toString());
                }
            }
        } catch (Exception e) {
            parts.add("\"error\":\"" + escapeString(e.getMessage()) + "\"");
        }
        
        return String.join(",", parts);
    }
    
    private static String escapeString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
