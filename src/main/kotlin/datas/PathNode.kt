package datas

class PathNode(
    val parent: PathNode?,
    val materialName: String,
    val depth: Int
) {
    fun toList(): List<String> {
        val list = ArrayList<String>()
        var curr: PathNode? = this
        while (curr != null) {
            list.add(curr.materialName)
            curr = curr.parent
        }
        list.reverse()
        return list
    }
}
