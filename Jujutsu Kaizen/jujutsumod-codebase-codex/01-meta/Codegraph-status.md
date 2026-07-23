# Code Graph Status

Status: CURRENT

No versioned .codegraph index is present in the repository. Do not assume an external index exists.

Use, in order:

1. Native filesystem/symbol search.
2. The scoped Filesystem MCP server when available.
3. A code graph only after indexing the current checkout and recording its commit.

Any graph result must be cross-checked against current source before changing behavior.
