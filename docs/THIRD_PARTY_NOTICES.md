# Third-Party Notices

## ProjectJJK temporary placeholders

Some current runtime assets under paths containing `projectjjk` are temporary placeholders used with permission from the ProjectJJK author. They are not relicensed as CC0 by this repository.

Upstream notice retained for provenance:

> Copyright © 2023 Hadences
>
> All rights reserved. The ProjectJJK project, including code, assets, and documentation, is the property of Hadences. Unauthorized copying, modification, distribution, transmission, display, performance, reproduction, licensing, derivative works, transfer, or use requires express written permission.

The jujutsumod developer has permission to use the current models/assets as temporary private-development placeholders. Do not expand this imported asset set. Replace the placeholders or preserve public redistribution permission before a public release.

## Segoe UI Semilight

`src/main/resources/assets/jujutsumod/font/neon.ttf` identifies as Segoe UI Semilight. It is not Open Sans and is not covered by the Open Sans OFL notice. Remove it if unused or replace it with a verified redistributable font before public distribution.

This file owns the notice; the tracked action item is R2 in [KNOWN_ISSUES.md](KNOWN_ISSUES.md).

## Rich-Modern-derived material

The current `client/rich` source and related assets require a separate provenance/license decision before public release.

## minecraft-java-fabric-mcp-server (dev-only companion)

The issue #43 spike's dev-only MCP companion (`src/mcpdev`, `jujutsumod-mcpdev` mod) references and runs against `chapmanjw/minecraft-java-fabric-mcp-server` (MIT), inspected at commit `0caf461`. The upstream jar loads exclusively through the `-PmcpSpike` / `-PmcpUpstreamJar` dev-run knobs and is never bundled into the release jar — `auditReleaseJarIsolation` forbids `com/chapmanjw/`, `io/modelcontextprotocol/` and `jujutsu/mcpdev/` entries and the `mcp-tools` entrypoint in the release descriptor. Do not expand this imported set; a separate provenance/license decision is required before any upstream code ships in a release.

Upstream notice retained:

> MIT License
>
> Copyright (c) 2026 John Chapman (@amzn)
>
> Permission is hereby granted, free of charge, to any person obtaining a copy
> of this software and associated documentation files (the "Software"), to deal
> in the Software without restriction, including without limitation the rights
> to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
> copies of the Software, and to permit persons to whom the Software is
> furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all
> copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
> IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
> FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
> AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
> LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
> OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
> SOFTWARE.