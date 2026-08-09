package net.finnigan.tommemod.client.model;

import net.finnigan.tommemod.entity.custom.WarriorVillagerEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The Warrior's rig, transcribed from tempassets/warrior_villager/warrior_villager.geo.json.
 *
 * The art is painted on the VILLAGER uv map (10-tall head, nose, robe over the body, sleeve and
 * trouser overlays), which shares nothing but its 64x64 canvas with the player/zombie map the
 * renderer used to bake. That mismatch is what garbled everything below the neck.
 *
 * It is still a HumanoidModel subclass rather than a GeckoLib model, because that is what keeps the
 * Warrior animating like a player and keeps HumanoidArmorLayer and ItemInHandLayer working - it needs
 * to visibly wear the armor it picks up and hold the weapon it was conscripted with. Only the mesh is
 * villager-shaped; the bones are the standard humanoid ones, so nothing about the animation changes.
 *
 * Note the deliberate absence of a hat_rim: vanilla's zombie villager has one at uv (30,47), but this
 * texture packs its trouser and sleeve overlays across (29,38)-(61,54), so drawing a rim would smear
 * those over the hat. The source geo has no rim either (its "headwear2" bone is empty).
 */
public class WarriorVillagerModel extends HumanoidModel<WarriorVillagerEntity> {

    public WarriorVillagerModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();

        // Head is 10 tall, not the humanoid 8, and carries the villager nose. Folded into one part the
        // way vanilla's ZombieVillagerModel does, so "head" stays a single animatable bone.
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F)
                        .texOffs(24, 0).addBox(-1.0F, -3.0F, -6.0F, 2.0F, 4.0F, 2.0F),
                PartPose.ZERO);

        root.addOrReplaceChild("hat", CubeListBuilder.create()
                        .texOffs(32, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F, new CubeDeformation(0.51F)),
                PartPose.ZERO);

        // Body is 6 deep, not the 4 the geo declares: its per-face uvs are the exact standard unwrap of
        // texOffs(16,20) at 8x12x6, so the art is painted for the deeper vanilla villager torso.
        // The robe below it is the same story at texOffs(0,38).
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(16, 20).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 12.0F, 6.0F)
                        .texOffs(0, 38).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 20.0F, 6.0F, new CubeDeformation(0.49F)),
                PartPose.ZERO);

        // The geo's arm bones pivot at x=0, which would swing each arm around the body's centre line.
        // That is Blockbench noise - the cubes themselves sit exactly where vanilla's humanoid arms do -
        // so the standard +/-5 pivots are used and only the uvs come from the geo.
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                        .texOffs(44, 22).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F)
                        .texOffs(45, 38).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.25F)),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                        .texOffs(44, 22).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F)
                        .texOffs(45, 38).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.25F)),
                PartPose.offset(5.0F, 2.0F, 0.0F));

        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                        .texOffs(0, 22).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F)
                        .texOffs(29, 38).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.25F)),
                PartPose.offset(-2.0F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                        .texOffs(0, 22).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F)
                        .texOffs(29, 38).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.25F)),
                PartPose.offset(2.0F, 12.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }
}
