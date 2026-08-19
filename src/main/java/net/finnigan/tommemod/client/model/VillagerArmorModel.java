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
 * Armor cut for the Warrior's villager-shaped body, transcribed from
 * tempassets/soldier/villager_armor_layer_1.geo.json and villager_armor_layer_2.geo.json.
 *
 * The Warrior used to borrow vanilla's ZOMBIE_VILLAGER armor meshes. Those fit a villager head, but
 * everything below it is player-proportioned, so a chestplate cut across the robe and the boots sat
 * inside the legs. These are the same two vanilla armor layers re-inflated to clear this rig: the
 * uv layout is untouched, so a 64x32 armor sheet painted for vanilla still lines up.
 *
 * The head box is 9 tall, not 8, because a villager skull is 10 - that is the one shape change. The
 * inner (leggings) layer nudges each leg 0.25px outward, which is what the source geo asks for.
 */
public class VillagerArmorModel extends HumanoidModel<WarriorVillagerEntity> {

    public VillagerArmorModel(ModelPart root) {
        super(root);
    }

    /** Helmet, chestplate and boots - vanilla's "layer 1". */
    public static LayerDefinition createOuterLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0F, -9.5F, -4.0F, 8.0F, 9.0F, 8.0F, new CubeDeformation(0.75F)),
                PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create()
                        .texOffs(32, 0).addBox(-4.0F, -9.5F, -4.0F, 8.0F, 9.0F, 8.0F, new CubeDeformation(1.0F)),
                PartPose.ZERO);

        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.6F)),
                PartPose.ZERO);

        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                        .texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.75F)),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                        .texOffs(40, 16).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.75F)),
                PartPose.offset(5.0F, 2.0F, 0.0F));

        // The boots ride on the leg parts, which is how vanilla does it too.
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                        .texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.75F)),
                PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                        .texOffs(0, 16).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.75F)),
                PartPose.offset(1.9F, 12.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 32);
    }

    /** Leggings - vanilla's "layer 2". Head and arms are empty because nothing is ever drawn on them. */
    public static LayerDefinition createInnerLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));

        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.5F)),
                PartPose.ZERO);

        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                        .texOffs(0, 16).addBox(-2.25F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.55F)),
                PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                        .texOffs(0, 16).mirror().addBox(-1.75F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.55F)),
                PartPose.offset(1.9F, 12.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 32);
    }
}
