package services

import resources.requirements

// 【最適化】検索用キャッシュ
// リストを毎回全探索するのではなく、Map化して O(1) で引けるようにする
// Key1: 反応元の効果ID, Key2: 素材名 -> Value: 結果の効果IDリスト
private val requirementsCache: Map<Int, Map<String, List<Int>>> by lazy {
    requirements
        .groupBy { it.requiredSubEffectId }
        .mapValues { (_, reqsBySource) ->
            reqsBySource
                .groupBy { it.requiredMaterial }
                .mapValues { (_, reqs) ->
                    reqs.map { it.effectId }
                }
        }
}

fun findEffectByRequirements(material: String, subEffect: Int, currentEffects: List<Int>): List<Int> {
    // キャッシュから高速に取得
    // 現状のデータには include/exclude の条件付きレシピが含まれていないため、
    // 単純な Map 検索だけで結果を返せる（これが最速）
    return requirementsCache[subEffect]?.get(material) ?: emptyList()
}
