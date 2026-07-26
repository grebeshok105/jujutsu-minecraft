# Code Graph Status

Status: CURRENT

A local `.codegraph/` index exists but is never versioned. `.codegraph/.gitignore` keeps the database, sockets, and logs out of git, so every machine indexes its own checkout. Do not assume a shared index exists.

Index the current checkout with `codegraph init`, then query it with `codegraph explore "<symbols or question>"` or the codegraph MCP tools. The index reflects whatever commit was checked out when it was built; rebuild it after switching branches.

Use, in order:

1. A local code graph when `.codegraph/` is present and current.
2. Native filesystem/symbol search.
3. The scoped Filesystem MCP server when available.

Any graph result must be cross-checked against current source before changing behavior.
