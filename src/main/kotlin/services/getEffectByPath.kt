package services

import resources.idToEffectName

fun getEffectByPath(path: List<String>): List<Int> {
    val effects: MutableList<Int> = mutableListOf()

    path.forEachIndexed { index, material ->
        val currentMaterialEffect = material.materialToEffectId()

        if (index == 0) {
            effects.add(currentMaterialEffect)
        } else {
            val updatedEffects = mutableListOf<Int>()

            effects.forEach { effect ->
                val updated = findEffectByRequirements(material, effect, effects)

                if (updated != null && updated !in effects && updated != effect) {
                    // レシピで新しい効果に変わる場合、既に追加されていなければ追加
                    updatedEffects.add(updated)
                } else {
                    // 効果が変わらない、もしくは既にある → 元の効果を残す
                    updatedEffects.add(effect)
                }
            }

            effects.clear()
            effects.addAll(updatedEffects.distinct())

            // レシピを通じて得られる効果が already 含まれていれば、素材本体の効果も追加
            if (currentMaterialEffect !in effects) {
                effects.add(currentMaterialEffect)
            }
        }
    }

    return effects
}
