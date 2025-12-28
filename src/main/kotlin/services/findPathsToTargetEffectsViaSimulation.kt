package services

import datas.Material
import datas.PathNode
import datas.SearchState
import kotlinx.coroutines.*
import resources.effectNameToId
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

suspend fun findPathsToTargetEffectsViaSimulation(
    materials: List<Material>,
    targetEffects: List<Int>,
    maxResults: Int,
    initialMaterial: Material? = null
): List<List<String>> = coroutineScope {
    val result = Collections.synchronizedList(mutableListOf<List<String>>())

    // 1. 効果IDとビット位置(0..63)の対応付けを作成
    // resources.effectNameToId から全効果を取得
    val allEffectIds = effectNameToId.values.distinct().sorted()
    if (allEffectIds.size > 64) {
        throw IllegalStateException("効果の種類が64を超えているため、Longによるビットマスク最適化が使用できません。")
    }

    val idToBit = IntArray(allEffectIds.maxOrNull()?.plus(1) ?: 0) { -1 }
    allEffectIds.forEachIndexed { index, id -> idToBit[id] = index }

    // ヘルパー: IDリスト -> Bitmask
    fun toMask(ids: Collection<Int>): Long {
        var mask = 0L
        for (id in ids) {
            if (id >= 0 && id < idToBit.size) {
                val bit = idToBit[id]
                if (bit != -1) mask = mask or (1L shl bit)
            }
        }
        return mask
    }

    // 2. 【最適化2】遷移テーブルの事前計算 (Transition Table)
    // transitionTable[素材Index][効果Bit] = その素材を加えたときに、その効果がどう変化するかのマスク
    // materialSelfMask[素材Index] = その素材自体が持つ効果のマスク
    val materialList = materials.toList() // インデックスアクセス用
    val transitionTable = Array(materialList.size) { LongArray(64) }
    val materialSelfMask = LongArray(materialList.size)

    // 並列計算用にディスパッチャを使用せず、軽量なのでメインスレッドで計算
    for (mIdx in materialList.indices) {
        val mat = materialList[mIdx]

        // 素材自体の効果マスク
        materialSelfMask[mIdx] = if (mat.effectId < idToBit.size && idToBit[mat.effectId] != -1) {
            1L shl idToBit[mat.effectId]
        } else 0L

        // 各ビット（効果）に対する反応を事前計算
        for (bit in allEffectIds.indices) {
            val existingEffectId = allEffectIds[bit]

            // 既存のロジック(findEffectByRequirements)を使って結果を取得
            // ※ここで findEffectByRequirements はキャッシュを使っているため高速
            val triggeredEffects = findEffectByRequirements(mat.name, existingEffectId, emptyList())

            if (triggeredEffects.isNotEmpty()) {
                val isAllSameAsDefault = triggeredEffects.all { it == mat.effectId }
                if (isAllSameAsDefault) {
                    // 吸収されて消える場合 -> 元の効果を残す (ビットを立てたままにする)
                    transitionTable[mIdx][bit] = (1L shl bit)
                } else {
                    // 変化する場合 -> 新しい効果のマスクを計算
                    // ただし、素材自体の効果(mat.effectId)は除外して登録（後で一括で足すため）
                    var resultMask = 0L
                    triggeredEffects.forEach { newId ->
                        if (newId != mat.effectId) {
                            if (newId < idToBit.size) {
                                val newBit = idToBit[newId]
                                if (newBit != -1) resultMask = resultMask or (1L shl newBit)
                            }
                        }
                    }
                    transitionTable[mIdx][bit] = resultMask
                }
            } else {
                // 反応なし -> 元の効果を維持
                transitionTable[mIdx][bit] = (1L shl bit)
            }
        }
    }

    // 3. 探索開始
    // 訪問済みセットも Long (プリミティブのラッパー) なので高速
    val visitedStates = ConcurrentHashMap.newKeySet<Long>()

    // 初期状態
    val initialMask = if (initialMaterial != null) toMask(listOf(initialMaterial.effectId)) else 0L
    val initialNode = if (initialMaterial != null) PathNode(null, initialMaterial.name, 1) else null

    visitedStates.add(initialMask)

    var currentFrontier = listOf(SearchState(initialNode, initialMask))
    val targetMask = toMask(targetEffects)

    // ターゲットマスクが0(無効なIDのみ)の場合のガード
    if (targetEffects.isNotEmpty() && targetMask == 0L) {
        println("警告: ターゲット効果が無効、またはIDが見つかりません。")
        return@coroutineScope emptyList()
    }

    var steps = 0

    while (currentFrontier.isNotEmpty()) {
        steps++
        if (result.size >= maxResults) break

        val processorCount = Runtime.getRuntime().availableProcessors()
        // 状態管理が軽くなったのでチャンクサイズを大きくしてもOK
        val chunkSize = (currentFrontier.size / processorCount).coerceAtLeast(100)

        val nextFrontier = currentFrontier.chunked(chunkSize).map { chunk ->
            async(Dispatchers.Default) {
                val localNextStates = mutableListOf<SearchState>()

                for (state in chunk) {
                    if (result.size >= maxResults) break

                    val currentDepth = state.pathNode?.depth ?: 0
                    if (currentDepth >= 10) continue

                    // 全素材に対してループ
                    for (mIdx in materialList.indices) {
                        val mat = materialList[mIdx]

                        // 【超高速化】ビット演算による次状態の計算
                        var nextMask = 0L
                        var tempMask = state.effectsMask

                        // 現在立っているビットを走査
                        while (tempMask != 0L) {
                            val bit = java.lang.Long.numberOfTrailingZeros(tempMask)
                            // 事前計算テーブルから結果を取得してOR合成
                            nextMask = nextMask or transitionTable[mIdx][bit]
                            // 処理したビットを消す
                            tempMask = tempMask and (1L shl bit).inv()
                        }
                        // 素材自体の効果を足す
                        nextMask = nextMask or materialSelfMask[mIdx]

                        // ターゲット判定 ( (A & Target) == Target )
                        if ((nextMask and targetMask) == targetMask) {
                            synchronized(result) {
                                if (result.size < maxResults) {
                                    val newNode = PathNode(state.pathNode, mat.name, currentDepth + 1)
                                    result.add(newNode.toList())
                                }
                            }
                        } else {
                            // 訪問済みチェック (Longの比較は爆速)
                            if (visitedStates.add(nextMask)) {
                                localNextStates.add(
                                    SearchState(
                                        PathNode(state.pathNode, mat.name, currentDepth + 1),
                                        nextMask
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

    println("🚀 爆速探索完了: ステップ数=$steps, 見つかったパス=${result.size}")
    return@coroutineScope result.toList()
}
