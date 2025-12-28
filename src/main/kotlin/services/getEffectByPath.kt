package services

fun getEffectByPath(path: List<String>): List<Int> {
    val effects: MutableList<Int> = mutableListOf()

    path.forEachIndexed { index, material ->
        val currentMaterialEffect = material.materialToEffectId()

        if (index == 0) {
            effects.add(currentMaterialEffect)
        } else {
            val nextEffects = mutableListOf<Int>()

            // 既存の効果が新しい効果に変化するかチェック
            effects.forEach { effect ->
                val newEffects = findEffectByRequirements(material, effect, effects)

                if (newEffects.isNotEmpty()) {
                    // レシピが存在する場合

                    // 変化先がすべて「素材のデフォルト効果」と同じ場合、
                    // それは実質的に効果が吸収されて消滅することを意味する。
                    // ユーザーの意図として、この場合は元の効果を残す（レシピを適用しない）挙動とする。
                    val isAllSameAsDefault = newEffects.all { it == currentMaterialEffect }

                    if (isAllSameAsDefault) {
                        nextEffects.add(effect)
                    } else {
                        // 素材のデフォルト効果以外の新しい効果があれば追加する
                        newEffects.forEach { newEffect ->
                            if (newEffect != currentMaterialEffect) {
                                nextEffects.add(newEffect)
                            }
                        }
                    }
                } else {
                    // レシピがない場合は元の効果を維持
                    nextEffects.add(effect)
                }
            }

            // 素材自体の効果を追加
            nextEffects.add(currentMaterialEffect)

            effects.clear()
            effects.addAll(nextEffects.distinct())
        }
    }

    return effects
}
