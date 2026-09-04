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
 * The art is painted on the VILLAGER uv map (10-tall head, nose, sleeve and trouser overlays), which
 * shares nothing but its 64x64 canvas with the player/zombie map the renderer used to bake. That
 * mismatch is what garbled everything below the neck.
 *
 * It is still a HumanoidModel subclass rather than a GeckoLib model, because that is what keeps the
 * Warrior animating like a player and keeps HumanoidArmorLayer and ItemInHandLayer working - it needs
 * to visibly wear the armor it picks up and hold the weapon it was conscripted with. Only the mesh is
 * villager-shaped; the bones are the standard humanoid ones, so nothing about the animation changes.
 *
 * Both overlay ("second skin layer") bones from the geo are drawn: headwear on the hat part, and the
 * robe folded into body. The arms and legs carry theirs already, as the 0.25-deformation second box
 * on each.
 *
 * The robe is the one place this deviates from the geo, in two ways, and both are about leaving room
 * for the armor a Warrior is meant to wear:
 *
 * <ul>
 *   <li>12 tall rather than the geo's 20. At full length it reaches y=20, which buries the leggings
 *       and boot tops that VillagerArmorModel draws down the legs - a Warrior armored by its village's
 *       Armorer would visibly lose half of what it was given. Cut to the torso it stops at the waist,
 *       and since the uv is read from the top of the robe art down, the 12 rows it keeps are exactly
 *       the chest and shoulders.</li>
 *   <li>0.45 deformation rather than 0.49. At 0.49 the robe sits 0.01 inside the leggings' own body
 *       box (8x12x4 plus 0.5), which is z-fighting rather than clearance. 0.45 gives it real room and
 *       is still comfortably outside the 4-deep torso.</li>
 * </ul>
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

        // The geo's headwear bone, verbatim: same 8x10x8 as the head, inflated clear of it, on the
        // uv the villager map reserves for the head overlay. HumanoidModel poses "hat" off the head
        // every frame, so it tracks the head without being parented to it the way the geo is.
        root.addOrReplaceChild("hat", CubeListBuilder.create()
                        .texOffs(32, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F, new CubeDeformation(0.51F)),
                PartPose.ZERO);

        // 8x12x4, the depth the geo actually declares. texOffs is 18,22 rather than the villager's
        // 16,20 because a box's unwrap origin shifts with its depth: at d=4 the front face lands at
        // (u+4, v+4), so 18,22 puts it on (22,26) - pixel-exact with the geo's own front-face uv.
        // The side faces then read the inner 4px of art cut 6px wide, and the back sits 2px off; that
        // is the cost of the shallower torso, and it is far less visible than the extra depth was.
        //
        // The second box is the robe - the body's overlay layer - by the same arithmetic: 2,40 lands
        // its front face on (6,44), which is where the geo's bodywear bone puts it. See the class
        // javadoc for why it is 12 tall and 0.45 deep rather than the geo's 20 and 0.49.
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(18, 22).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F)
                        .texOffs(2, 40).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.45F)),
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
