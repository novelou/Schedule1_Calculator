@file:OptIn(ExperimentalLayoutApi::class)

import resources.*
import services.*
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Preview
fun App() {
    val allEffects = effectNameToId.keys.toList()
    val scope = rememberCoroutineScope()
    val selectedEffects = remember { mutableStateMapOf<String, Boolean>() }
    allEffects.forEach { effect -> selectedEffects.putIfAbsent(effect, false) }

    var maxResultsText by remember { mutableStateOf("1") }
    
    // 検索結果の状態
    var searchResults by remember { mutableStateOf<List<Pair<List<String>, List<Int>>>>(emptyList()) }
    var searchMessage by remember { mutableStateOf("") }

    var selectedTabIndex by remember { mutableStateOf(0) }

    // シミュレーション結果の状態
    var simulationPath by remember { mutableStateOf<List<String>>(emptyList()) }
    var simulationEffects by remember { mutableStateOf<List<Int>>(emptyList()) }

    var isSearching by remember { mutableStateOf(false) }

    // 検索タブ用のベース素材選択
    var searchSelectedRawMaterial by remember { mutableStateOf<String?>(null) }

    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            // タブ
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }) {
                    Text("検索")
                }
                Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }) {
                    Text("シミュレーション")
                }
            }

            Spacer(Modifier.height(16.dp))

            when (selectedTabIndex) {
                0 -> {
                    // 検索タブの中身
                    Text("効果を選択してください", style = MaterialTheme.typography.h6)

                    FlowRow(modifier = Modifier.fillMaxWidth()) {
                        allEffects.forEach { effect ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = selectedEffects[effect] == true,
                                    onCheckedChange = { selectedEffects[effect] = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = effectAttributes[effect]?.color ?: MaterialTheme.colors.secondary
                                    )
                                )
                                OutlinedText(
                                    text = effect,
                                    color = effectAttributes[effect]?.color ?: Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.body1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("ベース素材（任意・1つのみ）", style = MaterialTheme.typography.h6)
                    FlowRow(modifier = Modifier.fillMaxWidth()) {
                        rawMaterials.forEach { material ->
                            Button(
                                onClick = {
                                    searchSelectedRawMaterial = if (searchSelectedRawMaterial == material.name) null else material.name
                                },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = if (searchSelectedRawMaterial == material.name) MaterialTheme.colors.secondary else MaterialTheme.colors.primary
                                ),
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Text(material.name)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("最大結果数:")
                        TextField(
                            value = maxResultsText,
                            onValueChange = {
                                if (it.all { ch -> ch.isDigit() } && it.toIntOrNull() in 1..100)
                                    maxResultsText = it
                            },
                            modifier = Modifier.width(100.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                isSearching = true
                                searchMessage = ""
                                searchResults = emptyList()

                                try {
                                    val effectIds = selectedEffects.filterValues { it }.keys.mapNotNull { effectNameToId[it] }
                                    val max = maxResultsText.toIntOrNull() ?: 1
                                    val initialMaterial = rawMaterials.find { it.name == searchSelectedRawMaterial }

                                    // 🔽 非同期にバックグラウンドで実行
                                    val paths = withContext(Dispatchers.Default) {
                                        findPathsToTargetEffectsViaSimulation(
                                            baseMaterials,
                                            effectIds,
                                            maxResults = max,
                                            initialMaterial = initialMaterial
                                        )
                                    }

                                    if (paths.isEmpty()) {
                                        searchMessage = "条件を満たす組み合わせは見つかりませんでした。"
                                    } else {
                                        searchResults = paths.map { path ->
                                            path to getEffectByPath(path)
                                        }
                                    }
                                } catch (e: TimeoutCancellationException) {
                                    searchMessage = "15秒経過したため、処理を中断しました。${e.message}"
                                } finally {
                                    isSearching = false
                                }
                            }
                        },
                        enabled = !isSearching
                    ) {
                        Text(if (isSearching) "検索中..." else "検索")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text("結果:", style = MaterialTheme.typography.h6)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (searchMessage.isNotEmpty()) {
                            Text(searchMessage)
                        }

                        searchResults.forEachIndexed { index, (path, effects) ->
                            Text("パターン${index + 1}: ${path.joinToString(" -> ")} (${path.size})")
                            
                            val multiplier = calculateMultiplier(effects)
                            val formattedMultiplier = "(*%.4f)".format(multiplier)

                            FlowRow {
                                Text("効果 : ")
                                effects.forEachIndexed { i, effectId ->
                                    val name = idToEffectName[effectId] ?: "?"
                                    val color = effectAttributes[name]?.color ?: Color.Black
                                    OutlinedText(
                                        text = name + (if (i < effects.size - 1) ", " else ""),
                                        color = color,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.body1
                                    )
                                }
                                Text(" $formattedMultiplier")
                            }
                            Divider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }

                1 -> {
                    // 素材名シミュレーションタブ
                    val selectedMaterials = remember { mutableStateListOf<String>() }
                    var selectedRawMaterial by remember { mutableStateOf<String?>(null) }

                    Text("ベース素材（任意・1つのみ）", style = MaterialTheme.typography.h6)
                    FlowRow(modifier = Modifier.fillMaxWidth()) {
                        rawMaterials.forEach { material ->
                            Button(
                                onClick = {
                                    selectedRawMaterial = if (selectedRawMaterial == material.name) null else material.name
                                },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = if (selectedRawMaterial == material.name) MaterialTheme.colors.secondary else MaterialTheme.colors.primary
                                ),
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Text(material.name)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("素材を選んでください", style = MaterialTheme.typography.h6)

                    // 折り返し可能な素材ボタンリスト
                    FlowRow(modifier = Modifier.fillMaxWidth()) {
                        baseMaterials.forEach { material ->
                            Button(
                                onClick = { selectedMaterials.add(material.name) },
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Text(material.name)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 現在の素材リスト表示
                    val fullPathDisplay = buildString {
                        if (selectedRawMaterial != null) {
                            append(selectedRawMaterial)
                            if (selectedMaterials.isNotEmpty()) append(", ")
                        }
                        append(selectedMaterials.joinToString(", "))
                    }
                    Text("選択中の素材: $fullPathDisplay")

                    Spacer(modifier = Modifier.height(8.dp))

                    // 効果確認ボタン
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val fullPath = if (selectedRawMaterial != null) {
                                listOf(selectedRawMaterial!!) + selectedMaterials
                            } else {
                                selectedMaterials.toList()
                            }

                            simulationPath = fullPath
                            simulationEffects = getEffectByPath(fullPath)
                        }) {
                            Text("効果を確認")
                        }
                        Button(onClick = {
                            selectedMaterials.clear()
                            selectedRawMaterial = null
                            simulationPath = emptyList()
                            simulationEffects = emptyList()
                        }) { Text("リストをリセット") }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (simulationPath.isNotEmpty()) {
                        Text("選択した素材:", style = MaterialTheme.typography.h6)
                        Text(simulationPath.joinToString(", "))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("発現した効果", style = MaterialTheme.typography.h6)
                        
                        val multiplier = calculateMultiplier(simulationEffects)
                        val formattedMultiplier = "(*%.4f)".format(multiplier)

                        FlowRow {
                            simulationEffects.forEachIndexed { i, effectId ->
                                val name = idToEffectName[effectId] ?: "?"
                                val color = effectAttributes[name]?.color ?: Color.Black
                                OutlinedText(
                                    text = name + (if (i < simulationEffects.size - 1) ", " else ""),
                                    color = color,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.body1
                                )
                            }
                            Text(" $formattedMultiplier")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OutlinedText(
    text: String,
    color: Color,
    outlineColor: Color = Color.Black,
    fontWeight: FontWeight? = null,
    style: TextStyle = LocalTextStyle.current,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // 縁取り
        Text(
            text = text,
            color = outlineColor,
            fontWeight = fontWeight,
            style = style.copy(
                drawStyle = Stroke(
                    miter = 0f,
                    width = 2f,
                    join = StrokeJoin.Round
                )
            )
        )
        // 本体
        Text(
            text = text,
            color = color,
            fontWeight = fontWeight,
            style = style
        )
    }
}

fun calculateMultiplier(effectIds: List<Int>): Double {
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

fun main() = application{
    Window(onCloseRequest = ::exitApplication, title = "効果検索ツール") {
        App()
    }
}
