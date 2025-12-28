package services

import datas.Material
import datas.PathNode
import datas.SearchState
import kotlinx.coroutines.*
import resources.baseMaterials
import resources.effectAttributes
import resources.effectNameToId
import resources.idToEffectName
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 価値（IDが小さいほど高価値）を最大化するルートを探索するCUI用プログラム
 */
fun main() = runBlocking {
    println("=== 高価値効果ルート探索シミュレーション開始 ===")
    
    // 1. セットアップ（既存の高速化ロジックを流用）
    val allEffectIds = effectNameToId.values.distinct().sorted()
    if (allEffectIds.size > 64) {
        throw IllegalStateException("効果の種類が64を超えているため、Longによるビットマスク最適化が使用できません。")
    }

    // ID -> Bit位置 のマッピング
    val idToBit = IntArray(allEffectIds.maxOrNull()?.plus(1) ?: 0) { -1 }
    allEffectIds.forEachIndexed { index, id -> idToBit[id] = index }

    // Bit位置 -> ID のマッピング（結果表示用）
    val bitToId = IntArray(64) { 0 }
    allEffectIds.forEachIndexed { index, id -> bitToId[index] = id }

    // 遷移テーブルの事前計算
    val materialList = baseMaterials.toList()
    val transitionTable = Array(materialList.size) { LongArray(64) }
    val materialSelfMask = LongArray(materialList.size)

    println("遷移テーブルを計算中...")
    for (mIdx in materialList.indices) {
        val mat = materialList[mIdx]
        // 素材自体の効果マスク
        materialSelfMask[mIdx] = if (mat.effectId < idToBit.size && idToBit[mat.effectId] != -1) {
            1L shl idToBit[mat.effectId]
        } else 0L

        for (bit in allEffectIds.indices) {
            val existingEffectId = allEffectIds[bit]
            val triggeredEffects = findEffectByRequirements(mat.name, existingEffectId, emptyList())

            if (triggeredEffects.isNotEmpty()) {
                val isAllSameAsDefault = triggeredEffects.all { it == mat.effectId }
                if (isAllSameAsDefault) {
                    transitionTable[mIdx][bit] = (1L shl bit)
                } else {
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
                transitionTable[mIdx][bit] = (1L shl bit)
            }
        }
    }

    // 2. 探索実行
    // 結果を格納するリスト（スレッドセーフ）
    // Pair<効果IDリスト, パス>
    val foundPaths = ConcurrentHashMap.newKeySet<Pair<List<Int>, List<String>>>()
    val visitedStates = ConcurrentHashMap.newKeySet<Long>()

    // 初期状態：何もなし（RawMaterialなし）からスタート
    visitedStates.add(0L)
    var currentFrontier = listOf(SearchState(null, 0L))

    val maxDepth = 8 // 追加素材8つ
    var depth = 0
    
    println("探索を開始します (最大深度: $maxDepth)...")

    while (currentFrontier.isNotEmpty() && depth <= maxDepth) {
        val nextFrontier = currentFrontier.chunked(500).map { chunk ->
            async(Dispatchers.Default) {
                val localNextStates = mutableListOf<SearchState>()

                for (state in chunk) {
                    // 現在の状態を結果候補として保存
                    val currentEffects = maskToIds(state.effectsMask, bitToId)
                    if (currentEffects.isNotEmpty()) {
                        foundPaths.add(currentEffects to (state.pathNode?.toList() ?: emptyList()))
                    }

                    // 次のステップへ
                    if (depth < maxDepth) {
                    for (mIdx in materialList.indices) {
                        val mat = materialList[mIdx]
                        var nextMask = 0L
                        var tempMask = state.effectsMask

                        while (tempMask != 0L) {
                            val bit = java.lang.Long.numberOfTrailingZeros(tempMask)
                            nextMask = nextMask or transitionTable[mIdx][bit]
                            tempMask = tempMask and (1L shl bit).inv()
                        }

                        if (java.lang.Long.bitCount(nextMask) < 8) {
                            nextMask = nextMask or materialSelfMask[mIdx]
                        }

                        if (visitedStates.add(nextMask)) {
                            localNextStates.add(
                                SearchState(
                                    PathNode(state.pathNode, mat.name, depth + 1),
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

        currentFrontier = nextFrontier
        depth++
        println("深度 $depth 完了: 現在の状態数 ${currentFrontier.size}, 蓄積されたユニークな結果数 ${foundPaths.size}")
    }

    // 3. 集計とランキング
    println("集計中...")

    // 評価関数: 倍率（multiplierの積）が大きいものを高価値とする
    val comparator = Comparator<List<Int>> { o1, o2 ->
        val m1 = calculateMultiplierLocal(o1)
        val m2 = calculateMultiplierLocal(o2)
        // 降順（大きい方が上位）
        m2.compareTo(m1)
    }

    val topResults = foundPaths.toList()
        .sortedWith { a, b -> comparator.compare(a.first, b.first) }
        .take(10)

    println("\n=== 🏆 最も価値の高いルート Top 10 (倍率順) ===")
    topResults.forEachIndexed { index, (effects, path) ->
        val multiplier = calculateMultiplierLocal(effects)
        val effectNames = effects.sorted().map { id -> "${idToEffectName[id]}($id)" }
        println("${index + 1}位 (倍率: %.4f):".format(multiplier))
        println("  効果: $effectNames")
        println("  経路: ${path.joinToString(" -> ")}")
        println("--------------------------------------------------")
    }
}

fun maskToIds(mask: Long, bitToId: IntArray): List<Int> {
    val ids = mutableListOf<Int>()
    var tempMask = mask
    while (tempMask != 0L) {
        val bit = java.lang.Long.numberOfTrailingZeros(tempMask)
        ids.add(bitToId[bit])
        tempMask = tempMask and (1L shl bit).inv()
    }
    return ids
}

fun calculateMultiplierLocal(effectIds: List<Int>): Double {
    var multiplier = 1.0
    effectIds.forEach { id ->
        val name = idToEffectName[id]
        val attr = effectAttributes[name]
        if (attr != null) {
            multiplier *= attr.multiplier
        }
    }
    return multiplier
}