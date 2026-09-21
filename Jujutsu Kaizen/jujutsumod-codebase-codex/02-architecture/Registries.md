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

Three entity types are registered (VERIFIED — JujutsuEntities.java:16-18): `projectjjk_nail`, `megumi_divine_dog` (both Divine Dogs ride one type, the pack tells them apart), and `todo_stone`. All three are `noSave()` transients. The twelve particles add `megumi_shadow_mote` to the eleven hairpin/Black-Flash ones; the twenty-six sounds add `aec_boom`, the two mega-nail sounds and the two Divine Dogs events (`megumi.dog_ambient`, `megumi.dog_growl`) to the hairpin/projectjjk set; the three effects are `resonant_momentum`, `todo_swap_momentum`, and `megumi_shadow_grip`. The vanilla `wolf_sound_variant` registry additionally carries the mod's `jujutsumod:dire_wolf` entry, which routes the dogs' ambient and growl sounds and is set on each summoned dog with the variant and collar components. CharacterPlayerState is stored through the character_state Fabric attachment and copied on death.

Client mixins: 17 total in `src/client/resources/jujutsumod.client.mixins.json` (VERIFIED — count tracked in [00-MOC](../00-MOC.md)). The render-facing subset: CharacterSkinAnimationMixin, CharacterSkinMixin, FirstPersonHandFxMixin, HairpinCameraMixin, HairpinGameRendererMixin, and PlayerRenderContextMixin — all `required` and must be smoke-tested on Minecraft updates. The black-hole VFX pass adds four more (GameRenderer renderLevel TAIL, GuiRenderer HEAD/TAIL, SoundEngine.calculateVolume, BlackHoleBobViewMixin).

The render-facing three are roster-wide, not Nobara-specific: CharacterSkinAnimationMixin applies the selected GeckoLib pose to vanilla player parts, PlayerRenderContextMixin records the AbstractClientPlayer/partial-tick pair the bridge needs, and FirstPersonHandFxMixin owns both first-person hand styles. See [Vessel render stack](../04-client-vfx/Vessel-render-stack.md).
