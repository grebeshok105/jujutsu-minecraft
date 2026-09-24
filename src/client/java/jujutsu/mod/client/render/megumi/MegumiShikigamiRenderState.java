package jujutsu.mod.client.render.megumi;

import java.util.Map;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.character.megumi.MegumiShikigamiPresentationPolicy;

/**
 * GeckoLib render state for the four new shikigami bodies.
 *
 * <p>Same trap as the dog state: GeckoLib applies {@link GeoRenderState} to every {@code EntityRenderState}
 * with a runtime mixin, so javac cannot see the interface — the renderer's bound needs it statically, and
 * the data bag below is the store GeckoLib writes tickets into.
 */
public final class MegumiShikigamiRenderState extends LivingEntityRenderState implements GeoRenderState {
	private final Map<DataTicket<?>, Object> geckolibData = new Reference2ObjectOpenHashMap<>();

	public MegumiShikigamiPresentationPolicy.Phase phase = MegumiShikigamiPresentationPolicy.Phase.ACTIVE;
	public float progress = 1.0f;
	public float verticalOffset;
	/** The body is mid-action (dive impact, tongue, jet): the action clip owns the whole body. */
	public boolean actionActive;
	public float attackAnim;
	/**
	 * Which clip the action layer holds, defined per type (0 = none). Serpent uses
	 * 1=submerge, 2=emerge, 3=bind, 4=release; deer 1=pulse, 2=shove; ox 1=windup, 2=charge,
	 * 3=impact, 4=recover; tiger 1=windup, 2..4=strikes, 5=recover.
	 */
	public int actionIndex;

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
