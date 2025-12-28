package services

import datas.Material
import kotlinx.coroutines.*
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

// 探索の状態を保持するデータクラス
private data class SearchState(
    val path: List<String>,
    val currentEffects: Set<Int>
)

suspend fun findPathsToTargetEffectsViaSimulation(
    materials: List<Material>,
    targetEffects: List<Int>,
    maxResults: Int,
    initialMaterial: Material? = null
): List<List<String>> = coroutineScope {
    val result = Collections.synchronizedList(mutableListOf<List<String>>())

    // 【最適化1】訪問済みの「効果の組み合わせ」を記録して枝刈りを行う
    // ConcurrentHashMapのKeySetを使うことでスレッドセーフに重複チェックが可能
    val visitedStates = ConcurrentHashMap.newKeySet<Set<Int>>()

    // 初期状態の構築
    val initialPath = if (initialMaterial != null) listOf(initialMaterial.name) else emptyList()
    val initialEffects = if (initialMaterial != null) setOf(initialMaterial.effectId) else emptySet<Int>()

    // 初期状態を記録
    visitedStates.add(initialEffects)

    var currentFrontier = listOf(SearchState(initialPath, initialEffects))
    var steps = 0
    val targetSet = targetEffects.toSet()

    while (currentFrontier.isNotEmpty()) {
        steps++

        if (result.size >= maxResults) break

        val processorCount = Runtime.getRuntime().availableProcessors()
        val chunkSize = (currentFrontier.size / processorCount).coerceAtLeast(50)

        val nextFrontier = currentFrontier.chunked(chunkSize).map { chunk ->
            async(Dispatchers.Default) {
                val localNextStates = mutableListOf<SearchState>()

                for (state in chunk) {
                    // 他のスレッドですでに十分な結果が見つかっていたら中断
                    if (result.size >= maxResults) break

                    // 深さ制限 (10)
                    if (state.path.size >= 10) continue

                    for (material in materials) {
                        // 【最適化2】インクリメンタルに次の効果を計算
                        // 毎回 getEffectByPath を呼ぶのではなく、前の状態からの差分で計算する
                        val nextEffects = calculateNextEffects(state.currentEffects, material)

                        // ターゲット条件を満たすかチェック
                        if (nextEffects.containsAll(targetSet)) {
                            synchronized(result) {
                                if (result.size < maxResults) {
                                    result.add(state.path + material.name)
                                }
                            }
                            // ゴールに到達したパスはこれ以上伸ばさない（最短経路優先のため）
                        } else {
                            // 【最適化1の続き】まだ到達していない効果セットなら探索を続ける
                            // add が true を返す＝初めてこの状態に到達した（アトミック操作）
                            if (visitedStates.add(nextEffects)) {
                                localNextStates.add(
                                    SearchState(
                                        state.path + material.name,
                                        nextEffects
                                    )
                                )
                            }
                        }
                    }
                }
                localNextStates
            }
        }.awaitAll().flatten()

        if (result.size >= maxResults) break
        currentFrontier = nextFrontier
    }

    println("🔍 探索完了: ステップ数=$steps, 見つかったパス=${result.size}")
    return@coroutineScope result.toList()
}

/**
 * 現在の効果セットに新しい素材を加えたときの結果を計算するヘルパー関数
 * getEffectByPath のロジックをインクリメンタルに再現したもの
 */
private fun calculateNextEffects(currentEffects: Set<Int>, material: Material): Set<Int> {
    val nextEffects = mutableSetOf<Int>()
    val currentEffectsList = currentEffects.toList()

    // 現在発現している各効果に対して、新しい素材が反応するかチェック
    for (existingEffectId in currentEffects) {
        val triggeredEffects = findEffectByRequirements(material.name, existingEffectId, currentEffectsList)

        if (triggeredEffects.isNotEmpty()) {
            val isAllSameAsDefault = triggeredEffects.all { it == material.effectId }
            if (isAllSameAsDefault) {
                nextEffects.add(existingEffectId)
            } else {
                triggeredEffects.forEach { newEffect ->
                    if (newEffect != material.effectId) {
                        nextEffects.add(newEffect)
                    }
                }
            }
        } else {
            nextEffects.add(existingEffectId)
        }
    }

    // 素材自体の効果を追加
    nextEffects.add(material.effectId)

    return nextEffects
}
