interface ModuleDescription {
    val name: String
    val website: String?
    val version: String?
    val authors: List<String>?
    val dependencies: List<String>?
}
interface JarModuleDescription : ModuleDescription {
    val mainClass: String
}
