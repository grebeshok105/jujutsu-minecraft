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
import jujutsu.mod.client.render.cursedspirit.anim.CursedSpiritGuzzlerAnimations;

public class CursedSpiritGuzzlerModel extends EntityModel<CursedSpiritRenderState> {
    public final ModelPart all;
    public final ModelPart body2;
    public final ModelPart body;
    public final ModelPart jaw;
    public final ModelPart right_arm;
    public final ModelPart left_arm;
    public final ModelPart right_leg;
    public final ModelPart left_leg;

    private final KeyframeAnimation idleAnimation;
    private final KeyframeAnimation walkAnimation;
    private final KeyframeAnimation attackAnimation;

    public CursedSpiritGuzzlerModel(ModelPart root) {
        super(root);
        this.all = root.getChild("all");
        this.body2 = this.all.getChild("body2");
        this.body = this.body2.getChild("body");
        this.jaw = this.body.getChild("jaw");
        this.right_arm = this.body2.getChild("right_arm");
        this.left_arm = this.all.getChild("left_arm");
        this.right_leg = this.all.getChild("right_leg");
        this.left_leg = this.all.getChild("left_leg");
        this.idleAnimation = CursedSpiritGuzzlerAnimations.IDLE.bake(root());
        this.walkAnimation = CursedSpiritGuzzlerAnimations.WALK.bake(root());
        this.attackAnimation = CursedSpiritGuzzlerAnimations.ATTACK.bake(root());
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();
        PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)24.0f, (float)0.0f));
        PartDefinition body2 = all.addOrReplaceChild("body2", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)-13.0f, (float)0.0f));
        PartDefinition body = body2.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0f, -9.0f, -3.0f, 8.0f, 13.0f, 6.0f, new CubeDeformation(0.0f)).texOffs(40, 0).addBox(-3.0f, -5.0f, 3.0f, 6.0f, 6.0f, 1.0f, new CubeDeformation(0.0f)).texOffs(-6, 46).addBox(-4.0f, -8.0f, -3.0f, 8.0f, 0.0f, 6.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)-4.0f, (float)0.0f, (float)0.2182f, (float)0.0f, (float)0.0f));
        PartDefinition jaw = body.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 19).addBox(-4.0f, -5.0f, -6.0f, 8.0f, 6.0f, 6.0f, new CubeDeformation(0.01f)).texOffs(-6, 52).addBox(-4.0f, -1.0f, -6.0f, 8.0f, 0.0f, 6.0f, new CubeDeformation(0.0f)).texOffs(8, 53).addBox(-4.0f, -1.0f, -1.0f, 8.0f, 1.0f, 0.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)-7.0f, (float)3.0f, (float)-0.5672f, (float)0.0f, (float)0.0f));
        PartDefinition right_arm = body2.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(28, 0).mirror().addBox(0.0f, -1.0f, -2.0f, 2.0f, 13.0f, 4.0f, new CubeDeformation(0.0f)).mirror(false).texOffs(24, 47).mirror().addBox(0.0f, 12.0f, -2.0f, 2.0f, 1.0f, 4.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation((float)4.0f, (float)-4.0f, (float)0.0f, (float)0.0f, (float)0.0f, (float)-0.0873f));
        PartDefinition left_arm = all.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(24, 27).addBox(-2.0f, -1.0f, -2.0f, 2.0f, 16.0f, 4.0f, new CubeDeformation(0.0f)).texOffs(24, 47).addBox(-2.0f, 15.0f, -2.0f, 2.0f, 1.0f, 4.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)-4.0f, (float)-18.0f, (float)0.0f, (float)0.0f, (float)0.0f, (float)0.0873f));
        PartDefinition right_leg = all.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 31).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 13.0f, 2.0f, new CubeDeformation(0.0f)), PartPose.offset((float)2.0f, (float)-13.0f, (float)0.0f));
        PartDefinition left_leg = all.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 31).mirror().addBox(-1.0f, 0.0f, -1.0f, 2.0f, 13.0f, 2.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offset((float)-2.0f, (float)-13.0f, (float)0.0f));
        return LayerDefinition.create((MeshDefinition)meshdefinition, (int)64, (int)64);
    }

    @Override
    public void setupAnim(CursedSpiritRenderState state) {
        root().getAllParts().forEach(ModelPart::resetPose);
CursedSpiritClips.apply(state, null, this.attackAnimation, this.idleAnimation, this.walkAnimation, 2.0f, 4.0f);
    }
}
