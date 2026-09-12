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
import jujutsu.mod.client.render.cursedspirit.anim.CursedSpiritButcherAnimations;

public class CursedSpiritButcherModel extends EntityModel<CursedSpiritRenderState> {
    public final ModelPart all;
    public final ModelPart flater;
    public final ModelPart body;
    public final ModelPart in_body;
    public final ModelPart left_arm;
    public final ModelPart bone7;
    public final ModelPart right_arm;
    public final ModelPart head;
    public final ModelPart bone;
    public final ModelPart bone9;
    public final ModelPart left_leg;
    public final ModelPart right_leg;

    private final KeyframeAnimation idleAnimation;
    private final KeyframeAnimation walkAnimation;
    private final KeyframeAnimation attackAnimation;
    private final KeyframeAnimation screamAnimation;

    public CursedSpiritButcherModel(ModelPart root) {
        super(root);
        this.all = root.getChild("all");
        this.flater = this.all.getChild("flater");
        this.body = this.flater.getChild("body");
        this.in_body = this.body.getChild("in_body");
        this.left_arm = this.body.getChild("left_arm");
        this.bone7 = this.left_arm.getChild("bone7");
        this.right_arm = this.body.getChild("right_arm");
        this.head = this.body.getChild("head");
        this.bone = this.head.getChild("bone");
        this.bone9 = this.head.getChild("bone9");
        this.left_leg = this.flater.getChild("left_leg");
        this.right_leg = this.flater.getChild("right_leg");
        this.idleAnimation = CursedSpiritButcherAnimations.IDLE.bake(root());
        this.walkAnimation = CursedSpiritButcherAnimations.WALK.bake(root());
        this.attackAnimation = CursedSpiritButcherAnimations.ATTACK.bake(root());
        this.screamAnimation = CursedSpiritButcherAnimations.SCREAMER.bake(root());
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();
        PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)24.0f, (float)0.0f));
        PartDefinition flater = all.addOrReplaceChild("flater", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)0.0f, (float)0.0f));
        PartDefinition body = flater.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset((float)0.5f, (float)-12.0f, (float)2.0f));
        PartDefinition in_body = body.addOrReplaceChild("in_body", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0f, -14.0f, -3.0f, 9.0f, 14.0f, 5.0f, new CubeDeformation(0.0f)).texOffs(0, 51).addBox(-4.0f, -6.0f, -4.0f, 7.0f, 3.0f, 1.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)0.0f, (float)0.0f, (float)0.1745f, (float)0.0f, (float)0.0436f));
        PartDefinition left_arm = body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(28, 0).addBox(-4.0f, -2.5f, -2.5f, 4.0f, 13.0f, 5.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)-5.0f, (float)-10.5f, (float)-4.0f, (float)-1.0472f, (float)0.1309f, (float)0.2618f));
        PartDefinition bone7 = left_arm.addOrReplaceChild("bone7", CubeListBuilder.create(), PartPose.offsetAndRotation((float)-1.25f, (float)-0.25f, (float)0.0f, (float)-0.7418f, (float)-0.6109f, (float)-2.4871f));
        PartDefinition cube_r1 = bone7.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(0, 36).addBox(1.0f, 5.0f, -1.0f, 2.0f, 6.0f, 2.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)12.0f, (float)-2.0f, (float)0.0f, (float)0.0f, (float)0.0f, (float)1.5708f));
        PartDefinition right_arm = body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(8, 36).mirror().addBox(-1.0f, -2.5f, -2.5f, 4.0f, 4.0f, 5.0f, new CubeDeformation(0.0f)).mirror(false).texOffs(0, 45).mirror().addBox(-0.5f, 6.5f, -2.0f, 3.0f, 2.0f, 4.0f, new CubeDeformation(0.0f)).mirror(false).texOffs(46, 0).mirror().addBox(0.0f, -1.5f, -1.5f, 2.0f, 11.0f, 3.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation((float)5.0f, (float)-11.5f, (float)-3.5f, (float)0.0f, (float)0.4363f, (float)-0.1745f));
        PartDefinition cube_r2 = right_arm.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(56, 14).mirror().addBox(-0.5f, 3.0f, -4.5f, 1.0f, 8.0f, 3.0f, new CubeDeformation(0.0f)).mirror(false).texOffs(56, 0).mirror().addBox(-0.5f, 0.0f, -1.5f, 1.0f, 11.0f, 3.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation((float)1.0f, (float)9.5f, (float)0.0f, (float)0.0f, (float)0.0873f, (float)0.0f));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)-13.5f, (float)-2.5f));
        PartDefinition cube_r3 = head.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(36, 18).addBox(0.0f, -9.0f, -4.0f, 5.0f, 9.0f, 9.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)0.0f, (float)-2.0f, (float)0.0f, (float)0.0f, (float)0.1309f));
        PartDefinition bone = head.addOrReplaceChild("bone", CubeListBuilder.create(), PartPose.offset((float)-1.0f, (float)0.0f, (float)-2.0f));
        PartDefinition cube_r4 = bone.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(36, 36).addBox(-4.0f, -9.0f, -4.0f, 5.0f, 9.0f, 9.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)0.0f, (float)0.0f, (float)0.0f, (float)0.0f, (float)-0.2618f));
        PartDefinition bone9 = head.addOrReplaceChild("bone9", CubeListBuilder.create(), PartPose.offsetAndRotation((float)4.5f, (float)-6.5f, (float)-4.5f, (float)0.3491f, (float)0.5236f, (float)-0.5672f));
        PartDefinition cube_r5 = bone9.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(0, 36).addBox(-8.25f, -5.0f, -4.0f, 2.0f, 7.0f, 2.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)1.25f, (float)7.25f, (float)3.0f, (float)0.0f, (float)0.0f, (float)1.5708f));
        PartDefinition left_leg = flater.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 19).mirror().addBox(-2.5f, 0.0f, -1.0f, 5.0f, 12.0f, 5.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offset((float)-2.0f, (float)-12.0f, (float)0.0f));
        PartDefinition right_leg = flater.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 19).addBox(-2.5f, 0.0f, -1.0f, 5.0f, 12.0f, 5.0f, new CubeDeformation(0.0f)), PartPose.offset((float)2.0f, (float)-12.0f, (float)0.0f));
        return LayerDefinition.create((MeshDefinition)meshdefinition, (int)64, (int)64);
    }

    @Override
    public void setupAnim(CursedSpiritRenderState state) {
        root().getAllParts().forEach(ModelPart::resetPose);
        this.head.yRot = state.yRot * 0.017453292f;
        this.head.xRot = state.xRot * 0.017453292f;
CursedSpiritClips.apply(state, this.screamAnimation, this.attackAnimation, this.idleAnimation, this.walkAnimation, 2.5f, 4.5f);
    }
}
