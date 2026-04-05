package ru.berdinskiybear.armorhud.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.option.AttackIndicator;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.berdinskiybear.armorhud.ArmorHudMod;
import ru.berdinskiybear.armorhud.compat.TrinketsCompat;
import ru.berdinskiybear.armorhud.config.ArmorHudConfig;

import java.util.ArrayList;
import java.util.List;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Shadow
    @Final
    private MinecraftClient client;
    @Shadow
    @Final
    private Random random;

    @Unique
    private static final int STEP = 20;
    @Unique
    private static final int WIDTH = 22;
    @Unique
    private static final int HEIGHT = 22;
    @Unique
    private static final int HOTBAR_OFFSET = 98;
    @Unique
    private static final int OFFHAND_OFFSET = 29;
    @Unique
    private static final int ATTACK_INDICATOR_OFFSET = 23;
    @Unique
    private static final int WARNING_OFFSET = 7;

    @Unique
    private static final Identifier WARNING_TEXTURE = Identifier.of("sheetas-armor-hud", "warn.png");
    @Unique
    private static final Identifier CHARM_SLOT_TEXTURE = Identifier.of("sheetas-armor-hud", "item/empty_charm_slot");
    @Unique
    private static final Identifier CAPE_SLOT_TEXTURE = Identifier.of("sheetas-armor-hud", "item/empty_cape_slot");

    @Unique
    private List<ItemStack> armorItems = new ArrayList<>();
    @Unique
    private List<ItemStack> supportItems = List.of(ItemStack.EMPTY, ItemStack.EMPTY);
    @Unique
    private int shift = 0;

    @Shadow
    protected abstract PlayerEntity getCameraPlayer();

    @Shadow
    protected abstract void renderHotbarItem(DrawContext context, int x, int y, RenderTickCounter tickCounter, PlayerEntity player, ItemStack stack, int seed);

    @Inject(method = "renderHotbar", at = @At("TAIL"))
    public void renderArmorHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        this.client.getProfiler().push("sheetas-armor-hud");

        // this was extracted to a different method to be able to return whenever I want
        // without messing up the profiler
        drawArmorHud(context, tickCounter);

        // pop this out of profiler
        this.client.getProfiler().pop();
    }

    @Unique
    private void drawArmorHud(DrawContext context, RenderTickCounter tickCounter) {
        ArmorHudConfig config = ArmorHudMod.getManager().getConfig();
        if (!config.isEnabled()) return;

        PlayerEntity player = ArmorHudMod.getCameraPlayer();
        if (player == null) return;

        // fetch armor items
        this.armorItems = new ArrayList<>(player.getInventory().armor);
        this.supportItems = new ArrayList<>(TrinketsCompat.getTrackedItems(player));

        int armorNonEmptyAmount = getNonEmptyAmount(this.armorItems);
        int supportNonEmptyAmount = getNonEmptyAmount(this.supportItems);

        // return if there is nothing to draw
        if (!shouldRenderWidget(armorNonEmptyAmount, config) && !shouldRenderWidget(supportNonEmptyAmount, config)) return;

        if (config.isReversed())  {
            armorItems = armorItems.reversed();
            supportItems = supportItems.reversed();
        }

        // push them matrices :3
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 200);

        // hotbar offset is relative to the bar, so when we are on the left it needs to be flipped
        // and on the right side, we need to flip the offset, except when anchored to the hotbar
        final int sideMultiplier, sideOffsetMultiplier;
        if ((config.getAnchor() == ArmorHudConfig.Anchor.HOTBAR && config.getSide() == ArmorHudConfig.Side.LEFT)
                || (config.getAnchor() != ArmorHudConfig.Anchor.HOTBAR && config.getSide() == ArmorHudConfig.Side.RIGHT)) {
            sideMultiplier = -1;
            sideOffsetMultiplier = -1;
        } else {
            sideMultiplier = 1;
            sideOffsetMultiplier = 0;
        }

        final int verticalMultiplier = switch (config.getAnchor()) {
            case TOP, TOP_CENTER -> 1;
            case BOTTOM, HOTBAR -> -1;
        };

        final int verticalOffsetMultiplier = switch (config.getAnchor()) {
            case TOP, TOP_CENTER -> 0;
            case BOTTOM, HOTBAR -> -1;
        };

        final int armorSlots = getShownSlots(armorNonEmptyAmount, this.armorItems.size(), config);
        final int armorWidgetWidth = getWidgetWidth(armorSlots);
        final int armorAddedHotbarOffset = getHotbarOffsetForSide(config, player, config.getSide());
        final int armorWidgetX = getWidgetX(context, config, config.getSide(), armorWidgetWidth, armorAddedHotbarOffset);
        final int armorWidgetY = config.getOffsetY() * verticalMultiplier + switch (config.getAnchor()) {
            case BOTTOM, HOTBAR -> context.getScaledWindowHeight() - HEIGHT;
            case TOP, TOP_CENTER -> 0;
        };
        ArmorHudConfig.Side supportSide = getSupportSide(config);
        final int supportSlots = getShownSlots(supportNonEmptyAmount, this.supportItems.size(), config);
        final int supportWidgetWidth = getWidgetWidth(supportSlots);
        final int supportAddedHotbarOffset = getHotbarOffsetForSide(config, player, supportSide);
        final int supportWidgetX = getSupportWidgetX(context, config, supportSide, armorWidgetX, armorWidgetWidth, supportWidgetWidth, supportAddedHotbarOffset);

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (shouldRenderWidget(armorNonEmptyAmount, config)) {
            renderWidgetBackground(context, config, armorWidgetX, armorWidgetY, armorSlots, armorWidgetWidth);
        }

        if (shouldRenderWidget(supportNonEmptyAmount, config)) {
            renderWidgetBackground(context, config, supportWidgetX, armorWidgetY, supportSlots, supportWidgetWidth);
        }

        if (shouldRenderWidget(armorNonEmptyAmount, config)) {
            renderWidgetWarnings(context, config, this.armorItems, armorWidgetX, armorWidgetY, verticalOffsetMultiplier);
            renderArmorIcons(context, config, armorWidgetX, armorWidgetY);
            renderWidgetItems(context, tickCounter, player, this.armorItems, armorWidgetX, armorWidgetY, config);
        }

        if (shouldRenderWidget(supportNonEmptyAmount, config)) {
            renderWidgetWarnings(context, config, this.supportItems, supportWidgetX, armorWidgetY, verticalOffsetMultiplier);
            renderSupportIcons(context, config, supportWidgetX, armorWidgetY);
            renderWidgetItems(context, tickCounter, player, this.supportItems, supportWidgetX, armorWidgetY, config);
        }

        // remove my translations
        context.getMatrices().pop();
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;", shift = At.Shift.BY, by = 2))
    public void calculateStatusEffectIconsOffset(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ArmorHudConfig config = ArmorHudMod.getManager().getConfig();
        if (!config.isEnabled() || !config.isPushStatusEffectIcons() || config.getAnchor() != ArmorHudConfig.Anchor.TOP) return;

        PlayerEntity player = this.getCameraPlayer();
        if (player == null) return;

        List<ItemStack> currentArmorItems = player.getInventory().armor;
        List<ItemStack> currentSupportItems = TrinketsCompat.getTrackedItems(player);
        int armorAmount = getNonEmptyAmount(currentArmorItems);
        int supportAmount = getNonEmptyAmount(currentSupportItems);

        boolean armorOnRight = config.getSide() == ArmorHudConfig.Side.RIGHT && shouldRenderWidget(armorAmount, config);
        boolean supportOnRight = getSupportSide(config) == ArmorHudConfig.Side.RIGHT && shouldRenderWidget(supportAmount, config);
        if (!armorOnRight && !supportOnRight) return;

        int newShift = 22 + config.getOffsetY();
        if (config.isWarningShown() && (currentArmorItems.stream().anyMatch(ArmorHudMod::shouldShowWarning)
                || currentSupportItems.stream().anyMatch(ArmorHudMod::shouldShowWarning))) {
            newShift += 10;
            if (config.getWarningBobIntensity() != 0) {
                newShift += 7;
            }
        }

        this.shift = Math.max(newShift, 0);
    }

    @ModifyVariable(method = "renderStatusEffectOverlay", at = @At(value = "STORE"), ordinal = 3)
    public int statusEffectIconsOffset(int y) {
        return y + this.shift;
    }

    @Unique
    private int getNonEmptyAmount(List<ItemStack> items) {
        return (int) items.stream().filter(s -> !s.isEmpty()).count();
    }

    @Unique
    private boolean shouldRenderWidget(int nonEmptyAmount, ArmorHudConfig config) {
        return nonEmptyAmount > 0 || config.getWidgetShown() == ArmorHudConfig.WidgetShown.ALWAYS;
    }

    @Unique
    private int getShownSlots(int nonEmptyAmount, int maxSlots, ArmorHudConfig config) {
        return config.getWidgetShown() == ArmorHudConfig.WidgetShown.NOT_EMPTY ? nonEmptyAmount : maxSlots;
    }

    @Unique
    private int getWidgetWidth(int slots) {
        return WIDTH + ((slots - 1) * STEP);
    }

    @Unique
    private int getHotbarOffsetForSide(ArmorHudConfig config, PlayerEntity player, ArmorHudConfig.Side side) {
        if (config.getAnchor() != ArmorHudConfig.Anchor.HOTBAR || side != ArmorHudConfig.Side.LEFT) {
            return 0;
        }

        return switch (config.getOffhandSlotBehavior()) {
            case ALWAYS_IGNORE -> 0;
            case ALWAYS_LEAVE_SPACE -> Math.max(OFFHAND_OFFSET, ATTACK_INDICATOR_OFFSET);
            case ADHERE -> {
                if (!player.getOffHandStack().isEmpty()) {
                    yield OFFHAND_OFFSET;
                } else if (this.client.options.getAttackIndicator().getValue() == AttackIndicator.HOTBAR) {
                    yield ATTACK_INDICATOR_OFFSET;
                }

                yield 0;
            }
        };
    }

    @Unique
    private ArmorHudConfig.Side getSupportSide(ArmorHudConfig config) {
        if (config.getAnchor() == ArmorHudConfig.Anchor.TOP_CENTER) {
            return config.getSide();
        }
        return config.getSide() == ArmorHudConfig.Side.LEFT ? ArmorHudConfig.Side.RIGHT : ArmorHudConfig.Side.LEFT;
    }

    @Unique
    private int getWidgetX(DrawContext context, ArmorHudConfig config, ArmorHudConfig.Side side, int widgetWidth, int addedHotbarOffset) {
        final int sideMultiplier;
        final int sideOffsetMultiplier;
        if ((config.getAnchor() == ArmorHudConfig.Anchor.HOTBAR && side == ArmorHudConfig.Side.LEFT)
                || (config.getAnchor() != ArmorHudConfig.Anchor.HOTBAR && side == ArmorHudConfig.Side.RIGHT)) {
            sideMultiplier = -1;
            sideOffsetMultiplier = -1;
        } else {
            sideMultiplier = 1;
            sideOffsetMultiplier = 0;
        }

        return config.getOffsetX() * sideMultiplier + switch (config.getAnchor()) {
            case TOP_CENTER -> context.getScaledWindowWidth() / 2 - (widgetWidth / 2);
            case TOP, BOTTOM -> (widgetWidth - context.getScaledWindowWidth()) * sideOffsetMultiplier;
            case HOTBAR ->
                    context.getScaledWindowWidth() / 2 + ((HOTBAR_OFFSET + addedHotbarOffset) * sideMultiplier) + (widgetWidth * sideOffsetMultiplier);
        };
    }

    @Unique
    private int getSupportWidgetX(DrawContext context, ArmorHudConfig config, ArmorHudConfig.Side side, int armorWidgetX, int armorWidgetWidth, int widgetWidth, int addedHotbarOffset) {
        if (config.getAnchor() == ArmorHudConfig.Anchor.TOP_CENTER) {
            return config.getSide() == ArmorHudConfig.Side.LEFT
                    ? armorWidgetX + armorWidgetWidth + STEP
                    : armorWidgetX - widgetWidth - STEP;
        }

        return getWidgetX(context, config, side, widgetWidth, addedHotbarOffset);
    }

    @Unique
    private void renderWidgetBackground(DrawContext context, ArmorHudConfig config, int widgetX, int widgetY, int slots, int widgetWidth) {
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, -91);
        switch (config.getStyle()) {
            case HOTBAR -> {
                context.drawGuiTexture(InGameHud.HOTBAR_TEXTURE, 182, 22, 0, 0, widgetX, widgetY, widgetWidth - 3, HEIGHT);
                context.drawGuiTexture(InGameHud.HOTBAR_TEXTURE, 182, 22, 182 - 3, 0, widgetX + widgetWidth - 3, widgetY, 3, HEIGHT);
            }
            case ROUNDED_CORNERS -> {
                context.drawGuiTexture(InGameHud.HOTBAR_OFFHAND_LEFT_TEXTURE, 29, 24, 0, 1, widgetX, widgetY, 3, HEIGHT);
                context.drawGuiTexture(InGameHud.HOTBAR_TEXTURE, 182, 22, 3, 0, widgetX + 3, widgetY, widgetWidth - 6, HEIGHT);
                context.drawGuiTexture(InGameHud.HOTBAR_OFFHAND_LEFT_TEXTURE, 29, 24, WIDTH - 3, 1, widgetX + widgetWidth - 3, widgetY, 3, HEIGHT);
            }
            case ROUNDED -> {
                int borderWidth = (WIDTH - STEP) / 2;
                context.drawGuiTexture(InGameHud.HOTBAR_OFFHAND_LEFT_TEXTURE, 29, 24, 0, 1, widgetX, widgetY, borderWidth, HEIGHT);
                for (int i = 0; i < slots; i++) {
                    context.drawGuiTexture(InGameHud.HOTBAR_OFFHAND_LEFT_TEXTURE, 29, 24, borderWidth, 1, widgetX + borderWidth + i * STEP, widgetY, STEP, HEIGHT);
                }
                context.drawGuiTexture(InGameHud.HOTBAR_OFFHAND_LEFT_TEXTURE, 29, 24, 0, 1, widgetX + widgetWidth - borderWidth, widgetY, borderWidth, HEIGHT);
            }
        }
        context.getMatrices().pop();
    }

    @Unique
    private void renderWidgetWarnings(DrawContext context, ArmorHudConfig config, List<ItemStack> items, int widgetX, int widgetY, int verticalOffsetMultiplier) {
        if (!config.isWarningShown()) return;

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 90);

        int i = 0;
        for (ItemStack stack : items) {
            if (ArmorHudMod.shouldShowWarning(stack)) {
                int x = widgetX + (STEP * i) + WARNING_OFFSET;
                int y = widgetY + (HEIGHT * (verticalOffsetMultiplier + 1)) + (8 * verticalOffsetMultiplier);

                if (config.getWarningBobIntensity() != 0) {
                    int intensity = config.getWarningBobIntensity();
                    y += (int) (this.random.nextInt(intensity) - Math.ceil(intensity / 2F));
                }

                context.drawTexture(WARNING_TEXTURE, x, y, 0, 0, 0, 8, 8, 8, 8);
                i++;
            } else if (config.getWidgetShown() != ArmorHudConfig.WidgetShown.NOT_EMPTY || !stack.isEmpty()) {
                i++;
            }
        }

        context.getMatrices().pop();
    }

    @Unique
    private void renderArmorIcons(DrawContext context, ArmorHudConfig config, int widgetX, int widgetY) {
        if (!config.isIconsShown() || config.getWidgetShown() == ArmorHudConfig.WidgetShown.NOT_EMPTY) return;

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, -90);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_COLOR, GlStateManager.DstFactor.ONE, GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);

        for (int i = 0; i < this.armorItems.size(); i++) {
            if (this.armorItems.get(i).isEmpty()) {
                int slotIndex = config.isReversed() ? i : 3 - i;
                Identifier spriteId = PlayerScreenHandler.EMPTY_ARMOR_SLOT_TEXTURES.get(PlayerScreenHandler.EQUIPMENT_SLOT_ORDER[slotIndex]);
                Sprite sprite = this.client.getSpriteAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE).apply(spriteId);

                context.drawSprite(widgetX + (STEP * i) + 3, widgetY + 3, 0, 16, 16, sprite);
            }
        }

        RenderSystem.defaultBlendFunc();
        context.getMatrices().pop();
    }

    @Unique
    private void renderSupportIcons(DrawContext context, ArmorHudConfig config, int widgetX, int widgetY) {
        if (!config.isIconsShown() || config.getWidgetShown() == ArmorHudConfig.WidgetShown.NOT_EMPTY) return;

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, -90);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_COLOR, GlStateManager.DstFactor.ONE, GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);

        for (int i = 0; i < this.supportItems.size(); i++) {
            if (this.supportItems.get(i).isEmpty()) {
                Identifier spriteId = getSupportSlotTexture(i, config);
                Sprite sprite = this.client.getSpriteAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE).apply(spriteId);
                context.drawSprite(widgetX + (STEP * i) + 3, widgetY + 3, 0, 16, 16, sprite);
            }
        }

        RenderSystem.defaultBlendFunc();
        context.getMatrices().pop();
    }

    @Unique
    private Identifier getSupportSlotTexture(int index, ArmorHudConfig config) {
        boolean charmFirst = !config.isReversed();
        boolean isCharmSlot = charmFirst ? index == 0 : index == this.supportItems.size() - 1;
        return isCharmSlot ? CHARM_SLOT_TEXTURE : CAPE_SLOT_TEXTURE;
    }

    @Unique
    private void renderWidgetItems(DrawContext context, RenderTickCounter tickCounter, PlayerEntity player, List<ItemStack> items, int widgetX, int widgetY, ArmorHudConfig config) {
        int i = 0;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                this.renderHotbarItem(context, widgetX + (STEP * i) + 3, widgetY + 3, tickCounter, player, stack, i + 1);
            }

            if (!stack.isEmpty() || config.getWidgetShown() != ArmorHudConfig.WidgetShown.NOT_EMPTY) {
                i++;
            }
        }
    }
}
