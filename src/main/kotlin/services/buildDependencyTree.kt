package services

import datas.EffectDependency
import datas.RecipeRequirement

fun buildDependencyTree(requirements: List<RecipeRequirement>): Map<Int, EffectDependency> {
    val dependencyMap = mutableMapOf<Int, EffectDependency>()

    // 各RecipeRequirementから依存関係を生成
    requirements.forEach { req ->
        val effectId = req.effectId
        val subEffectId = req.requiredSubEffectId
        val requiredMaterial = req.requiredMaterial

        // 依存関係がすでに登録されていなければ初期化
        if (effectId !in dependencyMap) {
            dependencyMap[effectId] = EffectDependency(effectId, mutableListOf(), mutableListOf())
        }

        // サブ効果を追加
        if (!dependencyMap[effectId]!!.requiredSubEffects.contains(subEffectId)) {
            dependencyMap[effectId]!!.requiredSubEffects.add(subEffectId)
        }

        // 材料を追加
        if (!dependencyMap[effectId]!!.requiredMaterials.contains(requiredMaterial)) {
            dependencyMap[effectId]!!.requiredMaterials.add(requiredMaterial)
        }

        // excludeEffectId や includeEffectId を考慮する場合（オプション）
        req.excludeEffectId?.let {
            if (!dependencyMap[effectId]!!.requiredSubEffects.contains(it)) {
                dependencyMap[effectId]!!.requiredSubEffects.add(it)
            }
        }
        req.includeEffectId?.let {
            if (!dependencyMap[effectId]!!.requiredSubEffects.contains(it)) {
                dependencyMap[effectId]!!.requiredSubEffects.add(it)
            }
        }
    }

    return dependencyMap
}