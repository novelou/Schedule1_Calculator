package datas

data class RecipeResult(
    val base: String,
    val materials: List<String>,
    val effectIds: List<Int>
)