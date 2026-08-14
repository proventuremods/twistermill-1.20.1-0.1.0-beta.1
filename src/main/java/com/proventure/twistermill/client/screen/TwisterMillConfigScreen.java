package com.proventure.twistermill.client.screen;

import com.electronwill.nightconfig.core.AbstractConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.proventure.twistermill.TwisterMill;
import com.proventure.twistermill.config.AdvancementRewardManager;
import com.proventure.twistermill.config.AdvancementRewardManager.ValidationError;
import com.proventure.twistermill.config.AdvancementRewardManager.ValidationResult;
import com.proventure.twistermill.config.TwisterMillConfig;
import net.createmod.catnip.config.ui.ConfigHelper;
import net.createmod.catnip.config.ui.ConfigScreen;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.config.ui.SubMenuConfigScreen;
import net.createmod.catnip.config.ui.entries.BooleanEntry;
import net.createmod.catnip.config.ui.entries.NumberEntry;
import net.createmod.catnip.config.ui.entries.SubMenuEntry;
import net.createmod.catnip.gui.ConfirmationScreen.Response;
import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class TwisterMillConfigScreen extends SubMenuConfigScreen {

    private static final String ROOT_KEY = "twistermill";
    private static final String DROPS_KEY = "drops";
    private static final String SAILS_KEY = "sails";
    private static final String KEY_PREFIX = "twistermill.configgui.advancementDrop.";
    private static final int ITEM_FIELD_LEFT_INSET = 10;
    private static final int ITEM_FIELD_RIGHT_INSET = 8;

    private TwisterMillConfigScreen(Screen parent, UnmodifiableConfig rootConfig) {
        super(parent, "Twistermill", ModConfig.Type.COMMON, TwisterMillConfig.COMMON_SPEC, rootConfig);
        ConfigScreen.modID = TwisterMill.MOD_ID;
    }

    public static Screen create(Screen parent) {
        Object root = TwisterMillConfig.COMMON_SPEC.getValues().get(ROOT_KEY);
        if (!(root instanceof UnmodifiableConfig rootConfig)) {
            throw new IllegalStateException("Missing TwisterMill root config group");
        }
        return new TwisterMillConfigScreen(parent, rootConfig);
    }

    @Override
    protected void init() {
        super.init();

        list.children().removeIf(SubMenuEntry.class::isInstance);
        List<? extends UnmodifiableConfig.Entry> categories = configGroup.entrySet().stream()
                .filter(entry -> entry.getRawValue() instanceof AbstractConfig)
                .sorted(Comparator.comparing(entry -> toHumanReadable(entry.getKey())))
                .toList();
        for (UnmodifiableConfig.Entry category : categories) {
            String key = category.getKey();
            String label = toHumanReadable(key);
            list.children().add(new RootCategoryEntry(
                    this,
                    label,
                    key,
                    spec,
                    (UnmodifiableConfig) category.getRawValue()
            ));
        }

        if (goBack != null) {
            goBack.withCallback(this::attemptRootBackstep);
        }
    }

    @Override
    protected void resetConfig(@NotNull UnmodifiableConfig values) {
        super.resetConfig(values);
        stageValidatedDefaults();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && ConfigScreenList.currentText == null) {
            attemptRootBackstep();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void attemptRootBackstep() {
        if (ConfigHelper.changes.isEmpty()) {
            ScreenOpener.open(parent);
            return;
        }

        showLeavingPrompt(response -> {
            if (response == Response.Cancel) {
                return;
            }
            if (response == Response.Confirm) {
                saveChanges();
            }
            ConfigHelper.changes.clear();
            ScreenOpener.open(parent);
        });
    }

    private static void stageValidatedDefaults() {
        ValidationResult result = AdvancementRewardManager.validate(
                "minecraft:ancient_debris",
                "6",
                BuiltInRegistries.ITEM
        );
        if (!result.isValid()) {
            return;
        }
        ConfigHelper.setValue(
                configPath(TwisterMillConfig.ADVANCEMENT_DROP_ITEM),
                TwisterMillConfig.ADVANCEMENT_DROP_ITEM,
                result.itemId().toString(),
                Map.of()
        );
        ConfigHelper.setValue(
                configPath(TwisterMillConfig.ADVANCEMENT_DROP_COUNT),
                TwisterMillConfig.ADVANCEMENT_DROP_COUNT,
                result.count(),
                Map.of()
        );
    }

    private static String configPath(ModConfigSpec.ConfigValue<?> value) {
        return String.join(".", value.getPath());
    }

    private static final class RootCategoryEntry extends SubMenuEntry {
        private RootCategoryEntry(
                TwisterMillConfigScreen parent,
                String label,
                String path,
                ModConfigSpec spec,
                UnmodifiableConfig config
        ) {
            super(parent, label, spec, config);
            this.path = path;
            if (DROPS_KEY.equals(path)) {
                button.withCallback(() -> ScreenOpener.open(
                        new RewardConfigScreen(parent, label, spec, config)
                ));
            } else if (SAILS_KEY.equals(path)) {
                button.withCallback(() -> ScreenOpener.open(
                        new SailsConfigScreen(parent, label, spec, config)
                ));
            }
        }
    }

    private static final class SailsConfigScreen extends SubMenuConfigScreen {
        private SailsConfigScreen(
                Screen parent,
                String title,
                ModConfigSpec spec,
                UnmodifiableConfig configGroup
        ) {
            super(parent, title, ModConfig.Type.COMMON, spec, configGroup);
        }

        @Override
        protected void init() {
            super.init();
            list.children().clear();

            addSailValue("sail_placement_assist_range");
            addSailValue("enable_sail_wind_force");
            addSailValue("enable_sail_wind_force_vectors");
            addSailValue("enable_sail_wind_diagnostics");
            addSailValue("smooth_sail_force_updates");
            addSailValue("sail_force_smoothing_strength");
            addSailValue("peak_efficiency_rotor_blades");
            addSailValue("sail_peak_efficiency_pitch_degrees");
            addSailValue("sail_wind_diagnostic_interval_ticks");
            addSailValue("sail_wind_force_coefficient");
            addSailValue("sail_wind_min_exposure");
            addSailValue("sail_wind_max_force_per_block");
            addSailValue("sail_wind_max_force_per_object");
        }

        @SuppressWarnings("unchecked")
        private void addSailValue(String key) {
            Object rawValue = configGroup.getRaw(key);
            if (!(rawValue instanceof ModConfigSpec.ConfigValue<?> configValue)) {
                throw new IllegalStateException("Missing Sail config value: " + key);
            }

            ModConfigSpec.ValueSpec valueSpec = spec.getSpec().getRaw(configValue.getPath());
            Object currentValue = configValue.get();
            String label = toHumanReadable(key);
            if (currentValue instanceof Boolean) {
                list.children().add(new BooleanEntry(
                        label,
                        (ModConfigSpec.ConfigValue<Boolean>) configValue,
                        valueSpec
                ));
            } else if (currentValue instanceof Number) {
                list.children().add(NumberEntry.create(currentValue, label, configValue, valueSpec));
            } else {
                throw new IllegalStateException("Unsupported Sail config value: " + key);
            }
        }
    }

    private static final class RewardConfigScreen extends SubMenuConfigScreen {
        private RewardPairController rewardPair;

        private RewardConfigScreen(
                Screen parent,
                String title,
                ModConfigSpec spec,
                UnmodifiableConfig configGroup
        ) {
            super(parent, title, ModConfig.Type.COMMON, spec, configGroup);
        }

        @Override
        protected void init() {
            super.init();
            list.children().clear();

            ModConfigSpec.ValueSpec toggleSpec = spec.getSpec().getRaw(
                    TwisterMillConfig.ENABLE_NETHERITE_ADVANCEMENT_DROP.getPath()
            );
            list.children().add(new BooleanEntry(
                    Component.translatable(KEY_PREFIX + "enabled").getString(),
                    TwisterMillConfig.ENABLE_NETHERITE_ADVANCEMENT_DROP,
                    toggleSpec
            ));

            rewardPair = new RewardPairController();
            list.children().add(new ItemLabelEntry());
            list.children().add(new ItemDraftEntry(rewardPair));
            list.children().add(new CountAndCheckEntry(rewardPair));
        }

        @Override
        protected void clearChanges() {
            super.clearChanges();
            if (rewardPair != null) {
                rewardPair.reloadAcceptedValues();
            }
        }

        @Override
        protected void resetConfig(@NotNull UnmodifiableConfig values) {
            super.resetConfig(values);
            stageValidatedDefaults();
            if (rewardPair != null) {
                rewardPair.reloadAcceptedValues();
            }
        }
    }

    private enum DraftState {
        UNTOUCHED,
        EDITED,
        CLEARED
    }

    private static final class RewardPairController {
        private String acceptedItem;
        private String acceptedCount;
        private DraftEditBox itemField;
        private DraftEditBox countField;
        private Component status = CommonComponents.EMPTY;
        private int statusColor;
        private int statusTicks;

        private RewardPairController() {
            reloadAcceptedValues();
        }

        private void attachItemField(DraftEditBox field) {
            itemField = field;
        }

        private void attachCountField(DraftEditBox field) {
            countField = field;
        }

        private void check() {
            String effectiveItem = itemField.effectiveValue(acceptedItem);
            String effectiveCount = countField.effectiveValue(acceptedCount);
            ValidationResult result = AdvancementRewardManager.validate(
                    effectiveItem,
                    effectiveCount,
                    BuiltInRegistries.ITEM
            );
            if (!result.isValid()) {
                showStatus(Component.translatable(errorKey(result.error())), 0xFFFF5555);
                return;
            }

            String canonicalItem = result.itemId().toString();
            String canonicalCount = Integer.toString(result.count());
            ConfigHelper.setValue(
                    configPath(TwisterMillConfig.ADVANCEMENT_DROP_ITEM),
                    TwisterMillConfig.ADVANCEMENT_DROP_ITEM,
                    canonicalItem,
                    Map.of()
            );
            ConfigHelper.setValue(
                    configPath(TwisterMillConfig.ADVANCEMENT_DROP_COUNT),
                    TwisterMillConfig.ADVANCEMENT_DROP_COUNT,
                    result.count(),
                    Map.of()
            );

            acceptedItem = canonicalItem;
            acceptedCount = canonicalCount;
            itemField.acceptCheckedValue();
            countField.acceptCheckedValue();
            showStatus(Component.translatable(KEY_PREFIX + "valid"), 0xFF55FF55);
        }

        private void reloadAcceptedValues() {
            acceptedItem = ConfigHelper.getValue(
                    configPath(TwisterMillConfig.ADVANCEMENT_DROP_ITEM),
                    TwisterMillConfig.ADVANCEMENT_DROP_ITEM
            );
            acceptedCount = Integer.toString(ConfigHelper.getValue(
                    configPath(TwisterMillConfig.ADVANCEMENT_DROP_COUNT),
                    TwisterMillConfig.ADVANCEMENT_DROP_COUNT
            ));
            if (itemField != null) {
                itemField.acceptCheckedValue();
            }
            if (countField != null) {
                countField.acceptCheckedValue();
            }
            status = CommonComponents.EMPTY;
            statusTicks = 0;
        }

        private String acceptedItem() {
            return acceptedItem;
        }

        private String acceptedCount() {
            return acceptedCount;
        }

        private void tickStatus() {
            if (statusTicks > 0) {
                statusTicks--;
            }
        }

        private void renderStatus(GuiGraphics graphics, Font font, int x, int y) {
            if (statusTicks > 0 && status != CommonComponents.EMPTY) {
                graphics.drawString(font, status, x, y, statusColor);
            }
        }

        private void showStatus(Component message, int color) {
            status = message;
            statusColor = color;
            statusTicks = 100;
        }

        private static String errorKey(ValidationError error) {
            return switch (error) {
                case COUNT_MISSING -> KEY_PREFIX + "error.countMissing";
                case COUNT_NOT_INTEGER -> KEY_PREFIX + "error.countNotInteger";
                case COUNT_OUT_OF_RANGE -> KEY_PREFIX + "error.countOutOfRange";
                case ITEM_MISSING -> KEY_PREFIX + "error.itemMissing";
                case ITEM_INVALID_FORMAT -> KEY_PREFIX + "error.itemInvalidFormat";
                case ITEM_NOT_REGISTERED -> KEY_PREFIX + "error.itemNotRegistered";
                case ITEM_AIR -> KEY_PREFIX + "error.itemAir";
            };
        }
    }

    private static final class DraftEditBox extends EditBox {
        private DraftState state = DraftState.UNTOUCHED;

        private DraftEditBox(Font font) {
            super(font, 0, 0, 100, 20, CommonComponents.EMPTY);
            setResponder(value -> state = value.isEmpty() ? DraftState.CLEARED : DraftState.EDITED);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            super.onClick(mouseX, mouseY);
            setFocused(true);
        }

        @Override
        public void setFocused(boolean focused) {
            super.setFocused(focused);

            if (!focused) {
                if (ConfigScreenList.currentText == this) {
                    ConfigScreenList.currentText = null;
                }
                return;
            }

            EditBox previous = ConfigScreenList.currentText;
            if (previous != null && previous != this) {
                previous.setFocused(false);
            }
            ConfigScreenList.currentText = this;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            boolean clearKey = keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE;
            boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
            if (clearKey && isFocused() && getValue().isEmpty()) {
                state = DraftState.CLEARED;
            }
            return handled;
        }

        private String effectiveValue(String acceptedValue) {
            if (isFocused() && getValue().isEmpty()) {
                state = DraftState.CLEARED;
            }
            return state == DraftState.UNTOUCHED ? acceptedValue : getValue();
        }

        private void acceptCheckedValue() {
            setValue("");
            state = DraftState.UNTOUCHED;
            setFocused(false);
        }

        private boolean shouldRenderHint() {
            return state == DraftState.UNTOUCHED && getValue().isEmpty() && !isFocused();
        }
    }

    private static final class ItemLabelEntry extends ConfigScreenList.LabeledEntry {
        private ItemLabelEntry() {
            super(
                    Component.translatable(KEY_PREFIX + "item").getString(),
                    configPath(TwisterMillConfig.ADVANCEMENT_DROP_ITEM)
            );
        }
    }

    private static final class ItemDraftEntry extends ConfigScreenList.Entry {
        private final RewardPairController controller;
        private final DraftEditBox field;

        private ItemDraftEntry(RewardPairController controller) {
            this.controller = controller;
            field = new DraftEditBox(Minecraft.getInstance().font);
            field.setMaxLength(256);
            controller.attachItemField(field);
            listeners.add(field);
        }

        @Override
        public void tick() {
            super.tick();
        }

        @Override
        public @NotNull Component getNarration() {
            return CommonComponents.EMPTY;
        }

        @Override
        public void render(
                @NotNull GuiGraphics graphics,
                int index,
                int y,
                int x,
                int width,
                int height,
                int mouseX,
                int mouseY,
                boolean hovered,
                float partialTicks
        ) {
            int leftInset = Math.min(ITEM_FIELD_LEFT_INSET, Math.max(0, width - 1));
            int widthAfterLeftInset = width - leftInset;
            int rightInset = Math.min(ITEM_FIELD_RIGHT_INSET, Math.max(0, widthAfterLeftInset - 1));
            int fieldX = x + leftInset;
            field.setX(fieldX);
            field.setY(y + 8);
            field.setWidth(Math.max(1, widthAfterLeftInset - rightInset));
            field.render(graphics, mouseX, mouseY, partialTicks);
            if (field.shouldRenderHint()) {
                graphics.drawString(
                        Minecraft.getInstance().font,
                        controller.acceptedItem(),
                        field.getX() + 5,
                        field.getY() + 6,
                        0xFF888888
                );
            }
        }
    }

    private static final class CountAndCheckEntry extends ConfigScreenList.LabeledEntry {
        private final RewardPairController controller;
        private final DraftEditBox field;
        private final Button checkButton;

        private CountAndCheckEntry(RewardPairController controller) {
            super(
                    Component.translatable(KEY_PREFIX + "count").getString(),
                    configPath(TwisterMillConfig.ADVANCEMENT_DROP_COUNT)
            );
            this.controller = controller;
            field = new DraftEditBox(Minecraft.getInstance().font);
            field.setMaxLength(16);
            controller.attachCountField(field);
            checkButton = Button.builder(
                    Component.translatable(KEY_PREFIX + "check"),
                    button -> controller.check()
            ).bounds(0, 0, 64, 20).build();
            listeners.add(field);
            listeners.add(checkButton);
        }

        @Override
        public void tick() {
            super.tick();
            controller.tickStatus();
        }

        @Override
        public void render(
                @NotNull GuiGraphics graphics,
                int index,
                int y,
                int x,
                int width,
                int height,
                int mouseX,
                int mouseY,
                boolean hovered,
                float partialTicks
        ) {
            super.render(graphics, index, y, x, width, height, mouseX, mouseY, hovered, partialTicks);
            int fieldX = x + getLabelWidth(width);
            field.setX(fieldX);
            field.setY(y + 4);
            field.setWidth(45);
            field.render(graphics, mouseX, mouseY, partialTicks);
            if (field.shouldRenderHint()) {
                graphics.drawString(
                        Minecraft.getInstance().font,
                        controller.acceptedCount(),
                        field.getX() + 5,
                        field.getY() + 6,
                        0xFF888888
                );
            }

            checkButton.setX(fieldX + 50);
            checkButton.setY(y + 4);
            checkButton.setWidth(Math.max(54, width - getLabelWidth(width) - 58));
            checkButton.render(graphics, mouseX, mouseY, partialTicks);
            controller.renderStatus(graphics, Minecraft.getInstance().font, fieldX, y + 27);
        }

        @Override
        protected int getLabelWidth(int totalWidth) {
            return (int) (totalWidth * labelWidthMult) + 20;
        }
    }
}
