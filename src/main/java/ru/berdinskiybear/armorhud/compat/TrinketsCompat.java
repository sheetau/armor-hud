package ru.berdinskiybear.armorhud.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TrinketsCompat {
    private static final List<ItemStack> EMPTY_TRACKED_ITEMS = List.of(ItemStack.EMPTY, ItemStack.EMPTY);

    private static boolean initialized = false;
    private static boolean available = false;
    private static Method getTrinketComponentMethod;
    private static Method getInventoryMethod;

    private TrinketsCompat() {
    }

    public static List<ItemStack> getTrackedItems(PlayerEntity player) {
        if (!init()) {
            return EMPTY_TRACKED_ITEMS;
        }

        try {
            Optional<?> component = (Optional<?>) getTrinketComponentMethod.invoke(null, player);
            if (component.isEmpty()) {
                return EMPTY_TRACKED_ITEMS;
            }

            @SuppressWarnings("unchecked")
            Map<String, Map<String, ?>> inventory = (Map<String, Map<String, ?>>) getInventoryMethod.invoke(component.get());

            ItemStack charm = getSlotStack(inventory, "charm", "charm");
            ItemStack elytra = getSlotStack(inventory, "chest", "cape");
            return List.of(charm, elytra);
        } catch (ReflectiveOperationException | RuntimeException e) {
            available = false;
            return EMPTY_TRACKED_ITEMS;
        }
    }

    private static boolean init() {
        if (initialized) {
            return available;
        }

        initialized = true;
        available = FabricLoader.getInstance().isModLoaded("trinkets");
        if (!available) {
            return false;
        }

        try {
            Class<?> trinketsApiClass = Class.forName("dev.emi.trinkets.api.TrinketsApi");
            Class<?> trinketComponentClass = Class.forName("dev.emi.trinkets.api.TrinketComponent");

            getTrinketComponentMethod = trinketsApiClass.getMethod("getTrinketComponent", Class.forName("net.minecraft.entity.LivingEntity"));
            getInventoryMethod = trinketComponentClass.getMethod("getInventory");
        } catch (ReflectiveOperationException | LinkageError e) {
            available = false;
        }

        return available;
    }

    private static ItemStack getSlotStack(Map<String, Map<String, ?>> inventory, String group, String slot) {
        Map<String, ?> groupInventory = inventory.get(group);
        if (groupInventory == null) {
            return ItemStack.EMPTY;
        }

        Object slotInventory = groupInventory.get(slot);
        if (!(slotInventory instanceof Inventory inventoryView) || inventoryView.size() <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = inventoryView.getStack(0);
        return stack == null ? ItemStack.EMPTY : stack;
    }
}
