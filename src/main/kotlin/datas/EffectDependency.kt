package datas

data class EffectDependency(
    val effectId: Int,
    val requiredSubEffects: MutableList<Int>,  // MutableList に変更
    val requiredMaterials: MutableList<String> // MutableList に変更
)