# Registries

Status: CURRENT

| Registry area | Current count | Owner |
|---|---:|---|
| Items | 6 | JujutsuItems |
| Entity types | 1 | JujutsuEntities |
| Particles | 11 | JujutsuParticles |
| Sounds | 21 | JujutsuSounds |
| Effects | 1 | JujutsuEffects |
| Data components | 2 | JujutsuDataComponents |
| Persistent attachments | 1 | JujutsuAttachments |

The single entity type is projectjjk_nail. CharacterPlayerState is stored through the character_state Fabric attachment and copied on death.

Client mixins (VERIFIED — src/client/resources/jujutsumod.client.mixins.json): CharacterRenderDispatchMixin, CharacterSkinMixin, FirstPersonHandFxMixin, HairpinCameraMixin, HairpinGameRendererMixin, and PlayerRenderContextMixin. All six are `required` and must be smoke-tested on Minecraft updates.

The render-facing three are roster-wide, not Nobara-specific: CharacterRenderDispatchMixin picks the vessel renderer, PlayerRenderContextMixin records the AbstractClientPlayer/partial-tick pair the dispatch needs, and FirstPersonHandFxMixin owns both first-person hand styles. See [Vessel render stack](../04-client-vfx/Vessel-render-stack.md).
