package net.finnigan.tommemod.mixin;

import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(PoiTypes.class)
public abstract class PoiTypesMixin {

    // Vanilla registers minecraft:beehive with maxTickets=0 (bees just search for the POI,
    // they never claim a ticket, so this never mattered before). ModVillagers.BEEKEEPER matches
    // that same vanilla PoiTypes.BEEHIVE key directly rather than registering a second PoiType
    // on the same blockstates (Forge forbids that). This is what actually lets a tamed
    // beekeeper villager claim a real placed beehive.
    //
    // Plain reflection (Field.setAccessible + set) can't do this: PoiType is a Java record, and
    // the JVM hard-refuses to set a record's final fields via reflection even with
    // setAccessible(true) (IllegalAccessException at runtime, verified in-game). This patches
    // the literal "0" argument at its one call site instead.
    //
    // The @Slice bounds it to [BEEHIVE field read, BEE_NEST field read) rather than [BEEHIVE
    // field read, next register(...) call): register() is called ~20 times in this method (once
    // per POI type), so an unqualified @At on it resolves to its FIRST occurrence in the whole
    // method (armorer's, near the top) regardless of where "from" is — not "the next one after
    // BEEHIVE" — which produced a negative/backwards slice range and failed to apply. BEEHIVE
    // and BEE_NEST are each read exactly once in this method, so anchoring on both is
    // unambiguous and safely contains just beehive's own registration call.
    @ModifyConstant(
            method = "bootstrap",
            constant = @Constant(intValue = 0),
            slice = @Slice(
                    from = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
                            target = "Lnet/minecraft/world/entity/ai/village/poi/PoiTypes;BEEHIVE:Lnet/minecraft/resources/ResourceKey;"),
                    to = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
                            target = "Lnet/minecraft/world/entity/ai/village/poi/PoiTypes;BEE_NEST:Lnet/minecraft/resources/ResourceKey;")
            )
    )
    private static int tommemod$allowBeekeeperOnVanillaBeehive(int maxTickets) {
        return 1;
    }
}
