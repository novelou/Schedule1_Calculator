package services

import datas.Material

fun findPathsToTargetEffectsViaSimulation(
    materials: List<Material>,
    targetEffects: List<Int>,
    maxResults: Int
): List<List<String>> {
    val result = mutableListOf<List<String>>()
    val queue = ArrayDeque<List<Material>>()
    queue.add(emptyList()) // 空のパスからスタート

    var steps = 0

    while (queue.isNotEmpty()) {
        val currentPath = queue.removeFirst()
        steps++

        // パスを名前のリストに変換して getEffectByPath に渡す
        val materialNames = currentPath.map { it.name }
        val effects = getEffectByPath(materialNames)

        // ゴールチェック
        if (targetEffects.all { effects.contains(it) }) {
            result.add(materialNames)
            if (result.size >= maxResults) break
            continue
        }

        // 素材を追加して次の状態を探索
        for (material in materials) {
            val newPath = currentPath + material

            // ループしすぎ防止（必要に応じて調整）
            if (newPath.size > 10) continue

            queue.add(newPath)
        }
    }

    println("🔍 探索完了: ステップ数=$steps, 見つかったパス=${result.size}")
    return result
}