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
import jujutsu.mod.client.render.cursedspirit.CursedSpiritClips;
import jujutsu.mod.client.render.cursedspirit.CursedSpiritRenderState;
import jujutsu.mod.client.render.cursedspirit.anim.CursedSpiritWalkingBedAnimations;

public class CursedSpiritWalkingBedModel extends EntityModel<CursedSpiritRenderState> {
    public final ModelPart flatter;
    public final ModelPart body;
    public final ModelPart bone8;
    public final ModelPart bone2;
    public final ModelPart bone3;
    public final ModelPart bone5;
    public final ModelPart bone4;
    public final ModelPart bone9;
    public final ModelPart left_arm2;
    public final ModelPart right_arm2;
    public final ModelPart head2;
    public final ModelPart right_arm;
    public final ModelPart arm5;
    public final ModelPart arm;
    public final ModelPart arm2;
    public final ModelPart left_arm;
    public final ModelPart arm3;
    public final ModelPart arm4;
    public final ModelPart arm6;
    public final ModelPart head;
    public final ModelPart bone7;
    public final ModelPart bone14;
    public final ModelPart bone15;
    public final ModelPart right_leg;
    public final ModelPart bone;
    public final ModelPart leg;
    public final ModelPart left_leg;
    public final ModelPart bone6;
    public final ModelPart leg2;

    private final KeyframeAnimation idleAnimation;
    private final KeyframeAnimation walkAnimation;
    private final KeyframeAnimation attackAnimation;
    private final KeyframeAnimation screamAnimation;

