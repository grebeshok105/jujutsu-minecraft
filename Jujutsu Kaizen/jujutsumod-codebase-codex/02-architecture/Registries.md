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

Client mixins: CharacterSkinMixin, HairpinCameraMixin, HairpinGameRendererMixin, NobaraFirstPersonSnapMixin, NobaraLivingEntityRendererMixin, and NobaraPlayerRendererMixin. They are required and must be smoke-tested on Minecraft updates.
