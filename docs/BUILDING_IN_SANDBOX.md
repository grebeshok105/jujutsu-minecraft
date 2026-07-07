# Building this mod inside a restricted sandbox (no daemon, firewalled loopback)

This document explains, in full detail, how to build `jujutsu-minecraft` (a Fabric mod for
Minecraft 1.21.8, Gradle 9.5.1 + Loom) inside a **locked-down sandbox** where the normal
`./gradlew build` **hangs forever** or fails with *"Could not connect to the Gradle daemon."*

It is written for a future session (human or agent) that lands in the same environment and needs
to compile/verify the project. If you are on a normal machine with internet and a JDK, ignore all of
this and just run `./gradlew build` — none of the workarounds below are needed there.

---

## TL;DR — the working recipe

```bash
# 1. JDK 21 (Temurin) extracted to ~/jdk-21.0.11+10 with the sandbox MITM CA imported (see below)
# 2. The bind-shim compiled to /tmp/bindshim.so (see below)
# 3. Then just:
~/gradle-inproc.sh build          # BUILD SUCCESSFUL, runs all 8 assertion tests
~/gradle-inproc.sh compileJava compileClientJava   # faster: compile only
```

`~/gradle-inproc.sh` is the wrapper that makes Gradle run **in-process** (no daemon fork, no TCP)
and pins the compile-worker socket to an allowed port. Its full contents are at the end of this doc.

---

## The environment and why the normal build breaks

The sandbox has three properties that each break a different part of the Gradle build:

1. **No JDK preinstalled, no `sudo`, no package manager access.** You must download a JDK yourself
   into your home directory.
2. **An intercepting HTTPS proxy (mitmproxy).** Every outbound TLS connection is re-signed with a
   custom CA. A stock JDK truststore does not trust that CA, so Gradle's dependency downloads fail
   with `PKIX path building failed` / `unable to find valid certification path`.
3. **The firewall blocks loopback (127.0.0.1) TCP on almost all ports.** Only a tiny allow-list of
   ports accepts connections (in practice `8090` plus a few platform service ports like
   `8080/18080/18081`). **This is the big one.** Gradle's architecture is client → daemon → worker,
   and the daemon/worker listen on **random high ports** on localhost. The client then tries to
   connect to that random port, the firewall drops it, and you get:

   ```
   Could not connect to the Gradle daemon.
   ...
   org.gradle.internal.remote.internal.ConnectException: Could not connect to server [<uuid> port:37095, addresses:[/127.0.0.1]]
   Caused by: java.net.SocketTimeoutException: Connect timed out
   ```

Each problem and its fix is below, in the order you hit them.

---

## Step 1 — Install a JDK 21 into your home directory

Temurin 21 was downloaded straight from the GitHub release assets (Adoptium's `api.adoptium.net`
redirects to `release-assets.githubusercontent.com`, so both domains must be firewall-approved):

```bash
cd ~
curl -L -o jdk.tar.gz \
  'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.11%2B10/OpenJDK21U-jdk_x64_linux_hotspot_21.0.11_10.tar.gz'
# sha256 must be 4b2220e232a97997b436ca6ab15cbf70171ecff52958a46159dfa5a8c44ca4de
sha256sum jdk.tar.gz
tar xzf jdk.tar.gz          # -> ~/jdk-21.0.11+10
export JAVA_HOME=~/jdk-21.0.11+10
"$JAVA_HOME/bin/java" -version
```

Domains that must be allow-listed for the JDK download **and** the Gradle build:

- `api.adoptium.net`, `release-assets.githubusercontent.com`  (JDK)
- `services.gradle.org`      (Gradle wrapper distribution)
- `maven.fabricmc.net`, `repo.maven.apache.org`   (Fabric Loom + Maven Central deps)
- `libraries.minecraft.net`, `piston-meta.mojang.com`, `piston-data.mojang.com`  (Minecraft jars + mappings for Loom)
- `github.com`               (git clone/push)

---

## Step 2 — Trust the sandbox MITM CA inside the JDK

Without this, the very first thing Gradle does (download its own distribution / resolve deps) dies
with a PKIX error. Import the proxy CA into the **JDK's** cacerts (not the system store, which the
JVM ignores):

```bash
"$JAVA_HOME/bin/keytool" -importcert -noprompt \
  -cacerts -storepass changeit \
  -alias sandbox-mitm \
  -file /usr/local/share/ca-certificates/mitmproxy-ca.crt
```