    public CursedSpiritWalkingBedModel(ModelPart root) {
        super(root);
        this.flatter = root.getChild("flatter");
        this.body = this.flatter.getChild("body");
        this.bone8 = this.body.getChild("bone8");
        this.bone2 = this.body.getChild("bone2");
        this.bone3 = this.bone2.getChild("bone3");
        this.bone5 = this.body.getChild("bone5");
        this.bone4 = this.bone5.getChild("bone4");
        this.bone9 = this.body.getChild("bone9");
        this.left_arm2 = this.bone9.getChild("left_arm2");
        this.right_arm2 = this.bone9.getChild("right_arm2");
        this.head2 = this.bone9.getChild("head2");
        this.right_arm = this.body.getChild("right_arm");
        this.arm5 = this.right_arm.getChild("arm5");
        this.arm = this.arm5.getChild("arm");
        this.arm2 = this.arm.getChild("arm2");
        this.left_arm = this.body.getChild("left_arm");
        this.arm3 = this.left_arm.getChild("arm3");
        this.arm4 = this.arm3.getChild("arm4");
        this.arm6 = this.arm4.getChild("arm6");
        this.head = this.body.getChild("head");
        this.bone7 = this.head.getChild("bone7");
        this.bone14 = this.bone7.getChild("bone14");
        this.bone15 = this.bone7.getChild("bone15");
        this.right_leg = this.flatter.getChild("right_leg");
        this.bone = this.right_leg.getChild("bone");
        this.leg = this.bone.getChild("leg");
        this.left_leg = this.flatter.getChild("left_leg");
        this.bone6 = this.left_leg.getChild("bone6");
        this.leg2 = this.bone6.getChild("leg2");
        this.idleAnimation = CursedSpiritWalkingBedAnimations.IDLE.bake(root());
        this.walkAnimation = CursedSpiritWalkingBedAnimations.WALK.bake(root());
        this.attackAnimation = CursedSpiritWalkingBedAnimations.ATTACK.bake(root());
        this.screamAnimation = CursedSpiritWalkingBedAnimations.SCREAMER.bake(root());
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();
        PartDefinition flatter = partdefinition.addOrReplaceChild("flatter", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)24.0f, (float)0.0f));
        PartDefinition body = flatter.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0f, -27.0f, -3.0f, 17.0f, 18.0f, 7.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)-0.5f, (float)-14.0f, (float)4.5f, (float)0.2182f, (float)0.0f, (float)0.0f));
        PartDefinition bone8 = body.addOrReplaceChild("bone8", CubeListBuilder.create().texOffs(0, 25).addBox(-7.0f, 0.0f, -7.0f, 15.0f, 10.0f, 7.0f, new CubeDeformation(0.0f)), PartPose.offset((float)0.0f, (float)-9.0f, (float)4.0f));
        PartDefinition bone2 = body.addOrReplaceChild("bone2", CubeListBuilder.create(), PartPose.offsetAndRotation((float)-3.5f, (float)-21.0f, (float)4.0f, (float)0.1745f, (float)-0.0873f, (float)0.0f));
        PartDefinition bone3 = bone2.addOrReplaceChild("bone3", CubeListBuilder.create().texOffs(0, 49).addBox(-1.5f, -1.5f, -1.0f, 3.0f, 3.0f, 6.0f, new CubeDeformation(0.0f)).texOffs(53, 43).addBox(-1.5f, -1.5f, -12.0f, 3.0f, 3.0f, 6.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)0.0f, (float)0.0f, (float)0.0f, (float)0.0f, (float)0.7854f));
        PartDefinition bone5 = body.addOrReplaceChild("bone5", CubeListBuilder.create(), PartPose.offsetAndRotation((float)4.0f, (float)-15.0f, (float)4.0f, (float)0.0f, (float)0.1745f, (float)0.0f));
        PartDefinition bone4 = bone5.addOrReplaceChild("bone4", CubeListBuilder.create().texOffs(0, 49).addBox(-1.5f, -1.5f, -1.0f, 3.0f, 3.0f, 6.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)0.0f, (float)0.0f, (float)0.0f, (float)0.0f, (float)0.7854f));
        PartDefinition bone9 = body.addOrReplaceChild("bone9", CubeListBuilder.create().texOffs(0, 80).addBox(-2.0f, -2.0f, -2.0f, 4.0f, 5.0f, 3.0f, new CubeDeformation(0.0f)).texOffs(0, 89).addBox(-2.0f, -2.0f, -2.25f, 4.0f, 5.0f, 3.0f, new CubeDeformation(0.0f)), PartPose.offset((float)7.5f, (float)-31.0f, (float)2.0f));
        PartDefinition cube_r1 = bone9.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(14, 80).mirror().addBox(-2.0f, -2.0f, -3.0f, 2.0f, 2.0f, 3.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation((float)0.0f, (float)4.0f, (float)-1.0f, (float)0.0f, (float)0.3054f, (float)0.0f));
        PartDefinition cube_r2 = bone9.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(14, 80).addBox(0.0f, -2.0f, -3.0f, 2.0f, 2.0f, 3.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)4.0f, (float)-1.0f, (float)0.0f, (float)-0.3054f, (float)0.0f));
        PartDefinition left_arm2 = bone9.addOrReplaceChild("left_arm2", CubeListBuilder.create(), PartPose.offset((float)2.0f, (float)2.0f, (float)0.0f));
        PartDefinition cube_r3 = left_arm2.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(14, 85).addBox(0.0f, 1.0f, -1.0f, 2.0f, 3.0f, 2.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)-2.0f, (float)0.0f, (float)0.0f, (float)0.0f, (float)-0.0873f));
        PartDefinition right_arm2 = bone9.addOrReplaceChild("right_arm2", CubeListBuilder.create(), PartPose.offset((float)-2.0f, (float)2.0f, (float)0.0f));
        PartDefinition cube_r4 = right_arm2.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(14, 85).mirror().addBox(-2.0f, 1.0f, -1.0f, 2.0f, 3.0f, 2.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation((float)0.0f, (float)-2.0f, (float)0.0f, (float)0.0f, (float)0.0f, (float)0.0873f));
        PartDefinition head2 = bone9.addOrReplaceChild("head2", CubeListBuilder.create().texOffs(0, 69).addBox(-3.0f, -6.0f, -2.5f, 6.0f, 6.0f, 5.0f, new CubeDeformation(0.0f)).texOffs(1, 96).addBox(-3.0f, -6.0f, -2.5f, 6.0f, 6.0f, 5.0f, new CubeDeformation(0.25f)), PartPose.offsetAndRotation((float)0.0f, (float)-0.5f, (float)-1.5f, (float)-0.2182f, (float)0.0f, (float)0.0f));
        PartDefinition right_arm = body.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset((float)8.5f, (float)-21.5f, (float)1.0f));
        PartDefinition arm5 = right_arm.addOrReplaceChild("arm5", CubeListBuilder.create().texOffs(0, 58).mirror().addBox(0.0f, -2.5f, -3.0f, 5.0f, 5.0f, 6.0f, new CubeDeformation(0.0f)).mirror(false).texOffs(24, 42).mirror().addBox(1.0f, 2.5f, -2.0f, 3.0f, 3.0f, 4.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation((float)0.5f, (float)1.0f, (float)0.0f, (float)-0.1309f, (float)0.0f, (float)-0.2182f));
        PartDefinition cube_r5 = arm5.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(50, 36).mirror().addBox(-3.0f, -3.0f, -2.0f, 3.0f, 3.0f, 4.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation((float)3.0f, (float)-2.5f, (float)0.0f, (float)0.0f, (float)0.0f, (float)-0.7854f));
        PartDefinition arm = arm5.addOrReplaceChild("arm", CubeListBuilder.create().texOffs(18, 49).mirror().addBox(-3.0f, 0.0f, -3.0f, 6.0f, 6.0f, 6.0f, new CubeDeformation(0.0f)).mirror(false).texOffs(8, 42).addBox(-2.0f, 6.0f, -2.0f, 4.0f, 3.0f, 4.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)3.0f, (float)4.5f, (float)0.0f, (float)-0.1309f, (float)0.0f, (float)0.0436f));
        PartDefinition arm2 = arm.addOrReplaceChild("arm2", CubeListBuilder.create().texOffs(48, 18).mirror().addBox(-3.0f, 0.0f, -1.0f, 6.0f, 12.0f, 6.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation((float)0.0f, (float)8.0f, (float)-2.0f, (float)-0.1309f, (float)0.0f, (float)0.0436f));
        PartDefinition left_arm = body.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset((float)-7.5f, (float)-21.5f, (float)1.0f));
        PartDefinition arm3 = left_arm.addOrReplaceChild("arm3", CubeListBuilder.create().texOffs(0, 58).addBox(-5.0f, -2.5f, -3.0f, 5.0f, 5.0f, 6.0f, new CubeDeformation(0.0f)).texOffs(24, 42).addBox(-4.0f, 2.5f, -2.0f, 3.0f, 3.0f, 4.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)-0.5f, (float)1.0f, (float)0.0f, (float)-0.1309f, (float)0.0f, (float)0.2182f));
        PartDefinition cube_r6 = arm3.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(50, 36).addBox(0.0f, -3.0f, -2.0f, 3.0f, 3.0f, 4.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)-3.0f, (float)-2.5f, (float)0.0f, (float)0.0f, (float)0.0f, (float)0.7854f));
        PartDefinition arm4 = arm3.addOrReplaceChild("arm4", CubeListBuilder.create().texOffs(18, 49).addBox(-3.0f, 0.0f, -3.0f, 6.0f, 6.0f, 6.0f, new CubeDeformation(0.0f)).texOffs(8, 42).mirror().addBox(-2.0f, 6.0f, -2.0f, 4.0f, 3.0f, 4.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation((float)-3.0f, (float)4.5f, (float)0.0f, (float)-0.1309f, (float)0.0f, (float)-0.0436f));
        PartDefinition arm6 = arm4.addOrReplaceChild("arm6", CubeListBuilder.create().texOffs(48, 18).addBox(-3.0f, 0.0f, -1.0f, 6.0f, 12.0f, 6.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)8.0f, (float)-2.0f, (float)-0.1309f, (float)0.0f, (float)-0.0436f));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset((float)0.5f, (float)-26.0f, (float)-2.0f));
        PartDefinition bone7 = head.addOrReplaceChild("bone7", CubeListBuilder.create().texOffs(48, 0).addBox(-4.5f, -7.0f, -7.0f, 9.0f, 9.0f, 9.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)0.0f, (float)0.0f, (float)-0.2182f, (float)0.0f, (float)-0.1309f));
        PartDefinition bone14 = bone7.addOrReplaceChild("bone14", CubeListBuilder.create().texOffs(0, 42).addBox(-1.0f, -4.0f, -1.0f, 2.0f, 4.0f, 2.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)3.5f, (float)1.0f, (float)-6.0f, (float)-2.0944f, (float)2.4435f, (float)0.4363f));
        PartDefinition bone15 = bone7.addOrReplaceChild("bone15", CubeListBuilder.create().texOffs(0, 42).addBox(-1.0f, -5.0f, -1.0f, 2.0f, 5.0f, 2.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)-3.25f, (float)-5.5f, (float)-6.0f, (float)1.1781f, (float)0.5236f, (float)0.3054f));
        PartDefinition right_leg = flatter.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset((float)7.0f, (float)-13.25f, (float)5.0f));
        PartDefinition bone = right_leg.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(22, 61).mirror().addBox(-2.5f, -2.0f, -3.0f, 5.0f, 6.0f, 6.0f, new CubeDeformation(0.0f)).mirror(false).texOffs(38, 42).mirror().addBox(-2.0f, 4.0f, -2.0f, 4.0f, 2.0f, 4.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation((float)0.0f, (float)0.0f, (float)0.0f, (float)-0.2618f, (float)0.0f, (float)0.0f));
        PartDefinition leg = bone.addOrReplaceChild("leg", CubeListBuilder.create().texOffs(72, 18).mirror().addBox(-3.0f, 0.0f, -1.0f, 6.0f, 8.0f, 6.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation((float)0.0f, (float)6.0f, (float)-2.0f, (float)0.2618f, (float)0.0f, (float)0.0f));
        PartDefinition left_leg = flatter.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset((float)-7.0f, (float)-13.25f, (float)5.0f));
        PartDefinition bone6 = left_leg.addOrReplaceChild("bone6", CubeListBuilder.create().texOffs(22, 61).addBox(-2.5f, -2.0f, -3.0f, 5.0f, 6.0f, 6.0f, new CubeDeformation(0.0f)).texOffs(38, 42).addBox(-2.0f, 4.0f, -2.0f, 4.0f, 2.0f, 4.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)0.0f, (float)0.0f, (float)-0.2618f, (float)0.0f, (float)0.0f));
        PartDefinition leg2 = bone6.addOrReplaceChild("leg2", CubeListBuilder.create().texOffs(72, 18).addBox(-3.0f, 0.0f, -1.0f, 6.0f, 8.0f, 6.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)6.0f, (float)-2.0f, (float)0.2618f, (float)0.0f, (float)0.0f));
        return LayerDefinition.create((MeshDefinition)meshdefinition, (int)128, (int)128);
    }

    @Override
    public void setupAnim(CursedSpiritRenderState state) {
        root().getAllParts().forEach(ModelPart::resetPose);
        this.head.yRot = state.yRot * 0.017453292f;
        this.head.xRot = state.xRot * 0.017453292f;
CursedSpiritClips.apply(state, this.screamAnimation, this.attackAnimation, this.idleAnimation, this.walkAnimation, 2.5f, 4.5f);
    }
}
