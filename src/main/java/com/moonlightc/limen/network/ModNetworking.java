package com.moonlightc.limen.network;

import com.moonlightc.limen.Limen;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetworking {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            Limen.id("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private ModNetworking() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(ChainswordHoldPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(ChainswordHoldPacket::decode)
                .encoder(ChainswordHoldPacket::encode)
                .consumerMainThread(ChainswordHoldPacket::handle)
                .add();
        CHANNEL.messageBuilder(ChainswordHeatPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(ChainswordHeatPacket::decode)
                .encoder(ChainswordHeatPacket::encode)
                .consumerMainThread(ChainswordHeatPacket::handle)
                .add();
        CHANNEL.messageBuilder(ChainswordGrindPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(ChainswordGrindPacket::decode)
                .encoder(ChainswordGrindPacket::encode)
                .consumerMainThread(ChainswordGrindPacket::handle)
                .add();
    }
}
