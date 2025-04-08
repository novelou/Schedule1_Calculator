package services

import datas.RecipeRequirement
import resources.idToEffectName
import resources.requirements

fun findEffectByRequirements(material: String, subEffect: Int, currentEffects: List<Int>): Int? {
    val requirement = requirements.filter { it.requiredMaterial == material && it.requiredSubEffectId == subEffect }

    if (requirement.isEmpty()) {
        return null // レシピが存在しない場合、効果の更新は行わない
    } else {
        // `includeEffectId` と `excludeEffectId` の条件を満たす場合に効果を適用
        val validRecipe = requirement.find {
            (it.includeEffectId == null || !currentEffects.contains(it.includeEffectId)) &&
                    (it.excludeEffectId == null || currentEffects.contains(it.excludeEffectId))
        }

        return validRecipe?.effectId ?: requirement.firstOrNull { it.excludeEffectId == null }?.effectId
    }
}