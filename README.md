# Virtual Farm Works

A NeoForge mod for Minecraft 26.1.2 that offers compact, server-friendly virtual farming.
Instead of simulating thousands of physical crops, each Farm Matrix keeps a single global growth cycle and aggregated
plot groups, so large farms cost almost nothing in server tick time.

Designed for large modpacks and for compatibility with modded crops, especially Mystical Agriculture.

**Status:** early development (scaffolding). See [CLAUDE.md](CLAUDE.md) for the design and project status.

## License

MIT, for both code and assets (textures, models, JSON). See [LICENSE](LICENSE).
Build files derived from the NeoForge MDK keep their original notice in [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt).

## Building

Requires JDK 25.

```
.\gradlew.bat build
.\gradlew.bat runClient
```
