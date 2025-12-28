package services

import resources.baseMaterials
import resources.rawMaterials

fun String.materialToEffectId(): Int {
    val material = baseMaterials.find { it.name == this }
        ?: rawMaterials.find { it.name == this }
        ?: throw NoSuchElementException("Material not found: $this")
    return material.effectId
}
