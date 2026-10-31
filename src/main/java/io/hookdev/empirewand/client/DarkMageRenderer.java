package io.hookdev.empirewand.client;

import io.hookdev.empirewand.DarkMage;
import io.hookdev.empirewand.EmpireWand;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** The Dark Mage: a humanoid in a hooded purple robe (textures/entity/dark_mage.png), his wand in his hand. */
public class DarkMageRenderer extends HumanoidMobRenderer<DarkMage, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    private static final Identifier TEXTURE = EmpireWand.id("textures/entity/dark_mage.png");

    public DarkMageRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return TEXTURE;
    }
}
