@file:OptIn(ExperimentalLayoutApi::class)

import datas.*
import resources.*
import services.*
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Preview
fun App() {
    val allEffects = effectNameToId.keys.toList()
    val selectedEffects = remember { mutableStateMapOf<String, Boolean>() }
    allEffects.forEach { effect -> selectedEffects.putIfAbsent(effect, false) }

    var maxResultsText by remember { mutableStateOf("1") }
    var resultText by remember { mutableStateOf("") }

    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            Text("効果を選択してください", style = MaterialTheme.typography.h6)

            // FlowRowを使って横並びに折り返し表示
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
            ) {
                allEffects.forEach { effect ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = selectedEffects[effect] == true,
                            onCheckedChange = { selectedEffects[effect] = it }
                        )
                        Text(effect)
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

            Button(onClick = {
                runBlocking {
                    try {
                        withTimeout(15000L){
                            val effectIds = selectedEffects.filterValues { it }.keys.mapNotNull { effectNameToId[it] }
                            val max = maxResultsText.toIntOrNull() ?: 1
                            val paths = findPathsToTargetEffectsViaSimulation(baseMaterials, effectIds, maxResults = max)

                            resultText = buildString {
                                appendLine(paths)
                                paths.forEachIndexed { i, path ->
                                    appendLine("パターン${i + 1}: ${path.joinToString(" -> ")}")
                                    appendLine("効果 : ${getEffectByPath(path).joinToString(", ") { idToEffectName[it]!! }}")
                                }
                            }
                        }
                    }catch (e: TimeoutCancellationException){
                        resultText = "15秒経過したため、処理を中断しました。"
                    }
                }
            }) {
                Text("検索")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Text("結果:", style = MaterialTheme.typography.h6)
                Spacer(modifier = Modifier.height(8.dp))
                Text(resultText)
            }
        }
    }
}

fun main() = application{
    Window(onCloseRequest = ::exitApplication, title = "効果検索ツール") {
        App()
    }
}
