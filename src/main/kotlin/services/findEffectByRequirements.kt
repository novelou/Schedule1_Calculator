package services

import resources.requirements

fun findEffectByRequirements(material: String, subEffect: Int, currentEffects: List<Int>): List<Int> {
    val matchingRecipes = requirements.filter { it.requiredMaterial == material && it.requiredSubEffectId == subEffect }

    val validRecipes = matchingRecipes.filter {
        (it.includeEffectId == null || currentEffects.contains(it.includeEffectId)) &&
                (it.excludeEffectId == null || !currentEffects.contains(it.excludeEffectId))
    }

    return validRecipes.map { it.effectId }
}
