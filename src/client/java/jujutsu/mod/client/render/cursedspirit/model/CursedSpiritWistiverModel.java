package jujutsu.mod.client.render.cursedspirit.model;

// Ported from the asset pack's vanilla model class: part hierarchy and createBodyLayer()
// are verbatim; only the hierarchy target changed (EntityModel, 1.21.8).
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import jujutsu.mod.client.render.cursedspirit.CursedSpiritRenderState;
import jujutsu.mod.client.render.cursedspirit.anim.CursedSpiritWistiverAnimations;

public class CursedSpiritWistiverModel extends EntityModel<CursedSpiritRenderState> {
    public final ModelPart all;
    public final ModelPart flater;
    public final ModelPart bod;
    public final ModelPart head;
    public final ModelPart bone2;
    public final ModelPart bone10;
    public final ModelPart bone9;
    public final ModelPart body;
    public final ModelPart bone;
    public final ModelPart bone11;
    public final ModelPart bone12;
    public final ModelPart left_arm;
    public final ModelPart bone3;
    public final ModelPart left_arm2;
    public final ModelPart bone13;
    public final ModelPart left_front_arm;
    public final ModelPart jaw;
    public final ModelPart top_jaw;
    public final ModelPart right_arm;
    public final ModelPart bone4;
    public final ModelPart right_arm2;
    public final ModelPart bone5;
    public final ModelPart right_front_arm;
    public final ModelPart jaw2;
    public final ModelPart top_jaw2;

    private final KeyframeAnimation idleAnimation;
    private final KeyframeAnimation walkAnimation;
    private final KeyframeAnimation attackAnimation;
    private final KeyframeAnimation screamAnimation;

