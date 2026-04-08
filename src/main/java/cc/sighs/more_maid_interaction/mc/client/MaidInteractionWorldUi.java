package cc.sighs.more_maid_interaction.mc.client;

import cc.sighs.more_maid_interaction.MoreMaidInteraction;
import cc.sighs.more_maid_interaction.mc.interaction.BuiltinMaidInteractionEvent;
import cc.sighs.more_maid_interaction.mc.network.C2SMaidGiftExecutePacket;
import cc.sighs.more_maid_interaction.mc.network.C2SMaidGiftStagePacket;
import cc.sighs.more_maid_interaction.mc.network.C2SMaidInteractionEventPacket;
import cc.sighs.more_maid_interaction.mc.network.ModNetwork;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.platform.InputConstants;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.WorldWindow;
import com.sighs.apricityui.instance.element.Slot;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = MoreMaidInteraction.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MaidInteractionWorldUi {
    private static final String DOCUMENT_PATH = "more_maid_interaction/maid_interaction_panel.html";

    private static final float WINDOW_WIDTH = 280.0F;
    private static final float WINDOW_HEIGHT = 190.0F;
    private static final float WINDOW_SCALE = 0.0065F;
    private static final float WINDOW_Y_OFFSET = 0.38F;
    private static final double WINDOW_SIDE_OFFSET = 0.96D;
    private static final double WINDOW_FORWARD_OFFSET = 0.14D;
    private static final int WINDOW_HIT_DISTANCE = 8;
    private static final double WINDOW_KEEP_DISTANCE_SQR = 100.0D;
    private static final float YAW_SMOOTHING = 0.22F;
    private static final float PITCH_SMOOTHING = 0.18F;

    private static final int MAX_GIFT_DISTINCT = 24;
    private static final int MAX_GIFT_TOTAL = 4096;
    private static final int GIFT_SLOT_RENDER_COUNT = 8;

    private static WorldWindow activeWindow;
    private static boolean activeWindowAdded;
    private static UUID activeMaidUuid;
    private static int activeMaidEntityId = -1;
    private static float activeWindowYaw;
    private static float activeWindowPitch;

    private static boolean pendingClose;
    private static boolean giftMode;
    private static BuiltinMaidInteractionEvent selectedEvent;
    private static boolean pendingDetailReveal;
    private static boolean giftExecuteSubmitted;
    private static final List<GiftSelection> giftSelections = new ArrayList<>();

    private MaidInteractionWorldUi() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            closeWindow();
            return;
        }

        if (pendingClose) {
            closeWindow();
        }

        while (ClientKeyMappings.OPEN_INTERACTION_PANEL.consumeClick()) {
            handleHotkey(minecraft);
        }

        if (pendingDetailReveal) {
            if (activeWindow != null && activeWindow.document != null) {
                setDetailVisible(activeWindow.document, true);
            }
            pendingDetailReveal = false;
        }

        updateActiveWindow(minecraft);
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        closeWindow();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != InputConstants.PRESS) {
            return;
        }
        if (activeWindow == null || activeWindow.document == null) {
            return;
        }

        Position realPos = activeWindow.getRealPos();
        if (realPos == null) {
            return;
        }

        Document document = activeWindow.document;
        if (!isInsideElement(document, "event-detail-column", realPos)) {
            return;
        }

        if (event.getButton() == 1) {
            if (!giftMode || !isInsideElement(document, "gift-panel", realPos)) {
                return;
            }
            boolean added = addGiftFromMainHand(document, Screen.hasShiftDown());
            MoreMaidInteraction.LOGGER.info(
                    "Gift fallback add: x={}, y={}, shift={}, added={}",
                    realPos.x,
                    realPos.y,
                    Screen.hasShiftDown(),
                    added
            );
            if (added) {
                event.setCanceled(true);
            }
            return;
        }

        if (event.getButton() != 0) {
            return;
        }

        if (selectedEvent != null && isInsideElement(document, "event-execute", realPos)) {
            if (activeMaidUuid == null) {
                MoreMaidInteraction.LOGGER.warn("Event fallback execute skipped: active maid uuid is null");
                return;
            }
            executeSelectedEvent(document, activeMaidUuid);
            MoreMaidInteraction.LOGGER.info("Event fallback execute: event={}, x={}, y={}", selectedEvent, realPos.x, realPos.y);
            event.setCanceled(true);
            return;
        }

        if (!giftMode || !isInsideElement(document, "gift-panel", realPos)) {
            return;
        }

        if (isInsideElement(document, "gift-execute", realPos)) {
            if (activeMaidUuid == null) {
                MoreMaidInteraction.LOGGER.warn("Gift fallback execute skipped: active maid uuid is null");
                return;
            }
            executeGift(document, activeMaidUuid);
            MoreMaidInteraction.LOGGER.info("Gift fallback execute: x={}, y={}", realPos.x, realPos.y);
            event.setCanceled(true);
            return;
        }

        if (isInsideElement(document, "gift-clear", realPos)) {
            giftSelections.clear();
            giftExecuteSubmitted = false;
            sendStageClear(activeMaidUuid);
            refreshGiftUi(document);
            showActionBar(Minecraft.getInstance(), "message.more_maid_interaction.gift_cleared");
            MoreMaidInteraction.LOGGER.info("Gift fallback clear: x={}, y={}", realPos.x, realPos.y);
            event.setCanceled(true);
        }
    }

    private static void handleHotkey(Minecraft minecraft) {
        EntityMaid targetMaid = getCrosshairMaid(minecraft);
        if (targetMaid == null) {
            if (activeWindow != null) {
                closeWindow();
                showActionBar(minecraft, "message.more_maid_interaction.panel_closed");
            } else {
                showActionBar(minecraft, "message.more_maid_interaction.aim_maid");
            }
            return;
        }

        if (activeMaidUuid != null && activeMaidUuid.equals(targetMaid.getUUID())) {
            closeWindow();
            showActionBar(minecraft, "message.more_maid_interaction.panel_closed");
            return;
        }

        openWindowForMaid(minecraft, targetMaid);
        showActionBar(minecraft, "message.more_maid_interaction.opened");
    }

    private static void openWindowForMaid(Minecraft minecraft, EntityMaid maid) {
        closeWindow();

        WorldWindow window = new WorldWindow(
                DOCUMENT_PATH,
                maid.position().add(0.0D, maid.getBbHeight() + WINDOW_Y_OFFSET, 0.0D),
                WINDOW_WIDTH,
                WINDOW_HEIGHT,
                WINDOW_HIT_DISTANCE
        );

        window.setScale(WINDOW_SCALE);
        syncWindowPosition(window, maid);
        activeWindowYaw = getCameraYaw(minecraft);
        activeWindowPitch = getCameraPitch(minecraft);
        window.setRotation(activeWindowYaw, activeWindowPitch);

        Document document = window.document;
        if (document == null) {
            MoreMaidInteraction.LOGGER.error("Failed to create world UI document: {}", DOCUMENT_PATH);
            showActionBar(minecraft, "message.more_maid_interaction.ui_missing");
            return;
        }

        configureDocument(document, maid.getUUID(), maid.getName().getString());

        WorldWindow.addWindow(window);
        activeWindow = window;
        activeWindowAdded = true;
        activeMaidUuid = maid.getUUID();
        activeMaidEntityId = maid.getId();
    }

    private static void configureDocument(Document document, UUID maidUuid, String maidName) {
        giftMode = false;
        selectedEvent = null;
        giftSelections.clear();
        pendingDetailReveal = false;
        giftExecuteSubmitted = false;

        setText(document, "maid-name", maidName);
        setTranslatedText(document, "preview-title", "ui.more_maid_interaction.preview_default_title");
        setTranslatedText(document, "preview-detail", "ui.more_maid_interaction.preview_default_detail");

        setDetailVisible(document, false);
        setGiftPanelVisible(document, false);
        setEventActionVisible(document, false);
        updateEventCardStyles(document, null);
        refreshGiftUi(document);

        bindGiftPanel(document, maidUuid);
        bindEventExecute(document, maidUuid);

        for (BuiltinMaidInteractionEvent event : BuiltinMaidInteractionEvent.values()) {
            bindEventCard(document, maidUuid, event);
        }
    }

    private static void bindEventCard(Document document, UUID maidUuid, BuiltinMaidInteractionEvent event) {
        Element card = document.getElementById(event.buttonElementId());
        if (card == null) {
            MoreMaidInteraction.LOGGER.warn("Missing AUI element: {}", event.buttonElementId());
            return;
        }

        card.addEventListener("mousedown", mouseInput -> {
            if (!(mouseInput instanceof MouseEvent mouseEvent) || mouseEvent.button != 0) {
                return;
            }
            selectEvent(document, event);
            mouseInput.stopPropagation();
        });
    }

    private static void bindGiftPanel(Document document, UUID maidUuid) {
        Element panel = document.getElementById("gift-panel");
        Element slotZone = document.getElementById("gift-slots");
        Element clearButton = document.getElementById("gift-clear");
        Element executeButton = document.getElementById("gift-execute");

        if (panel == null || clearButton == null || executeButton == null) {
            MoreMaidInteraction.LOGGER.warn(
                    "Gift panel binding missing elements: panel={}, slotZone={}, clear={}, execute={}",
                    panel != null,
                    slotZone != null,
                    clearButton != null,
                    executeButton != null
            );
        }

        if (slotZone != null) {
            slotZone.addEventListener("mousedown", mouseInput -> {
                if (!(mouseInput instanceof MouseEvent mouseEvent)) {
                    return;
                }
                if (handleGiftPanelRightClick(document, mouseEvent, "gift-slots")) {
                    mouseInput.stopPropagation();
                }
            });
        }

        if (panel != null) {
            panel.addEventListener("mousedown", mouseInput -> {
                if (!(mouseInput instanceof MouseEvent mouseEvent)) {
                    return;
                }
                if (handleGiftPanelRightClick(document, mouseEvent, "gift-panel")) {
                    mouseInput.stopPropagation();
                }
            });
        }

        if (clearButton != null) {
            clearButton.addEventListener("mousedown", mouseInput -> {
                if (!(mouseInput instanceof MouseEvent mouseEvent) || mouseEvent.button != 0) {
                    return;
                }
                giftSelections.clear();
                giftExecuteSubmitted = false;
                sendStageClear(maidUuid);
                refreshGiftUi(document);
                showActionBar(Minecraft.getInstance(), "message.more_maid_interaction.gift_cleared");
                mouseInput.stopPropagation();
            });
        }

        if (executeButton != null) {
            executeButton.addEventListener("mousedown", mouseInput -> {
                if (!(mouseInput instanceof MouseEvent mouseEvent) || mouseEvent.button != 0) {
                    return;
                }
                executeGift(document, maidUuid);
                mouseInput.stopPropagation();
            });
        }
    }
    private static void bindEventExecute(Document document, UUID maidUuid) {
        Element executeEventButton = document.getElementById("event-execute");
        if (executeEventButton == null) {
            return;
        }
        executeEventButton.addEventListener("mousedown", mouseInput -> {
            if (!(mouseInput instanceof MouseEvent mouseEvent) || mouseEvent.button != 0) {
                return;
            }
            executeSelectedEvent(document, maidUuid);
            mouseInput.stopPropagation();
        });
    }

    private static void selectEvent(Document document, BuiltinMaidInteractionEvent event) {
        selectedEvent = event;
        updateEventCardStyles(document, event);
        requestDetailReveal(document);
        updatePreview(document, event);

        if (event == BuiltinMaidInteractionEvent.GIFT) {
            enterGiftMode(document);
        } else {
            leaveGiftMode(document);
        }
    }

    private static void executeSelectedEvent(Document document, UUID maidUuid) {
        if (selectedEvent == null) {
            showActionBar(Minecraft.getInstance(), "message.more_maid_interaction.select_event_first");
            return;
        }
        if (selectedEvent == BuiltinMaidInteractionEvent.GIFT) {
            executeGift(document, maidUuid);
            return;
        }
        submitSimpleInteraction(maidUuid, selectedEvent);
    }

    private static boolean handleGiftPanelRightClick(Document document, MouseEvent mouseEvent, String source) {
        if (mouseEvent.button != 1) {
            return false;
        }
        if (!giftMode) {
            MoreMaidInteraction.LOGGER.info("Ignored gift right click while not in gift mode: source={}", source);
            return false;
        }

        boolean added = addGiftFromMainHand(document, mouseEvent.shiftKey);
        MoreMaidInteraction.LOGGER.info(
                "Gift right click handled: source={}, shift={}, added={}, targetId={}, currentTargetId={}",
                source,
                mouseEvent.shiftKey,
                added,
                mouseEvent.target == null ? "null" : mouseEvent.target.id,
                mouseEvent.currentTarget == null ? "null" : mouseEvent.currentTarget.id
        );
        return added;
    }

    private static boolean isInsideElement(Document document, String elementId, Position realPos) {
        Element element = document.getElementById(elementId);
        if (element == null) {
            return false;
        }
        Position pos = Position.of(element);
        Size size = Size.of(element);
        if (pos == null || size == null) {
            return false;
        }

        return realPos.x >= pos.x
                && realPos.x <= pos.x + size.width()
                && realPos.y >= pos.y
                && realPos.y <= pos.y + size.height();
    }

    
    private static void updateEventCardStyles(Document document, BuiltinMaidInteractionEvent activeEvent) {
        for (BuiltinMaidInteractionEvent event : BuiltinMaidInteractionEvent.values()) {
            Element card = document.getElementById(event.buttonElementId());
            if (card == null) {
                continue;
            }
            card.setAttribute("class", event == activeEvent ? "event-card active" : "event-card");
        }
    }

    private static void enterGiftMode(Document document) {
        giftMode = true;
        setGiftPanelVisible(document, true);
        setEventActionVisible(document, false);
        setTranslatedText(document, "preview-title", "event.more_maid_interaction.gift");
        setTranslatedText(document, "preview-detail", "event_detail.more_maid_interaction.gift");
        refreshGiftUi(document);
        MoreMaidInteraction.LOGGER.info("Entered gift mode for maid interaction panel");
    }

    private static void leaveGiftMode(Document document) {
        giftMode = false;
        setGiftPanelVisible(document, false);
        setEventActionVisible(document, true);
    }

    private static void executeGift(Document document, UUID maidUuid) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (giftSelections.isEmpty()) {
            showActionBar(minecraft, "message.more_maid_interaction.gift_empty");
            return;
        }

        List<ItemStack> payload = new ArrayList<>();
        for (GiftSelection entry : giftSelections) {
            ItemStack stack = entry.stack.copy();
            stack.setCount(entry.count);
            payload.add(stack);
        }

        giftExecuteSubmitted = true;
        ModNetwork.CHANNEL.sendToServer(new C2SMaidGiftExecutePacket(maidUuid, payload));
        showActionBar(minecraft, "message.more_maid_interaction.gift_execute_sent");
        pendingClose = true;
    }

    private static boolean addGiftFromMainHand(Document document, boolean addWholeStack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }

        ItemStack held = minecraft.player.getMainHandItem();
        if (held.isEmpty()) {
            showActionBar(minecraft, "message.more_maid_interaction.gift_no_item");
            return false;
        }

        int requestCount = addWholeStack ? held.getCount() : 1;
        if (requestCount <= 0) {
            return false;
        }

        int total = totalGiftCount();
        if (total >= MAX_GIFT_TOTAL) {
            showActionBar(minecraft, "message.more_maid_interaction.gift_total_limit");
            return false;
        }

        GiftSelection existing = findSelection(held);
        if (existing == null && giftSelections.size() >= MAX_GIFT_DISTINCT) {
            showActionBar(minecraft, "message.more_maid_interaction.gift_distinct_limit");
            return false;
        }

        int addable = Math.min(requestCount, MAX_GIFT_TOTAL - total);
        if (addable <= 0) {
            showActionBar(minecraft, "message.more_maid_interaction.gift_total_limit");
            return false;
        }

        if (existing == null) {
            ItemStack copy = held.copy();
            copy.setCount(1);
            giftSelections.add(new GiftSelection(copy, addable));
        } else {
            existing.count += addable;
        }

        if (activeMaidUuid != null) {
            ItemStack staged = held.copy();
            staged.setCount(1);
            sendStageAdd(activeMaidUuid, staged, addable);
        }

        if (!minecraft.player.getAbilities().instabuild) {
            held.shrink(addable);
            minecraft.player.getInventory().setChanged();
        }

        refreshGiftUi(document);
        minecraft.player.displayClientMessage(
                Component.translatable("message.more_maid_interaction.gift_added", held.getHoverName(), addable),
                true
        );
        return true;
    }
    private static GiftSelection findSelection(ItemStack candidate) {
        for (GiftSelection entry : giftSelections) {
            if (ItemStack.isSameItemSameTags(entry.stack, candidate)) {
                return entry;
            }
        }
        return null;
    }

    private static int totalGiftCount() {
        int total = 0;
        for (GiftSelection entry : giftSelections) {
            total += entry.count;
        }
        return total;
    }

    private static void refreshGiftUi(Document document) {
        Element hint = document.getElementById("gift-items-hint");
        Element overflow = document.getElementById("gift-overflow");
        Element counter = document.getElementById("gift-counter");

        if (hint != null) {
            hint.innerText = giftSelections.isEmpty()
                    ? Component.translatable("ui.more_maid_interaction.gift_empty").getString()
                    : "";
        }

        if (overflow != null) {
            int hidden = Math.max(0, giftSelections.size() - GIFT_SLOT_RENDER_COUNT);
            overflow.innerText = hidden > 0 ? "+" + hidden : "";
        }

        for (int i = 0; i < GIFT_SLOT_RENDER_COUNT; i++) {
            Element slot = document.getElementById("gift-slot-" + i);
            if (slot == null) {
                continue;
            }

            if (i < giftSelections.size()) {
                GiftSelection entry = giftSelections.get(i);
                slot.innerText = toVirtualSlot(entry);
                slot.setAttribute("style", "display:block;");
            } else {
                slot.innerText = "{id:\"minecraft:air\",Count:1b}";
                slot.setAttribute("style", "display:none;");
            }
        }

        if (counter != null) {
            counter.innerText = Component.translatable(
                    "ui.more_maid_interaction.gift_counter",
                    totalGiftCount(),
                    giftSelections.size(),
                    MAX_GIFT_TOTAL,
                    MAX_GIFT_DISTINCT
            ).getString();
        }
    }

    private static String toVirtualSlot(GiftSelection entry) {
        String itemId = ForgeRegistries.ITEMS.getKey(entry.stack.getItem()).toString();
        int renderCount = Mth.clamp(entry.count, 1, 64);
        return Slot.buildLiteralWithCount(itemId, renderCount);
    }

    private static void sendStageAdd(UUID maidUuid, ItemStack itemTemplate, int count) {
        if (maidUuid == null || itemTemplate == null || itemTemplate.isEmpty() || count <= 0) {
            return;
        }
        ModNetwork.CHANNEL.sendToServer(C2SMaidGiftStagePacket.add(maidUuid, itemTemplate, count));
    }

    private static void sendStageClear(UUID maidUuid) {
        if (maidUuid == null) {
            return;
        }
        ModNetwork.CHANNEL.sendToServer(C2SMaidGiftStagePacket.clear(maidUuid));
    }

    private static void submitSimpleInteraction(UUID maidUuid, BuiltinMaidInteractionEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        ModNetwork.CHANNEL.sendToServer(new C2SMaidInteractionEventPacket(maidUuid, event.eventId()));
        minecraft.player.displayClientMessage(
                Component.translatable("message.more_maid_interaction.interaction_selected", Component.translatable(event.displayNameKey())),
                true
        );
        pendingClose = true;
    }

    private static void updatePreview(Document document, BuiltinMaidInteractionEvent event) {
        setTranslatedText(document, "preview-title", event.displayNameKey());
        setTranslatedText(document, "preview-detail", event.detailKey());
    }

    private static void updateActiveWindow(Minecraft minecraft) {
        if (activeWindow == null || minecraft.level == null || minecraft.player == null) {
            return;
        }

        if (activeMaidEntityId < 0) {
            closeWindow();
            return;
        }

        if (!(minecraft.level.getEntity(activeMaidEntityId) instanceof EntityMaid maid) || !maid.isAlive()) {
            closeWindow();
            return;
        }

        if (!maid.getUUID().equals(activeMaidUuid)) {
            closeWindow();
            return;
        }

        if (minecraft.player.distanceToSqr(maid) > WINDOW_KEEP_DISTANCE_SQR) {
            closeWindow();
            return;
        }

        syncWindowPosition(activeWindow, maid);

        float targetYaw = getCameraYaw(minecraft);
        float yawDelta = Mth.wrapDegrees(targetYaw - activeWindowYaw);
        activeWindowYaw = activeWindowYaw + yawDelta * YAW_SMOOTHING;

        float targetPitch = getCameraPitch(minecraft);
        activeWindowPitch = Mth.lerp(PITCH_SMOOTHING, activeWindowPitch, targetPitch);

        activeWindow.setRotation(activeWindowYaw, activeWindowPitch);
    }

    private static void syncWindowPosition(WorldWindow window, EntityMaid maid) {
        window.setPosition(computeWindowAnchor(maid));
    }

    private static Vec3 computeWindowAnchor(EntityMaid maid) {
        Vec3 look = maid.getViewVector(1.0F);
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() < 1.0E-6D) {
            float yawRad = maid.getYRot() * Mth.DEG_TO_RAD;
            horizontalLook = new Vec3(-Mth.sin(yawRad), 0.0D, Mth.cos(yawRad));
        } else {
            horizontalLook = horizontalLook.normalize();
        }

        Vec3 right = new Vec3(horizontalLook.z, 0.0D, -horizontalLook.x).normalize();
        Vec3 anchor = maid.position()
                .add(right.scale(WINDOW_SIDE_OFFSET))
                .add(horizontalLook.scale(WINDOW_FORWARD_OFFSET));
        double y = maid.getBbHeight() * 0.55D + WINDOW_Y_OFFSET;
        return anchor.add(0.0D, y, 0.0D);
    }

    private static EntityMaid getCrosshairMaid(Minecraft minecraft) {
        HitResult hitResult = minecraft.hitResult;
        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return null;
        }
        if (!(entityHitResult.getEntity() instanceof EntityMaid maid)) {
            return null;
        }
        if (minecraft.player == null || minecraft.player.distanceToSqr(maid) > WINDOW_KEEP_DISTANCE_SQR) {
            return null;
        }
        return maid;
    }

    private static void setGiftPanelVisible(Document document, boolean visible) {
        Element panel = document.getElementById("gift-panel");
        if (panel != null) {
            panel.setAttribute("style", visible ? "display:block;" : "display:none;");
        }
    }

    private static void setEventActionVisible(Document document, boolean visible) {
        Element panel = document.getElementById("event-actions");
        if (panel != null) {
            panel.setAttribute("style", visible ? "display:block;" : "display:none;");
        }
    }

    private static void setDetailVisible(Document document, boolean visible) {
        Element panel = document.getElementById("event-detail-column");
        if (panel == null) {
            return;
        }
        panel.setAttribute("class", visible ? "right-column open" : "right-column");
    }

    private static void requestDetailReveal(Document document) {
        setDetailVisible(document, false);
        pendingDetailReveal = true;
    }

    private static void setText(Document document, String elementId, String text) {
        Element element = document.getElementById(elementId);
        if (element != null) {
            element.innerText = text;
        }
    }

    private static void setTranslatedText(Document document, String elementId, String translationKey) {
        setText(document, elementId, Component.translatable(translationKey).getString());
    }

    private static void showActionBar(Minecraft minecraft, String key) {
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(key), true);
        }
    }

    private static float getCameraYaw(Minecraft minecraft) {
        Camera camera = minecraft.gameRenderer.getMainCamera();
        return camera.getYRot();
    }

    private static float getCameraPitch(Minecraft minecraft) {
        Camera camera = minecraft.gameRenderer.getMainCamera();
        return camera.getXRot();
    }

    private static void closeWindow() {
        if (activeMaidUuid != null && !giftExecuteSubmitted && !giftSelections.isEmpty()) {
            sendStageClear(activeMaidUuid);
        }

        if (activeWindow != null && activeWindowAdded) {
            try {
                WorldWindow.removeWindow(activeWindow);
            } catch (Exception exception) {
                MoreMaidInteraction.LOGGER.warn("Failed to close world interaction UI cleanly", exception);
            }
        }

        activeWindow = null;
        activeWindowAdded = false;
        activeMaidUuid = null;
        activeMaidEntityId = -1;
        activeWindowYaw = 0.0F;
        activeWindowPitch = 0.0F;
        pendingClose = false;

        giftMode = false;
        selectedEvent = null;
        giftSelections.clear();
        pendingDetailReveal = false;
        giftExecuteSubmitted = false;
    }

    private static final class GiftSelection {
        private final ItemStack stack;
        private int count;

        private GiftSelection(ItemStack stack, int count) {
            this.stack = stack;
            this.count = count;
        }
    }
}
