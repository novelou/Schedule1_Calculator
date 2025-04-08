package datas

data class RecipeRequirement(
    val effectId: Int,
    val requiredMaterial: String,
    val requiredSubEffectId: Int,
    val excludeEffectId: Int? = null,
    val includeEffectId: Int? = null
)
