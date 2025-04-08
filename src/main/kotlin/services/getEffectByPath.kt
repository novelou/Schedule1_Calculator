package services

import resources.idToEffectName

fun getEffectByPath(path: List<String>): List<Int> {
    val effects: MutableList<Int> = mutableListOf()

    path.forEachIndexed { index, material ->
        val currentMaterialEffect = material.materialToEffectId()

        if (index == 0) {
            // 最初の素材の場合、その効果IDを追加
            effects.add(currentMaterialEffect)
        } else {
            // それ以降の素材の場合、効果IDを更新
            val updatedEffects = mutableListOf<Int>()
            effects.forEach { effect ->
                findEffectByRequirements(material, effect, effects)?.let { updatedEffects.add(it) } ?: updatedEffects.add(effect)
            }
            updatedEffects.add(currentMaterialEffect)
            effects.clear() // 既存の効果リストをクリアして更新後のリストを設定
            effects.addAll(updatedEffects)
        }
    }

    return effects
}