    public CursedSpiritWistiverModel(ModelPart root) {
        super(root);
        this.all = root.getChild("all");
        this.flater = this.all.getChild("flater");
        this.bod = this.flater.getChild("bod");
        this.head = this.bod.getChild("head");
        this.bone2 = this.head.getChild("bone2");
        this.bone10 = this.bone2.getChild("bone10");
        this.bone9 = this.bone2.getChild("bone9");
        this.body = this.bod.getChild("body");
        this.bone = this.body.getChild("bone");
        this.bone11 = this.body.getChild("bone11");
        this.bone12 = this.body.getChild("bone12");
        this.left_arm = this.bod.getChild("left_arm");
        this.bone3 = this.left_arm.getChild("bone3");
        this.left_arm2 = this.bone3.getChild("left_arm2");
        this.bone13 = this.left_arm2.getChild("bone13");
        this.left_front_arm = this.left_arm2.getChild("left_front_arm");
        this.jaw = this.left_front_arm.getChild("jaw");
        this.top_jaw = this.left_front_arm.getChild("top_jaw");
        this.right_arm = this.bod.getChild("right_arm");
        this.bone4 = this.right_arm.getChild("bone4");
        this.right_arm2 = this.bone4.getChild("right_arm2");
        this.bone5 = this.right_arm2.getChild("bone5");
        this.right_front_arm = this.right_arm2.getChild("right_front_arm");
        this.jaw2 = this.right_front_arm.getChild("jaw2");
        this.top_jaw2 = this.right_front_arm.getChild("top_jaw2");
        this.idleAnimation = CursedSpiritWistiverAnimations.IDLE.bake(root());
        this.walkAnimation = CursedSpiritWistiverAnimations.WALK.bake(root());
        this.attackAnimation = CursedSpiritWistiverAnimations.ATTACK.bake(root());
        this.screamAnimation = CursedSpiritWistiverAnimations.SCREAMER.bake(root());
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();
        PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)24.0f, (float)0.0f));
        PartDefinition flater = all.addOrReplaceChild("flater", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)0.0f, (float)0.0f));
        PartDefinition bod = flater.addOrReplaceChild("bod", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)-24.0f, (float)2.0f));
        PartDefinition head = bod.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)0.5f, (float)0.0f));
        PartDefinition bone2 = head.addOrReplaceChild("bone2", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5f, -9.0f, -6.0f, 9.0f, 9.0f, 9.0f, new CubeDeformation(-0.01f)), PartPose.offsetAndRotation((float)0.0f, (float)0.0f, (float)0.0f, (float)0.0f, (float)0.0f, (float)-0.0873f));
        PartDefinition bone10 = bone2.addOrReplaceChild("bone10", CubeListBuilder.create(), PartPose.offsetAndRotation((float)-0.25f, (float)-4.75f, (float)1.0f, (float)1.5708f, (float)1.3526f, (float)1.5708f));
        PartDefinition cube_r1 = bone10.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(48, 13).mirror().addBox(-4.5f, -1.0f, -1.0f, 2.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)).mirror(false).texOffs(48, 13).addBox(-2.5f, -1.0f, -1.0f, 1.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)0.0f, (float)0.0f, (float)-0.7854f, (float)0.0f, (float)0.0f));
        PartDefinition bone9 = bone2.addOrReplaceChild("bone9", CubeListBuilder.create(), PartPose.offsetAndRotation((float)0.0f, (float)-5.25f, (float)-3.0f, (float)0.0f, (float)0.0f, (float)0.1309f));
        PartDefinition cube_r2 = bone9.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(48, 13).mirror().addBox(-13.0f, -1.0f, -1.0f, 2.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)).mirror(false).texOffs(48, 13).addBox(-11.0f, -1.0f, -1.0f, 1.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)).texOffs(48, 13).mirror().addBox(-3.0f, -1.0f, -1.0f, 1.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)).mirror(false).texOffs(48, 13).addBox(-2.0f, -1.0f, -1.0f, 2.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)6.5f, (float)0.0f, (float)0.0f, (float)-0.7854f, (float)0.0f, (float)0.0f));
        PartDefinition body = bod.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 18).addBox(-4.5f, 0.0f, -3.0f, 9.0f, 10.0f, 5.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)0.0f, (float)0.0f, (float)0.3927f, (float)0.0f, (float)0.0f));
        PartDefinition bone = body.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(47, 0).addBox(-1.0f, 0.0f, -2.0f, 2.0f, 11.0f, 2.0f, new CubeDeformation(0.0f)).texOffs(28, 18).mirror().addBox(1.0f, 2.0f, -4.0f, 2.0f, 5.0f, 3.0f, new CubeDeformation(0.0f)).mirror(false).texOffs(28, 18).addBox(-3.0f, 2.0f, -4.0f, 2.0f, 5.0f, 3.0f, new CubeDeformation(0.0f)), PartPose.offset((float)0.0f, (float)10.0f, (float)1.0f));
        PartDefinition bone11 = body.addOrReplaceChild("bone11", CubeListBuilder.create(), PartPose.offsetAndRotation((float)-1.25f, (float)7.75f, (float)0.0f, (float)1.5708f, (float)1.2654f, (float)1.5708f));
        PartDefinition cube_r3 = bone11.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(48, 13).mirror().addBox(-4.5f, -1.0f, -1.0f, 2.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)).mirror(false).texOffs(48, 13).mirror().addBox(-2.5f, -1.0f, -1.0f, 2.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation((float)0.0f, (float)0.0f, (float)0.0f, (float)-0.7854f, (float)0.0f, (float)0.0f));
        PartDefinition bone12 = body.addOrReplaceChild("bone12", CubeListBuilder.create(), PartPose.offsetAndRotation((float)1.5f, (float)3.5f, (float)-1.5f, (float)-1.5708f, (float)-1.2217f, (float)1.5708f));
        PartDefinition cube_r4 = bone12.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(48, 13).mirror().addBox(-4.5f, -1.0f, -1.0f, 2.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)).mirror(false).texOffs(48, 13).mirror().addBox(-2.5f, -1.0f, -1.0f, 2.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation((float)0.0f, (float)0.0f, (float)0.0f, (float)-0.7854f, (float)0.0f, (float)0.0f));
        PartDefinition left_arm = bod.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offsetAndRotation((float)-4.5f, (float)2.0f, (float)-0.5f, (float)0.0f, (float)0.0f, (float)0.2618f));
        PartDefinition bone3 = left_arm.addOrReplaceChild("bone3", CubeListBuilder.create(), PartPose.offsetAndRotation((float)0.5f, (float)0.0f, (float)-3.0f, (float)0.3927f, (float)0.1745f, (float)0.2618f));
        PartDefinition left_arm2 = bone3.addOrReplaceChild("left_arm2", CubeListBuilder.create().texOffs(55, 0).mirror().addBox(-3.5f, -1.0f, -1.5f, 2.0f, 5.0f, 2.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation((float)0.5f, (float)2.0f, (float)3.0f, (float)-0.3054f, (float)0.0f, (float)0.0f));
        PartDefinition cube_r5 = left_arm2.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(36, 13).mirror().addBox(-2.0f, -2.0f, -1.0f, 4.0f, 4.0f, 4.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation((float)-2.5f, (float)0.0f, (float)-0.5f, (float)0.0f, (float)-1.5708f, (float)0.0f));
        PartDefinition bone13 = left_arm2.addOrReplaceChild("bone13", CubeListBuilder.create(), PartPose.offset((float)-2.5f, (float)0.0f, (float)-0.5f));
        PartDefinition left_front_arm = left_arm2.addOrReplaceChild("left_front_arm", CubeListBuilder.create().texOffs(0, 43).mirror().addBox(-1.0f, -1.0f, -5.0f, 2.0f, 2.0f, 6.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offset((float)-2.5f, (float)5.0f, (float)-0.5f));
        PartDefinition jaw = left_front_arm.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(27, 0).mirror().addBox(-2.0f, 0.0f, -6.0f, 4.0f, 2.0f, 6.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation((float)0.0f, (float)1.0f, (float)-5.0f, (float)0.3927f, (float)0.0f, (float)0.0f));
        PartDefinition top_jaw = left_front_arm.addOrReplaceChild("top_jaw", CubeListBuilder.create().texOffs(0, 33).mirror().addBox(-3.0f, -3.0f, -6.0f, 6.0f, 4.0f, 6.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offset((float)0.0f, (float)1.0f, (float)-5.0f));
        PartDefinition right_arm = bod.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offsetAndRotation((float)4.5f, (float)2.0f, (float)-0.5f, (float)0.0f, (float)0.0f, (float)-0.2618f));
        PartDefinition bone4 = right_arm.addOrReplaceChild("bone4", CubeListBuilder.create(), PartPose.offsetAndRotation((float)-0.5f, (float)0.0f, (float)-3.0f, (float)0.3927f, (float)-0.1745f, (float)-0.2618f));
        PartDefinition right_arm2 = bone4.addOrReplaceChild("right_arm2", CubeListBuilder.create().texOffs(55, 0).addBox(1.5f, -1.0f, -1.5f, 2.0f, 5.0f, 2.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)-0.5f, (float)2.0f, (float)3.0f, (float)-0.3054f, (float)0.0f, (float)0.0f));
        PartDefinition cube_r6 = right_arm2.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(36, 13).addBox(-2.0f, -2.0f, -1.0f, 4.0f, 4.0f, 4.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)2.5f, (float)0.0f, (float)-0.5f, (float)0.0f, (float)1.5708f, (float)0.0f));
        PartDefinition bone5 = right_arm2.addOrReplaceChild("bone5", CubeListBuilder.create(), PartPose.offset((float)2.5f, (float)0.0f, (float)-0.5f));
        PartDefinition right_front_arm = right_arm2.addOrReplaceChild("right_front_arm", CubeListBuilder.create().texOffs(0, 43).addBox(-1.0f, -1.0f, -5.0f, 2.0f, 2.0f, 6.0f, new CubeDeformation(0.0f)), PartPose.offset((float)2.5f, (float)5.0f, (float)-0.5f));
        PartDefinition jaw2 = right_front_arm.addOrReplaceChild("jaw2", CubeListBuilder.create().texOffs(27, 0).addBox(-2.0f, 0.0f, -6.0f, 4.0f, 2.0f, 6.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)1.0f, (float)-5.0f, (float)0.3927f, (float)0.0f, (float)0.0f));
        PartDefinition top_jaw2 = right_front_arm.addOrReplaceChild("top_jaw2", CubeListBuilder.create().texOffs(0, 33).addBox(-3.0f, -3.0f, -6.0f, 6.0f, 4.0f, 6.0f, new CubeDeformation(0.0f)), PartPose.offset((float)0.0f, (float)1.0f, (float)-5.0f));
        return LayerDefinition.create((MeshDefinition)meshdefinition, (int)64, (int)64);
    }

    @Override
    public void setupAnim(CursedSpiritRenderState state) {
        root().getAllParts().forEach(ModelPart::resetPose);
        this.head.yRot = state.yRot * 0.017453292f;
        this.head.xRot = state.xRot * 0.017453292f;
        this.screamAnimation.apply(state.scream, state.ageInTicks);
        this.attackAnimation.apply(state.attack, state.ageInTicks);
        this.idleAnimation.apply(state.idle, state.ageInTicks);
        if (!state.scream.isStarted()) {
            this.walkAnimation.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 2.5f, 4.5f);
        }
    }
}
