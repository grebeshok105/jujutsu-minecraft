# Registries

Status: CURRENT

| Registry area | Current count | Owner |
|---|---:|---|
| Items | 6 | JujutsuItems |
| Entity types | 3 | JujutsuEntities |
| Particles | 12 | JujutsuParticles |
| Sounds | 24 | JujutsuSounds |
| Effects | 3 | JujutsuEffects |
| Data components | 2 | JujutsuDataComponents |
| Persistent attachments | 1 | JujutsuAttachments |

Three entity types are registered (VERIFIED — JujutsuEntities.java:16-18): `projectjjk_nail`, `megumi_divine_dog` (both Divine Dogs ride one type, the pack tells them apart), and `todo_stone`. All three are `noSave()` transients. The twelve particles add `megumi_shadow_mote` to the eleven hairpin/Black-Flash ones; the twenty-four sounds add `aec_boom` and the two mega-nail sounds to the hairpin/projectjjk set; the three effects are `resonant_momentum`, `todo_swap_momentum`, and `megumi_shadow_grip`. CharacterPlayerState is stored through the character_state Fabric attachment and copied on death.

Client mixins (VERIFIED — src/client/resources/jujutsumod.client.mixins.json): CharacterSkinAnimationMixin, CharacterSkinMixin, FirstPersonHandFxMixin, HairpinCameraMixin, HairpinGameRendererMixin, and PlayerRenderContextMixin. All six are `required` and must be smoke-tested on Minecraft updates.

The render-facing three are roster-wide, not Nobara-specific: CharacterSkinAnimationMixin applies the selected GeckoLib pose to vanilla player parts, PlayerRenderContextMixin records the AbstractClientPlayer/partial-tick pair the bridge needs, and FirstPersonHandFxMixin owns both first-person hand styles. See [Vessel render stack](../04-client-vfx/Vessel-render-stack.md).
