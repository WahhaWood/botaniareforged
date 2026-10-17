# Xplat

This is the `Xplat` submodule of Botania. It contains all Botania code that does
not depend on the loader's own API, so it stays out of the way when the loader
does change. The term "xplat" may also appear and means the same: something that
is not tied to a particular mod loader. "Xplat" stands for "cross platform". Other
multi-loader literature may call this the "Common" module, but that is easily
conflated with the old connotation of Common as "code shared between client and
server".

Botania is a NeoForge-only mod here: the Fabric port was removed rather than
maintained, and the merged Applied Botanics / Botanical Extra Machinery / Voltany
addons never had one. `Xplat` remains as a separate module because it still earns
its keep — it is the boundary that keeps loader-specific code out of the shared
body, and downstream addon authors compile against it directly. Folding it into
`NeoForge` would be a large mechanical change to roughly a thousand files for no
functional gain, so the name is a historical one rather than a promise.

`Xplat` code may occasionally need to make loader-specific calls, however. The
`XplatAbstractions` interface is present and implemented by the `NeoForge`
submodule for such calls as the need arises; it is discovered through
`ServiceLoader`.

## Build
The build system for the Xplat submodule works in a somewhat special manner.

### Part of the loader build
When the NeoForge artifact is built, the `Xplat` module's sources are
substituted into the project, as if the contents of `Xplat/src` are pasted into
`NeoForge/src`. From the perspective of players or modders consuming the
artifact, it should be indistinguishable from having two separate branches. Xplat
code that ends up in the JAR is remapped to official/Mojmap names like any
regular mod.

### Separate Xplat artifact
The `Xplat` module also has its own build.gradle, which uses ModDevGradle to grab
vanilla as a dependency to put on the classpath. The `Xplat` module's code is
also published independently to Maven in raw Mojmap without any remapping. This
is to aid in downstream addon authors that may also want to keep their addons
loader-agnostic. They would have their own `Xplat` module that depends on
Botania's `Xplat` module. However, Botania's `Xplat` module JAR should
**never appear on the classpath at runtime**. If a player finds themselves with
a Botania-Xplat JAR, things have gone very, very wrong.