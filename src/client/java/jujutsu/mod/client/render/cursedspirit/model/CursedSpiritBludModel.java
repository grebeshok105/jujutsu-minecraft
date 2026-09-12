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
import jujutsu.mod.client.render.cursedspirit.anim.CursedSpiritBludAnimations;

public class CursedSpiritBludModel extends EntityModel<CursedSpiritRenderState> {
    public final ModelPart all;
    public final ModelPart flater;
    public final ModelPart chest;
    public final ModelPart bone5;
    public final ModelPart bone6;
    public final ModelPart right_arm;
    public final ModelPart bone;
    public final ModelPart left_arm;
    public final ModelPart bone2;
    public final ModelPart head;
    public final ModelPart bone4;
    public final ModelPart bone3;
    public final ModelPart stain;

    private final KeyframeAnimation idleAnimation;
    private final KeyframeAnimation walkAnimation;
    private final KeyframeAnimation attackAnimation;
    private final KeyframeAnimation screamAnimation;

    public CursedSpiritBludModel(ModelPart root) {
        super(root);
        this.all = root.getChild("all");
        this.flater = this.all.getChild("flater");
        this.chest = this.flater.getChild("chest");
        this.bone5 = this.chest.getChild("bone5");
        this.bone6 = this.bone5.getChild("bone6");
        this.right_arm = this.chest.getChild("right_arm");
        this.bone = this.right_arm.getChild("bone");
        this.left_arm = this.chest.getChild("left_arm");
        this.bone2 = this.left_arm.getChild("bone2");
        this.head = this.chest.getChild("head");
        this.bone4 = this.head.getChild("bone4");
        this.bone3 = this.bone4.getChild("bone3");
        this.stain = this.all.getChild("stain");
        this.idleAnimation = CursedSpiritBludAnimations.IDLE.bake(root());
        this.walkAnimation = CursedSpiritBludAnimations.WALK.bake(root());
        this.attackAnimation = CursedSpiritBludAnimations.ATTACK.bake(root());
        this.screamAnimation = CursedSpiritBludAnimations.SCREAMER.bake(root());
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();
        PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)24.0f, (float)0.0f));
        PartDefinition flater = all.addOrReplaceChild("flater", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)0.0f, (float)0.0f));
        PartDefinition chest = flater.addOrReplaceChild("chest", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)-7.0f, (float)-1.0f));
        PartDefinition bone5 = chest.addOrReplaceChild("bone5", CubeListBuilder.create().texOffs(36, 0).addBox(-4.5f, -12.0f, -2.0f, 9.0f, 10.0f, 5.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)10.0f, (float)2.0f, (float)0.2182f, (float)0.0f, (float)0.0f));
        PartDefinition bone6 = bone5.addOrReplaceChild("bone6", CubeListBuilder.create().texOffs(44, 53).addBox(-3.5f, 0.0f, -1.0f, 7.0f, 8.0f, 3.0f, new CubeDeformation(0.0f)), PartPose.offset((float)0.0f, (float)-10.0f, (float)0.0f));
        PartDefinition right_arm = chest.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset((float)4.0f, (float)1.0f, (float)-1.0f));
        PartDefinition bone = right_arm.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(0, 18).addBox(0.5f, -3.0f, -12.0f, 4.0f, 5.0f, 13.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)-0.5f, (float)0.0f, (float)1.0f, (float)-0.1309f, (float)-0.4363f, (float)0.0f));
        PartDefinition cube_r1 = bone.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(0, 40).addBox(-2.5f, -1.0f, -2.0f, 5.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)3.8536f, (float)-3.3536f, (float)-3.0f, (float)0.7854f, (float)0.0f, (float)-0.7854f));
        PartDefinition left_arm = chest.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset((float)-3.5f, (float)1.0f, (float)-1.0f));
        PartDefinition bone2 = left_arm.addOrReplaceChild("bone2", CubeListBuilder.create().texOffs(0, 18).mirror().addBox(-4.5f, -3.0f, -12.0f, 4.0f, 5.0f, 13.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation((float)0.0f, (float)0.0f, (float)1.0f, (float)-0.1309f, (float)0.4363f, (float)0.0f));
        PartDefinition head = chest.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)-2.0f, (float)0.0f));
        PartDefinition bone4 = head.addOrReplaceChild("bone4", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5f, -7.5f, -5.5f, 9.0f, 9.0f, 9.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)-0.9991f, (float)-0.0436f, (float)0.0f, (float)0.0f, (float)0.2182f));
        PartDefinition bone3 = bone4.addOrReplaceChild("bone3", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)1.0f, (float)-1.0f));
        PartDefinition cube_r2 = bone3.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(0, 40).mirror().addBox(-2.5f, -2.25f, -0.5f, 5.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation((float)-4.0469f, (float)-2.9573f, (float)1.0829f, (float)0.1309f, (float)0.1745f, (float)-0.1745f));
        PartDefinition cube_r3 = bone3.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(0, 36).addBox(-3.5f, -1.0f, -1.0f, 7.0f, 2.0f, 2.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)3.6459f, (float)-7.5853f, (float)-3.6149f, (float)-0.3054f, (float)0.7854f, (float)-0.9163f));
        PartDefinition stain = all.addOrReplaceChild("stain", CubeListBuilder.create().texOffs(-20, 44).addBox(-10.0f, -0.025f, -10.0f, 20.0f, 0.0f, 20.0f, new CubeDeformation(0.0f)), PartPose.offset((float)0.0f, (float)0.0f, (float)0.0f));
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
