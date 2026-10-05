package net.finnigan.tommemod.network;

import net.finnigan.tommemod.TommeMod;
import net.finnigan.tommemod.network.packet.AdjustRideDistancePacket;
import net.finnigan.tommemod.network.packet.BallistaFirePacket;
import net.finnigan.tommemod.network.packet.BlueprintModeStatePacket;
import net.finnigan.tommemod.network.packet.CancelConstructionPacket;
import net.finnigan.tommemod.network.packet.ExitBlueprintModePacket;
import net.finnigan.tommemod.network.packet.BlueprintStandActionPacket;
import net.finnigan.tommemod.network.packet.BlueprintStandStatusPacket;
import net.finnigan.tommemod.network.packet.PlaceBlueprintPacket;
import net.finnigan.tommemod.network.packet.SyncConstructionSitesPacket;
import net.finnigan.tommemod.network.packet.ClassAbilityPacket;
import net.finnigan.tommemod.network.packet.MonolithUpgradePacket;
import net.finnigan.tommemod.network.packet.ResetClassPacket;
import net.finnigan.tommemod.network.packet.SyncAccessoryPacket;
import net.finnigan.tommemod.network.packet.SyncReputationHudPacket;
import net.finnigan.tommemod.network.packet.SyncReputationPacket;
import net.finnigan.tommemod.network.packet.SyncSkillDataPacket;
import net.finnigan.tommemod.network.packet.SyncSkillDefinitionsPacket;
import net.finnigan.tommemod.network.packet.UnlockSkillNodePacket;
import net.finnigan.tommemod.network.packet.SyncWitherspineChargeResetPacket;
import net.finnigan.tommemod.network.packet.SyncWitherspineStatePacket;
import net.finnigan.tommemod.network.packet.ReleaseLanternaUsePacket;
import net.finnigan.tommemod.network.packet.GrappleSwingInputPacket;
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
        CHANNEL.registerMessage(id++, SyncAccessoryPacket.class,
                SyncAccessoryPacket::encode, SyncAccessoryPacket::new, SyncAccessoryPacket::handle);
        CHANNEL.registerMessage(id++, SyncReputationPacket.class,
                SyncReputationPacket::encode, SyncReputationPacket::new, SyncReputationPacket::handle);
        CHANNEL.registerMessage(id++, SyncReputationHudPacket.class,
                SyncReputationHudPacket::encode, SyncReputationHudPacket::new, SyncReputationHudPacket::handle);
        CHANNEL.registerMessage(id++, AdjustRideDistancePacket.class,
                AdjustRideDistancePacket::encode, AdjustRideDistancePacket::decode, AdjustRideDistancePacket::handle);
        CHANNEL.registerMessage(id++, MonolithUpgradePacket.class,
                MonolithUpgradePacket::encode, MonolithUpgradePacket::new, MonolithUpgradePacket::handle);
        CHANNEL.registerMessage(id++, BlueprintStandActionPacket.class,
                BlueprintStandActionPacket::encode, BlueprintStandActionPacket::new, BlueprintStandActionPacket::handle);
        CHANNEL.registerMessage(id++, BlueprintStandStatusPacket.class,
                BlueprintStandStatusPacket::encode, BlueprintStandStatusPacket::new, BlueprintStandStatusPacket::handle);
        CHANNEL.registerMessage(id++, BlueprintModeStatePacket.class,
                BlueprintModeStatePacket::encode, BlueprintModeStatePacket::new, BlueprintModeStatePacket::handle);
        CHANNEL.registerMessage(id++, SyncConstructionSitesPacket.class,
                SyncConstructionSitesPacket::encode, SyncConstructionSitesPacket::new, SyncConstructionSitesPacket::handle);
        CHANNEL.registerMessage(id++, PlaceBlueprintPacket.class,
                PlaceBlueprintPacket::encode, PlaceBlueprintPacket::new, PlaceBlueprintPacket::handle);
        CHANNEL.registerMessage(id++, ExitBlueprintModePacket.class,
                ExitBlueprintModePacket::encode, ExitBlueprintModePacket::new, ExitBlueprintModePacket::handle);
        CHANNEL.registerMessage(id++, CancelConstructionPacket.class,
                CancelConstructionPacket::encode, CancelConstructionPacket::new, CancelConstructionPacket::handle);
        CHANNEL.registerMessage(id++, BallistaFirePacket.class,
                BallistaFirePacket::encode, BallistaFirePacket::new, BallistaFirePacket::handle);
        CHANNEL.registerMessage(id++, SyncSkillDefinitionsPacket.class,
                SyncSkillDefinitionsPacket::encode, SyncSkillDefinitionsPacket::new, SyncSkillDefinitionsPacket::handle);
        CHANNEL.registerMessage(id++, SyncSkillDataPacket.class,
                SyncSkillDataPacket::encode, SyncSkillDataPacket::new, SyncSkillDataPacket::handle);
        CHANNEL.registerMessage(id++, UnlockSkillNodePacket.class,
                UnlockSkillNodePacket::encode, UnlockSkillNodePacket::new, UnlockSkillNodePacket::handle);
        CHANNEL.registerMessage(id++, ResetClassPacket.class,
                ResetClassPacket::encode, ResetClassPacket::new, ResetClassPacket::handle);
        CHANNEL.registerMessage(id++, ClassAbilityPacket.class,
                ClassAbilityPacket::encode, ClassAbilityPacket::new, ClassAbilityPacket::handle);
        CHANNEL.registerMessage(id++, SyncWitherspineChargeResetPacket.class,
                SyncWitherspineChargeResetPacket::encode,
                SyncWitherspineChargeResetPacket::new,
                SyncWitherspineChargeResetPacket::handle);
        CHANNEL.registerMessage(id++, SyncWitherspineStatePacket.class,
                SyncWitherspineStatePacket::encode,
                SyncWitherspineStatePacket::new,
                SyncWitherspineStatePacket::handle);
        CHANNEL.registerMessage(id++, ReleaseLanternaUsePacket.class,
                ReleaseLanternaUsePacket::encode,
                ReleaseLanternaUsePacket::new,
                ReleaseLanternaUsePacket::handle);
        CHANNEL.registerMessage(id++, GrappleSwingInputPacket.class,
                GrappleSwingInputPacket::encode,
                GrappleSwingInputPacket::new,
                GrappleSwingInputPacket::handle);
    }
}
