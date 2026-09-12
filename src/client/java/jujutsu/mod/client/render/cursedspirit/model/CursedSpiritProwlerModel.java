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
import jujutsu.mod.client.render.cursedspirit.anim.CursedSpiritProwlerAnimations;

public class CursedSpiritProwlerModel extends EntityModel<CursedSpiritRenderState> {
    public final ModelPart all;
    public final ModelPart flatter;
    public final ModelPart body;
    public final ModelPart in_body;
    public final ModelPart bone2;
    public final ModelPart bone3;
    public final ModelPart in_left_leg;
    public final ModelPart in_right_leg;
    public final ModelPart head;
    public final ModelPart bone;
    public final ModelPart right_arm;
    public final ModelPart bone4;
    public final ModelPart left_arm;

    private final KeyframeAnimation idleAnimation;
    private final KeyframeAnimation walkAnimation;
    private final KeyframeAnimation attackAnimation;
    private final KeyframeAnimation screamAnimation;

    public CursedSpiritProwlerModel(ModelPart root) {
        super(root);
        this.all = root.getChild("all");
        this.flatter = this.all.getChild("flatter");
        this.body = this.flatter.getChild("body");
        this.in_body = this.body.getChild("in_body");
        this.bone2 = this.in_body.getChild("bone2");
        this.bone3 = this.in_body.getChild("bone3");
        this.in_left_leg = this.body.getChild("in_left_leg");
        this.in_right_leg = this.body.getChild("in_right_leg");
        this.head = this.body.getChild("head");
        this.bone = this.head.getChild("bone");
        this.right_arm = this.body.getChild("right_arm");
        this.bone4 = this.right_arm.getChild("bone4");
        this.left_arm = this.body.getChild("left_arm");
        this.idleAnimation = CursedSpiritProwlerAnimations.IDLE.bake(root());
        this.walkAnimation = CursedSpiritProwlerAnimations.WALK.bake(root());
        this.attackAnimation = CursedSpiritProwlerAnimations.ATTACK.bake(root());
        this.screamAnimation = CursedSpiritProwlerAnimations.SCREAMER.bake(root());
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();
        PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)24.0f, (float)0.0f));
        PartDefinition flatter = all.addOrReplaceChild("flatter", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)0.0f, (float)0.0f));
        PartDefinition body = flatter.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset((float)-0.5f, (float)-11.0f, (float)-6.0f));
        PartDefinition in_body = body.addOrReplaceChild("in_body", CubeListBuilder.create().texOffs(0, 18).addBox(-4.0f, 0.0f, -2.0f, 9.0f, 12.0f, 5.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)1.5f, (float)-0.5f, (float)1.309f, (float)0.0f, (float)0.0f));
        PartDefinition bone2 = in_body.addOrReplaceChild("bone2", CubeListBuilder.create(), PartPose.offsetAndRotation((float)2.0f, (float)3.0f, (float)3.0f, (float)-1.5708f, (float)0.0f, (float)0.0f));
        PartDefinition cube_r1 = bone2.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(0, 39).addBox(-1.0f, -2.5f, -1.0f, 2.0f, 5.0f, 2.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)-1.5f, (float)0.0f, (float)0.0f, (float)-0.7854f, (float)0.0f));
        PartDefinition bone3 = in_body.addOrReplaceChild("bone3", CubeListBuilder.create(), PartPose.offsetAndRotation((float)-2.0f, (float)8.0f, (float)1.0f, (float)-1.5708f, (float)0.0f, (float)0.0f));
        PartDefinition cube_r2 = bone3.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(0, 39).addBox(-1.0f, -2.5f, -1.0f, 2.0f, 3.0f, 2.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)-1.5f, (float)0.0f, (float)0.0f, (float)-0.7854f, (float)0.0f));
        PartDefinition in_left_leg = body.addOrReplaceChild("in_left_leg", CubeListBuilder.create().texOffs(44, 0).addBox(-2.5f, 0.0f, -2.5f, 5.0f, 13.0f, 5.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)-2.0f, (float)4.5f, (float)11.0f, (float)1.1345f, (float)0.0f, (float)0.2182f));
        PartDefinition in_right_leg = body.addOrReplaceChild("in_right_leg", CubeListBuilder.create().texOffs(44, 18).mirror().addBox(-2.5f, 0.0f, -2.5f, 5.0f, 13.0f, 5.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation((float)3.0f, (float)7.5f, (float)11.0f, (float)1.4835f, (float)0.0f, (float)-0.2182f));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0f, -8.0f, -5.0f, 9.0f, 9.0f, 9.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)1.0f, (float)-1.0f, (float)0.0f, (float)0.0f, (float)0.1309f));
        PartDefinition bone = head.addOrReplaceChild("bone", CubeListBuilder.create(), PartPose.offsetAndRotation((float)-4.0f, (float)-5.0f, (float)-2.0f, (float)0.0f, (float)0.0f, (float)1.6581f));
        PartDefinition cube_r3 = bone.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(0, 39).addBox(-1.0f, -2.5f, -1.0f, 2.0f, 7.0f, 2.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)-1.5f, (float)0.0f, (float)0.0f, (float)-0.7854f, (float)0.0f));
        PartDefinition right_arm = body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(45, 36).mirror().addBox(0.0f, -2.0f, -2.0f, 4.0f, 13.0f, 5.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation((float)5.0f, (float)2.0f, (float)2.0f, (float)0.0f, (float)0.0f, (float)-0.6109f));
        PartDefinition bone4 = right_arm.addOrReplaceChild("bone4", CubeListBuilder.create(), PartPose.offsetAndRotation((float)2.0f, (float)4.5f, (float)0.5f, (float)-1.3963f, (float)0.6109f, (float)0.0f));
        PartDefinition cube_r4 = bone4.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(0, 39).addBox(-1.0f, -3.5f, -1.0f, 2.0f, 6.0f, 2.0f, new CubeDeformation(0.0f)).texOffs(0, 39).addBox(-1.0f, -0.5f, -1.0f, 2.0f, 7.0f, 2.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)-1.5f, (float)0.0f, (float)0.0f, (float)-0.4363f, (float)0.0f));
        PartDefinition left_arm = body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(45, 36).addBox(-4.0f, -2.0f, -2.0f, 4.0f, 13.0f, 5.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)-4.0f, (float)2.0f, (float)2.0f, (float)0.0f, (float)0.0f, (float)0.6109f));
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
