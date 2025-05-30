package dev.paulsoporan.pingnametags.mixin;

import dev.paulsoporan.pingnametags.colors.PingColors;
import dev.paulsoporan.pingnametags.config.PingNametagsConfig;
import dev.paulsoporan.pingnametags.config.PingNametagsConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import oshi.util.tuples.Pair;

import java.util.*;

@Mixin(EntityRenderer.class)
public class PingNametagsRenderMixin<S extends EntityRenderState> {
    @ModifyArgs(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void renderLabelIfPresent(Args args) {
        PingNametagsConfig config = PingNametagsConfigManager.getConfig();
        MinecraftClient client = MinecraftClient.getInstance();
        if (!config.getEnabled() || client.getNetworkHandler() == null || client.world == null) {
            return;
        }

        S state = args.get(0);
        if (!(state instanceof PlayerEntityRenderState playerState)) {
            return;
        }

        Text text = args.get(1);
        String playerName = text.getString();
        UUID id = client.world.getEntityById(playerState.id).getUuid();

        Collection<PlayerListEntry> playerList = client.getNetworkHandler().getPlayerList();

        Optional<PlayerListEntry> exactMatchEntry = playerList.stream()
                .filter(playerListEntry -> playerListEntry.getProfile().getId().equals(id))
                .findFirst();

        List<PlayerListEntry> fakeEntries = playerList.stream()
                .filter(playerListEntry -> {
                    Text displayName = playerListEntry.getDisplayName();
                    if (displayName == null) {
                        return false;
                    }

                    String displayNameString = collectText(displayName);
                    return displayNameString.equals(playerName);
                })
                .toList();

        PlayerListEntry selectedEntry;
        if (fakeEntries.size() == 1) {
            selectedEntry = fakeEntries.getFirst();
        } else {
            if (exactMatchEntry.isEmpty()) {
                return;
            }

            selectedEntry = exactMatchEntry.get();
        }

        int latency = selectedEntry.getLatency();

        MutableText latencyText = Text.literal(String.format(config.getPingTextFormatString(), latency))
                .setStyle(Style.EMPTY.withColor(PingColors.getColor(latency)));

        Pair<Text, Text> textComponents = switch (config.getPingTextPosition()) {
            case Left -> new Pair<>(latencyText, text);
            case Right -> new Pair<>(text, latencyText);
        };

        Text newText = Text.literal("")
                .append(textComponents.getA())
                .append(" ")
                .append(textComponents.getB());

        args.set(1, newText);
    }

    @Unique
    private String collectText(Text text) {
        return text.getString().concat(String.join("", text.getSiblings().stream().map(this::collectText).toList()));
    }
}
