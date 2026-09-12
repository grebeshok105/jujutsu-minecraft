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
import jujutsu.mod.client.render.cursedspirit.anim.CursedSpiritGulberAnimations;

public class CursedSpiritGulberModel extends EntityModel<CursedSpiritRenderState> {
    public final ModelPart all;
    public final ModelPart body;
    public final ModelPart body2;
    public final ModelPart right_foot;
    public final ModelPart left_foot;
    public final ModelPart right_foot2;
    public final ModelPart left_foot2;
    public final ModelPart head;
    public final ModelPart bone;

    private final KeyframeAnimation idleAnimation;
    private final KeyframeAnimation walkAnimation;
    private final KeyframeAnimation attackAnimation;

    public CursedSpiritGulberModel(ModelPart root) {
        super(root);
        this.all = root.getChild("all");
        this.body = this.all.getChild("body");
        this.body2 = this.body.getChild("body2");
        this.right_foot = this.body.getChild("right_foot");
        this.left_foot = this.body.getChild("left_foot");
        this.right_foot2 = this.body.getChild("right_foot2");
        this.left_foot2 = this.body.getChild("left_foot2");
        this.head = this.body.getChild("head");
        this.bone = this.head.getChild("bone");
        this.idleAnimation = CursedSpiritGulberAnimations.IDLE.bake(root());
        this.walkAnimation = CursedSpiritGulberAnimations.WALK.bake(root());
        this.attackAnimation = CursedSpiritGulberAnimations.ATTACK.bake(root());
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();
        PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)24.0f, (float)0.0f));
        PartDefinition body = all.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)-4.0f, (float)0.0f));
        PartDefinition body2 = body.addOrReplaceChild("body2", CubeListBuilder.create().texOffs(36, 0).addBox(-3.0f, -1.0f, -5.0f, 6.0f, 5.0f, 8.0f, new CubeDeformation(0.0f)).texOffs(36, 21).addBox(-2.0f, -2.0f, -2.0f, 4.0f, 1.0f, 5.0f, new CubeDeformation(0.0f)), PartPose.offset((float)0.0f, (float)-4.0f, (float)1.0f));
        PartDefinition right_foot = body.addOrReplaceChild("right_foot", CubeListBuilder.create().texOffs(36, 13).mirror().addBox(-2.0f, 0.0f, -2.0f, 4.0f, 4.0f, 4.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offset((float)3.0f, (float)0.0f, (float)-3.0f));
        PartDefinition left_foot = body.addOrReplaceChild("left_foot", CubeListBuilder.create().texOffs(36, 13).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 4.0f, 4.0f, new CubeDeformation(0.0f)), PartPose.offset((float)-3.0f, (float)0.0f, (float)-3.0f));
        PartDefinition right_foot2 = body.addOrReplaceChild("right_foot2", CubeListBuilder.create().texOffs(36, 13).mirror().addBox(-2.0f, 0.0f, -2.0f, 4.0f, 4.0f, 4.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offset((float)3.0f, (float)0.0f, (float)3.0f));
        PartDefinition left_foot2 = body.addOrReplaceChild("left_foot2", CubeListBuilder.create().texOffs(36, 13).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 4.0f, 4.0f, new CubeDeformation(0.0f)), PartPose.offset((float)-3.0f, (float)0.0f, (float)3.0f));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0f, -3.0f, -5.0f, 10.0f, 5.0f, 8.0f, new CubeDeformation(0.0f)).texOffs(0, 26).addBox(-5.0f, -1.0f, -5.0f, 10.0f, 1.0f, 8.0f, new CubeDeformation(-0.01f)), PartPose.offset((float)0.0f, (float)-5.1f, (float)-4.0f));
        PartDefinition bone = head.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(0, 13).addBox(-5.0f, -2.0f, -8.0f, 10.0f, 5.0f, 8.0f, new CubeDeformation(-0.02f)).texOffs(0, 35).addBox(-5.0f, 0.0f, -8.0f, 10.0f, 0.0f, 8.0f, new CubeDeformation(-0.03f)), PartPose.offset((float)0.0f, (float)-1.0f, (float)3.0f));
        return LayerDefinition.create((MeshDefinition)meshdefinition, (int)64, (int)64);
    }

    @Override
    public void setupAnim(CursedSpiritRenderState state) {
        root().getAllParts().forEach(ModelPart::resetPose);
        this.head.yRot = state.yRot * 0.017453292f;
        this.head.xRot = state.xRot * 0.017453292f;
CursedSpiritClips.apply(state, null, this.attackAnimation, this.idleAnimation, this.walkAnimation, 2.5f, 4.0f);
    }
}