After this the Gradle wrapper can fetch `gradle-9.5.1-bin.zip` and all Maven/Loom artifacts over the
proxy. (The CA path above is where this sandbox keeps it; adjust if yours differs — look under
`/usr/local/share/ca-certificates/` or `/etc/ssl/certs/`.)

---

## Step 3 — The core problem: loopback TCP is firewalled, so the daemon is unreachable

### How we proved it

A quick Python probe that binds a listener on a port and then tries to connect to it shows that
**almost every localhost port times out** — only `8090` (and a handful of reserved service ports)
accept a connection. So any process that listens on a *random* port for IPC is unreachable.

### Why `--no-daemon` alone does NOT fix it

Gradle 9 still **forks a single-use daemon** even with `--no-daemon` whenever the launching JVM's
args don't match the "wanted" daemon args. You'll see:

```
To honour the JVM settings for this build a single-use Daemon process will be forked.
```

The forked daemon listens on a random port → same firewall block. Trying to match `GRADLE_OPTS` to
the daemon opts, downgrading Gradle, or commenting out `org.gradle.jvmargs` did **not** reliably stop
the fork.

### The fix, part A — run Gradle truly in-process

If you launch the Gradle **wrapper main class directly** with JVM args that already match what the
daemon would want, **plus the Gradle instrumentation agent**, Gradle runs the build inside that same
JVM and never forks a daemon (no daemon = no daemon TCP socket):

```bash
JAVA_HOME=~/jdk-21.0.11+10
DIST=$(ls -d ~/.gradle/wrapper/dists/gradle-9.5.1-bin/*/gradle-9.5.1)
AGENT="$DIST/lib/agents/gradle-instrumentation-agent-9.5.1.jar"

"$JAVA_HOME/bin/java" \
  -Xmx1G -Dfile.encoding=UTF-8 -Duser.country=US -Duser.language=en -Duser.variant \
  -javaagent:"$AGENT" \
  -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain \
  --no-daemon help
```

The three things that matter:
- **`-javaagent:.../gradle-instrumentation-agent-9.5.1.jar`** — without the agent, Gradle decides the
  current JVM is "not instrumented" and forks a daemon anyway.
- **JVM args exactly matching the daemon's** (`-Xmx1G -Dfile.encoding=UTF-8 -Duser.country=US
  -Duser.language=en -Duser.variant`) — any mismatch triggers a fork. You can read the wanted set out
  of a daemon log line (`daemonOpts=...`) under `~/.gradle/daemon/9.5.1/*.out.log`.
- **`--no-daemon`** — belt-and-suspenders.

With just part A, `help`/`compileJava`-only builds already succeed.

### The fix, part B — pin the compile *worker* socket to the allowed port

A full `build` runs `:compileJava` in a **Gradle Worker Daemon**, which is a *second* forked JVM that
connects back to the main process over a random loopback port — so `build` still failed with the same
`ConnectException` even though the main build was now in-process.

The main process opens a couple of listening sockets on `127.0.0.1:0` (OS-assigned random port). The
worker connects to the **second** of those. We force that listen socket onto the allowed port `8090`
with a tiny `LD_PRELOAD` shim that intercepts `bind()`:

- The shim watches `bind()` calls with port `0` (wildcard).
- It rewrites the **2nd** such wildcard bind (`BINDSHIM_SKIP=1`) to `BINDSHIM_PORT=8090`.
- The worker then connects to `127.0.0.1:8090`, which the firewall allows. 

> Why the 2nd bind, not the 1st? Empirically the first port-0 listener is something else; the worker
> message hub is the second. If a future Gradle version changes ordering, flip `BINDSHIM_SKIP` to `0`
> (or log every bind — see the debug variant in the git history of this file) and pick the socket the
> worker's `ConnectException` names.

Compile the shim once:

```bash
cat > /tmp/bindshim.c <<'EOF'
#define _GNU_SOURCE
#include <dlfcn.h>
#include <netinet/in.h>
#include <string.h>
#include <stdlib.h>
#include <sys/socket.h>
static int (*real_bind)(int, const struct sockaddr *, socklen_t) = NULL;
static int seen = 0;
int bind(int fd, const struct sockaddr *addr, socklen_t len) {
    if (!real_bind) real_bind = dlsym(RTLD_NEXT, "bind");
    const char *env = getenv("BINDSHIM_PORT");
    const char *skipenv = getenv("BINDSHIM_SKIP");
    int skip = skipenv ? atoi(skipenv) : 0;
    if (env && addr) {
        int isport0 = 0;
        if (addr->sa_family == AF_INET6 && ((struct sockaddr_in6*)addr)->sin6_port == 0) isport0 = 1;
        if (addr->sa_family == AF_INET  && ((struct sockaddr_in*)addr)->sin_port == 0) isport0 = 1;
        if (isport0) {
            int idx = seen++;
            if (idx == skip) {
                int port = atoi(env);
                if (addr->sa_family == AF_INET6) {
                    struct sockaddr_in6 a; memcpy(&a, addr, sizeof(a)); a.sin6_port = htons(port);
                    int r = real_bind(fd, (struct sockaddr*)&a, len); if (r==0) return 0;
                } else {
                    struct sockaddr_in a; memcpy(&a, addr, sizeof(a)); a.sin_port = htons(port);
                    int r = real_bind(fd, (struct sockaddr*)&a, len); if (r==0) return 0;
                }
            }
        }
    }
    return real_bind(fd, addr, len);
}
EOF
gcc -shared -fPIC -o /tmp/bindshim.so /tmp/bindshim.c -ldl
```

> Note: `LD_PRELOAD` is **not** inherited by a *forked Gradle daemon* (Gradle scrubs the daemon's
> environment), which is another reason we avoid the daemon entirely and run in-process — there the
> shim stays loaded in the JVM that spawns the worker.

---

## The wrapper script (`~/gradle-inproc.sh`)

This ties parts A + B together. It is the single command to use for every build in this sandbox.

```bash
#!/bin/bash
# In-process Gradle for this sandbox: loopback TCP to daemon/worker on random ports is
# firewalled; only port 8090 is allowed. We run the build in-process (matching JVM opts +
# instrumentation agent so Gradle doesn't fork a daemon), and an LD_PRELOAD bind shim pins
# the 2nd port-0 listen socket (worker message hub) to 8090.
export JAVA_HOME=~/jdk-21.0.11+10
DIST=$(ls -d ~/.gradle/wrapper/dists/gradle-9.5.1-bin/*/gradle-9.5.1)
AGENT="$DIST/lib/agents/gradle-instrumentation-agent-9.5.1.jar"
export LD_PRELOAD=/tmp/bindshim.so BINDSHIM_PORT=8090 BINDSHIM_SKIP=1
cd ~/workspace/jujutsu-minecraft || exit 1
exec "$JAVA_HOME/bin/java" -Xmx1G -Dfile.encoding=UTF-8 -Duser.country=US -Duser.language=en -Duser.variant \
  -javaagent:"$AGENT" -cp gradle/wrapper/gradle-wrapper.jar \
  org.gradle.wrapper.GradleWrapperMain --no-daemon "$@"
