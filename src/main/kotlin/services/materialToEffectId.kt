package services

import resources.baseMaterials

fun String.materialToEffectId(): Int {
    return baseMaterials.first { it.name == this }.effectId
}