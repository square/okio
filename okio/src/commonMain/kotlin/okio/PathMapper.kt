package okio

interface PathMapper {
  fun onPathParameter(path: Path, functionName: String, parameterName: String): Path
  fun onPathResult(path: Path, functionName: String): Path

  companion object {
    val NONE = object : PathMapper {
      override fun onPathParameter(path: Path, functionName: String, parameterName: String) = path
      override fun onPathResult(path: Path, functionName: String) = path
    }
  }
}