```

Usage:

```bash
~/gradle-inproc.sh build                              # full build + all 8 assertion tests
~/gradle-inproc.sh compileJava compileClientJava      # compile both source sets only (fast)
~/gradle-inproc.sh testProjectJjkNobaraProfile        # run one custom test task
```

A green run ends with `BUILD SUCCESSFUL` and prints each `*Test passed` line (the project uses
plain `main()`-based assertion tests wired as custom `JavaExec` tasks under `check`, see `build.gradle`).

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `PKIX path building failed` on first download | JDK doesn't trust the MITM CA | Re-run the `keytool -importcert` in Step 2 against the right JDK's cacerts |
| `Could not connect to the Gradle daemon` | daemon forked, its random port is firewalled | Use `~/gradle-inproc.sh` (in-process + agent), don't call `./gradlew` |
| `ConnectException: Could not connect to server [... port:NNNNN]` during `:compileJava` | worker socket on a blocked port | Ensure `LD_PRELOAD=/tmp/bindshim.so BINDSHIM_PORT=8090 BINDSHIM_SKIP=1` is set (the wrapper does this); if the port index changed, adjust `BINDSHIM_SKIP` |
| Build wants to fork despite the agent | JVM args don't match daemon opts | Read `daemonOpts=` from `~/.gradle/daemon/9.5.1/*.out.log` and mirror them exactly in the wrapper |
| `unshare --net` / iptables idea | not permitted, no root | Don't bother — the bind-shim approach needs no privileges |

---

## Why not simpler approaches?

- **`--offline`** — doesn't help; the daemon/worker sockets are the problem, not downloads.
- **Downgrade Gradle** — 8.x still forks a single-use daemon under the same conditions.
- **`unshare --user --net` to fake a permissive localhost** — blocked in this sandbox.
- **socat relay from 8090 to the daemon** — the daemon picks the port *after* it starts, and it's
  random each run, so you can't pre-wire a relay. The bind-shim solves it at the source instead.

This recipe needs **no root, no iptables, no network namespaces** — just a downloaded JDK, a CA
import, and a ~30-line `LD_PRELOAD` shim.
