package jujutsu.mod.client.render.cursedspirit.model;

// Ported from the asset pack's vanilla model class: part hierarchy and createBodyLayer()
// are verbatim; only the hierarchy target changed (EntityModel, 1.21.8).
// Source creature: the pack's curse ghost body (see block-3 report for the port-map note).
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
import jujutsu.mod.client.render.cursedspirit.anim.CursedSpiritFloatingCurseAnimations;

public class CursedSpiritFloatingCurseModel extends EntityModel<CursedSpiritRenderState> {
    public final ModelPart all;
    public final ModelPart flater;
    public final ModelPart body;
    public final ModelPart head;
    public final ModelPart bone;
    public final ModelPart spine;

    private final KeyframeAnimation idleAnimation;
    private final KeyframeAnimation attackAnimation;
    private final KeyframeAnimation screamAnimation;

    public CursedSpiritFloatingCurseModel(ModelPart root) {
        super(root);
        this.all = root.getChild("all");
        this.flater = this.all.getChild("flater");
        this.body = this.flater.getChild("body");
        this.head = this.body.getChild("head");
        this.bone = this.head.getChild("bone");
        this.spine = this.body.getChild("spine");
        this.idleAnimation = CursedSpiritFloatingCurseAnimations.IDLE.bake(root());
        this.attackAnimation = CursedSpiritFloatingCurseAnimations.ATTACK.bake(root());
        this.screamAnimation = CursedSpiritFloatingCurseAnimations.SCREAMER.bake(root());
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();
        PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)24.0f, (float)0.0f));
        PartDefinition flater = all.addOrReplaceChild("flater", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)0.0f, (float)0.0f));
        PartDefinition body = flater.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)-22.25f, (float)1.5f));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)0.25f, (float)-0.5f));
        PartDefinition bone = head.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5f, -9.0f, -5.5f, 9.0f, 9.0f, 9.0f, new CubeDeformation(0.0f)), PartPose.offset((float)0.0f, (float)0.0f, (float)0.0f));
        PartDefinition spine = body.addOrReplaceChild("spine", CubeListBuilder.create().texOffs(44, 0).addBox(-3.0f, 2.0f, -3.0f, 6.0f, 5.0f, 4.0f, new CubeDeformation(0.0f)).texOffs(36, 0).addBox(-1.0f, 0.0f, 0.0f, 2.0f, 13.0f, 2.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)0.0f, (float)0.0f, (float)0.0f, (float)0.2182f, (float)0.0f, (float)0.0f));
        return LayerDefinition.create((MeshDefinition)meshdefinition, (int)64, (int)64);
    }

    @Override
    public void setupAnim(CursedSpiritRenderState state) {
        root().getAllParts().forEach(ModelPart::resetPose);
        this.head.yRot = state.yRot * 0.017453292f;
        this.head.xRot = state.xRot * 0.017453292f;
CursedSpiritClips.apply(state, this.screamAnimation, this.attackAnimation, this.idleAnimation, null, 2.5f, 4.5f);
    }
}
