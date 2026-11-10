package io.github.yanbu3.mobrewards;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Carries permanent Spider unlock state from each player's server save to their client. */
public record SpiderClimbPayload(boolean unlocked) implements CustomPacketPayload {
    public static final Type<SpiderClimbPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(MobRewardsMod.MOD_ID, "spider_climb"));
    public static final StreamCodec<ByteBuf, SpiderClimbPayload> CODEC =
        StreamCodec.composite(ByteBufCodecs.BOOL, SpiderClimbPayload::unlocked, SpiderClimbPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
