package net.finnigan.tommemod.network;

import net.finnigan.tommemod.TommeMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TommeMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int id = 0;

    public static void register() {
        CHANNEL.registerMessage(id++, ConfirmKeyPacket.class,
                ConfirmKeyPacket::encode,
                ConfirmKeyPacket::decode,
                ConfirmKeyPacket::handle);
        CHANNEL.registerMessage(id++, ConfirmKeyPacket.class,
                ConfirmKeyPacket::encode, ConfirmKeyPacket::decode, ConfirmKeyPacket::handle);
        CHANNEL.registerMessage(id++, SetPlayerRotationPacket.class,
                SetPlayerRotationPacket::encode, SetPlayerRotationPacket::decode, SetPlayerRotationPacket::handle);
    }
}