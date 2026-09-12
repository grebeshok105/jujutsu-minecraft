"""Throwaway MCP-over-HTTP client for the Minecraft dev lane (streamable HTTP, JSON-RPC 2.0).

Used when the lane's MCP servers are not mounted in the agent session (they mount only if the
game was already running at session start). Endpoints: mod tools 8765/mcp, upstream 8766/mcp.

Usage (from repo root):
    python analysis/mc_mcp.py list 8766
    python analysis/mc_mcp.py call 8766 entity_query '{"selector":"@a"}'
"""
import json
import sys
import urllib.request
import urllib.error

SERVERS = {"mod": "http://127.0.0.1:8765/mcp", "upstream": "http://127.0.0.1:8766/mcp"}
_sessions = {}


def _post(url, payload, session=None):
    data = json.dumps(payload).encode("utf-8")
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json, text/event-stream",
    }
    if session:
        headers["Mcp-Session-Id"] = session
    req = urllib.request.Request(url, data=data, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            sid = resp.headers.get("Mcp-Session-Id")
            body = resp.read().decode("utf-8", "replace")
            return sid, body, resp.status
    except urllib.error.HTTPError as e:
        return None, e.read().decode("utf-8", "replace"), e.code


def _parse(body):
    """MCP streamable HTTP may answer as SSE (event: message / data: {...}) or plain JSON."""
    if not body:
        return None
    if body.lstrip().startswith("{"):
        return json.loads(body)
    out = None
    for line in body.splitlines():
        if line.startswith("data:"):
            chunk = line[5:].strip()
            if chunk:
                out = json.loads(chunk)
    return out


def session(which):
    if which in _sessions:
        return _sessions[which]
    url = SERVERS.get(which, which if which.startswith("http") else SERVERS["upstream"])
    sid, body, status = _post(url, {
        "jsonrpc": "2.0", "id": 1, "method": "initialize",
        "params": {
            "protocolVersion": "2025-06-18",
            "capabilities": {},
            "clientInfo": {"name": "omp-throwaway", "version": "1"},
        },
    })
    if status != 200:
        raise RuntimeError(f"initialize failed {status}: {body[:400]}")
    _post(url, {"jsonrpc": "2.0", "method": "notifications/initialized", "params": {}}, session=sid)
    _sessions[which] = (url, sid)
    return _sessions[which]


def rpc(which, method, params=None, _id=2):
    url, sid = session(which)
    _, body, status = _post(url, {"jsonrpc": "2.0", "id": _id, "method": method, "params": params or {}}, session=sid)
    if status != 200:
        raise RuntimeError(f"{method} failed {status}: {body[:400]}")
    return _parse(body)


def tools(which):
    return rpc(which, "tools/list", {})


def call(which, name, args):
    return rpc(which, "tools/call", {"name": name, "arguments": args})


def text_of(result):
    """Flatten an MCP tool result to text."""
    if result is None:
        return ""
    if "error" in result:
        return f"ERROR {result['error']}"
    res = result.get("result", {})
    parts = []
    for item in res.get("content", []) or []:
        if item.get("type") == "text":
            parts.append(item.get("text", ""))
        else:
            parts.append(json.dumps(item)[:2000])
    if res.get("structuredContent"):
        parts.append(json.dumps(res["structuredContent"])[:4000])
    return "\n".join(parts)


if __name__ == "__main__":
    cmd = sys.argv[1]
    which = sys.argv[2] if len(sys.argv) > 2 else "upstream"
    if cmd == "list":
        data = tools(which)
        names = [t["name"] for t in data.get("result", {}).get("tools", [])]
        print(len(names), "tools")
        for n in names:
            print(" ", n)
    elif cmd == "call":
        name = sys.argv[3]
        args = json.loads(sys.argv[4]) if len(sys.argv) > 4 else {}
        print(text_of(call(which, name, args)))
    elif cmd == "shot":
        name = sys.argv[3] if len(sys.argv) > 3 else "view_capture"
        out = sys.argv[4] if len(sys.argv) > 4 else "shot.png"
        args = json.loads(sys.argv[5]) if len(sys.argv) > 5 else {}
        result = call(which, name, args)
        saved = 0
        for item in result.get("result", {}).get("content", []) or []:
            if item.get("type") == "image":
                import base64
                with open(out, "wb") as fh:
                    fh.write(base64.b64decode(item["data"]))
                saved += 1
                print(f"saved {out} ({item.get('mimeType')})")
            elif item.get("type") == "text":
                print(item.get("text", "")[:1500])
        if not saved:
            print("no image content:", text_of(result)[:800])
    elif cmd == "schema":
        name = sys.argv[3]
        data = tools(which)
        for t in data.get("result", {}).get("tools", []):
            if t["name"] == name:
                print(json.dumps(t, indent=2)[:6000])
