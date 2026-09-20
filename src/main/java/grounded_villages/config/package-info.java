/**
 * The one config file this mod writes (`docs/spec/contracts/data-contract.md` {@code
 * DATA-REQ-002}): a pure Java model ({@link grounded_villages.config.ConfigModel}) and its
 * parser/serialiser ({@link grounded_villages.config.ConfigCodec}), built on a small hand-rolled,
 * dependency-free JSON reader ({@link grounded_villages.config.ConfigJson}) rather than a config
 * library (`decisions/DEC-008-config-file.md`). Carries no Minecraft, Fabric, NeoForge or Forge
 * import anywhere in this package (`04-architecture.md` {@code ARCH-DEC-002}) -- the only
 * loader-specific line in this mod's whole config surface is the config directory lookup, which
 * lives in each loader's own entrypoint, not here (`ARCH-DEC-003`).
 */
package grounded_villages.config;
