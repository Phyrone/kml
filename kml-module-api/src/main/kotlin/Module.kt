interface Module {

    suspend fun onLoad()
    suspend fun onEnable()
    suspend fun onDisable()
    suspend fun onUnload()
}