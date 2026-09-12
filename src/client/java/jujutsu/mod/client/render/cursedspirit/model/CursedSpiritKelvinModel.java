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
import jujutsu.mod.client.render.cursedspirit.anim.CursedSpiritKelvinAnimations;

public class CursedSpiritKelvinModel extends EntityModel<CursedSpiritRenderState> {
    public final ModelPart all;
    public final ModelPart flater;
    public final ModelPart body;
    public final ModelPart rightarm;
    public final ModelPart bone4;
    public final ModelPart bone5;
    public final ModelPart bone6;
    public final ModelPart bone7;
    public final ModelPart bone11;
    public final ModelPart leftarm;
    public final ModelPart bone12;
    public final ModelPart bone13;
    public final ModelPart head;
    public final ModelPart bone3;
    public final ModelPart bone8;
    public final ModelPart right_leg;
    public final ModelPart bone;
    public final ModelPart bone2;
    public final ModelPart left_leg;

    private final KeyframeAnimation idleAnimation;
    private final KeyframeAnimation walkAnimation;
    private final KeyframeAnimation attackAnimation;
    private final KeyframeAnimation screamAnimation;

    public CursedSpiritKelvinModel(ModelPart root) {
        super(root);
        this.all = root.getChild("all");
        this.flater = this.all.getChild("flater");
        this.body = this.flater.getChild("body");
        this.rightarm = this.body.getChild("rightarm");
        this.bone4 = this.rightarm.getChild("bone4");
        this.bone5 = this.bone4.getChild("bone5");
        this.bone6 = this.bone4.getChild("bone6");
        this.bone7 = this.bone6.getChild("bone7");
        this.bone11 = this.bone7.getChild("bone11");
        this.leftarm = this.body.getChild("leftarm");
        this.bone12 = this.leftarm.getChild("bone12");
        this.bone13 = this.bone12.getChild("bone13");
        this.head = this.body.getChild("head");
        this.bone3 = this.head.getChild("bone3");
        this.bone8 = this.head.getChild("bone8");
        this.right_leg = this.flater.getChild("right_leg");
        this.bone = this.right_leg.getChild("bone");
        this.bone2 = this.bone.getChild("bone2");
        this.left_leg = this.flater.getChild("left_leg");
        this.idleAnimation = CursedSpiritKelvinAnimations.IDLE.bake(root());
        this.walkAnimation = CursedSpiritKelvinAnimations.WALK.bake(root());
        this.attackAnimation = CursedSpiritKelvinAnimations.ATTACK.bake(root());
        this.screamAnimation = CursedSpiritKelvinAnimations.SCREAMER.bake(root());
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();
        PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)24.0f, (float)0.0f));
        PartDefinition flater = all.addOrReplaceChild("flater", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)0.0f, (float)0.0f));
        PartDefinition body = flater.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3.9f, -11.0f, -2.0f, 8.0f, 11.0f, 4.0f, new CubeDeformation(0.0f)).texOffs(44, 41).addBox(-4.9f, -12.0f, -3.0f, 4.0f, 5.0f, 6.0f, new CubeDeformation(0.0f)).texOffs(16, 24).addBox(0.1f, -7.0f, -2.0f, 4.0f, 3.0f, 4.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)-0.1f, (float)-12.0f, (float)0.0f, (float)0.0873f, (float)0.0f, (float)-0.0175f));
        PartDefinition cube_r1 = body.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(48, 52).addBox(-1.0f, -2.0f, -1.0f, 2.0f, 4.0f, 2.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.1f, (float)-5.0f, (float)0.0f, (float)0.0f, (float)-0.7854f, (float)0.0f));
        PartDefinition rightarm = body.addOrReplaceChild("rightarm", CubeListBuilder.create().texOffs(0, 57).addBox(-2.5f, -1.0f, -1.0f, 2.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)-4.0f, (float)-9.5f, (float)0.0f, (float)-0.0873f, (float)0.0f, (float)0.0f));
        PartDefinition bone4 = rightarm.addOrReplaceChild("bone4", CubeListBuilder.create().texOffs(48, 58).addBox(-1.25f, 1.0f, -1.0f, 2.0f, 4.0f, 2.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)-2.25f, (float)0.0f, (float)0.0f, (float)0.0f, (float)0.0f, (float)0.1309f));
        PartDefinition bone5 = bone4.addOrReplaceChild("bone5", CubeListBuilder.create().texOffs(8, 57).addBox(-1.0858f, -0.7071f, -1.5f, 2.0f, 2.0f, 3.0f, new CubeDeformation(0.1f)).texOffs(32, 24).addBox(-2.0858f, -1.7071f, -2.0f, 1.0f, 4.0f, 4.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)0.0f, (float)0.0f, (float)0.0f, (float)0.0f, (float)0.7854f));
        PartDefinition bone6 = bone4.addOrReplaceChild("bone6", CubeListBuilder.create().texOffs(18, 56).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 2.0f, 4.0f, new CubeDeformation(0.0f)).texOffs(56, 59).addBox(-1.0f, 2.0f, -1.0f, 2.0f, 3.0f, 2.0f, new CubeDeformation(0.0f)), PartPose.offset((float)-0.25f, (float)4.0f, (float)0.05f));
        PartDefinition bone7 = bone6.addOrReplaceChild("bone7", CubeListBuilder.create().texOffs(0, 49).addBox(-2.0f, -0.2847f, -2.2745f, 4.0f, 4.0f, 4.0f, new CubeDeformation(0.0f)), PartPose.offset((float)0.0f, (float)5.0f, (float)0.25f));
        PartDefinition bone11 = bone7.addOrReplaceChild("bone11", CubeListBuilder.create().texOffs(18, 17).addBox(-1.0f, -0.5f, 0.0f, 2.0f, 5.0f, 0.0f, new CubeDeformation(0.0f)), PartPose.offset((float)0.0f, (float)4.2153f, (float)-0.2745f));
        PartDefinition leftarm = body.addOrReplaceChild("leftarm", CubeListBuilder.create().texOffs(0, 57).mirror().addBox(0.5f, -1.0f, -1.0f, 2.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offset((float)3.2f, (float)-9.5f, (float)0.0f));
        PartDefinition bone12 = leftarm.addOrReplaceChild("bone12", CubeListBuilder.create().texOffs(48, 58).mirror().addBox(-0.75f, 1.0f, -1.0f, 2.0f, 4.0f, 2.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation((float)2.25f, (float)0.0f, (float)0.0f, (float)0.0f, (float)0.0f, (float)-0.1309f));
        PartDefinition bone13 = bone12.addOrReplaceChild("bone13", CubeListBuilder.create().texOffs(8, 57).mirror().addBox(-0.9142f, -0.7071f, -1.5f, 2.0f, 2.0f, 3.0f, new CubeDeformation(0.1f)).mirror(false).texOffs(32, 24).mirror().addBox(1.0858f, -1.7071f, -2.0f, 1.0f, 4.0f, 4.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation((float)0.0f, (float)0.0f, (float)0.0f, (float)0.0f, (float)0.0f, (float)-0.7854f));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation((float)0.1f, (float)-11.0f, (float)-0.25f, (float)-0.0436f, (float)0.0436f, (float)0.0698f));
        PartDefinition bone3 = head.addOrReplaceChild("bone3", CubeListBuilder.create().texOffs(24, 0).addBox(-4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 4.0f, new CubeDeformation(0.0f)).texOffs(0, 31).addBox(-5.0f, -7.0f, -5.0f, 4.0f, 4.0f, 4.0f, new CubeDeformation(0.0f)).texOffs(0, 39).addBox(-5.0f, -3.0f, -5.0f, 3.0f, 2.0f, 3.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)0.0f, (float)0.0f, (float)0.0436f, (float)0.0873f, (float)-0.0087f));
        PartDefinition bone8 = head.addOrReplaceChild("bone8", CubeListBuilder.create().texOffs(24, 12).addBox(-4.0f, -8.0f, 0.0f, 8.0f, 8.0f, 4.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)0.0f, (float)0.0f, (float)-0.1745f, (float)0.0f, (float)0.0436f));
        PartDefinition right_leg = flater.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(56, 52).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 5.0f, 2.0f, new CubeDeformation(0.0f)).texOffs(0, 44).addBox(-1.5f, 4.0f, -1.5f, 3.0f, 2.0f, 3.0f, new CubeDeformation(0.0f)), PartPose.offset((float)-1.75f, (float)-12.0f, (float)0.0f));
        PartDefinition bone = right_leg.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(56, 52).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 5.0f, 2.0f, new CubeDeformation(0.0f)), PartPose.offset((float)0.0f, (float)6.0f, (float)0.0f));
        PartDefinition bone2 = bone.addOrReplaceChild("bone2", CubeListBuilder.create().texOffs(12, 42).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 2.0f, 4.0f, new CubeDeformation(0.0f)), PartPose.offset((float)0.0f, (float)4.0f, (float)0.0f));
        PartDefinition left_leg = flater.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 15).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)2.0f, (float)-12.0f, (float)0.0f, (float)0.0f, (float)0.0f, (float)-0.0873f));
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
