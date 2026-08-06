package jujutsu.mcpdev;

import java.util.List;
import java.util.Map;

import com.chapmanjw.minecraft.fabric.mcp.compat.ToolCategory;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Tool;
import com.chapmanjw.minecraft.fabric.mcp.tools.ToolProvider;

/**
 * The {@code mcp-tools} entrypoint of the dev-only companion mod: contributes the repository's
 * MCP tools to the upstream bridge without the bridge ever importing jujutsumod classes (the
 * coupling direction is mcpdev -> both; see the issue #43 spike decision record).
 *
 * <p>The {@code jujutsu} tool-name domain is not in the upstream built-in category map, and the
 * compatibility filter hard-rejects unknown domains — so this provider declares the domain
 * explicitly via {@link #domainCategories()}, mapping it to {@link ToolCategory#SERVER}
 * (enabled by default; read-only info surface). Without that declaration the live registry
 * skipped the tool with "No category mapping for domain prefix 'jujutsu'" — caught by the
 * spike's first OMP run.
 */
public final class JujutsuModStatusToolProvider implements ToolProvider {

	@Override
	public List<Class<? extends Tool>> toolClasses() {
		return List.of(
				JujutsuAbilityInvokeTool.class,
				JujutsuCooldownsClearTool.class,
				JujutsuCooldownsGetTool.class,
				JujutsuFixtureResetTool.class,
				JujutsuModStatusTool.class,
				JujutsuStateGetTool.class,
				JujutsuVesselListTool.class,
				JujutsuVesselSelectTool.class);
	}

	@Override
	public Map<String, ToolCategory> domainCategories() {
		return Map.of("jujutsu", ToolCategory.SERVER);
	}
}
