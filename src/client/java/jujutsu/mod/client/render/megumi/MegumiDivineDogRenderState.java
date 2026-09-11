package jujutsu.mod.client.render.megumi;

import java.util.Map;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.character.megumi.MegumiDogPresentationPolicy;

/**
 * GeckoLib render state for the Dire Wolf Divine Dog visual.
 *
 * <p>GeckoLib applies {@link GeoRenderState} to every {@code EntityRenderState} with a runtime mixin, so the
 * interface is invisible to javac; the renderer's bound needs it statically. The data bag below mirrors the
 * mixin's own implementation and is the store GeckoLib writes tickets into.
 */
public final class MegumiDivineDogRenderState extends LivingEntityRenderState implements GeoRenderState {
	private final Map<DataTicket<?>, Object> geckolibData = new Reference2ObjectOpenHashMap<>();

	public MegumiDogPresentationPolicy.Phase phase = MegumiDogPresentationPolicy.Phase.ACTIVE;
	public float progress = 1.0f;
	public float verticalOffset;
	public boolean blackVariant;
	public float attackAnim;

	@Override
	public <D> void addGeckolibData(DataTicket<D> dataTicket, @Nullable D data) {
		geckolibData.put(dataTicket, data);
	}

	@Override
	public boolean hasGeckolibData(DataTicket<?> dataTicket) {
		return geckolibData.containsKey(dataTicket);
	}

	@Nullable
	@Override
	public <D> D getGeckolibData(DataTicket<D> dataTicket) {
		Object data = geckolibData.get(dataTicket);

		return data == null ? null : (D) data;
	}

	@Override
	public Map<DataTicket<?>, Object> getDataMap() {
		return geckolibData;
	}
}
