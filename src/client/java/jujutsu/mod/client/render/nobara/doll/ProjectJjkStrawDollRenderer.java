package jujutsu.mod.client.render.nobara.doll;

import jujutsu.mod.character.nobara.projectjjk.ProjectJjkStrawDollItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public final class ProjectJjkStrawDollRenderer extends GeoItemRenderer<ProjectJjkStrawDollItem> {
	public ProjectJjkStrawDollRenderer() {
		super(new ProjectJjkStrawDollModel());
	}

	public static GeoRenderProvider provider() {
		return new GeoRenderProvider() {
			private ProjectJjkStrawDollRenderer renderer;

			@Override
			public GeoItemRenderer<?> getGeoItemRenderer() {
				if (renderer == null) {
					renderer = new ProjectJjkStrawDollRenderer();
				}
				return renderer;
			}
		};
	}
}
