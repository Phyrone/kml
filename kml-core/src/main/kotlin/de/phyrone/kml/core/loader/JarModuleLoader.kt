package de.phyrone.kml.core.loader

import de.phyrone.kml.core.ModuleManager
import java.io.File
import java.net.URL
import java.net.URLClassLoader

abstract class AbstractJarModuleLoader<T>(val manager: ModuleManager<T>,val separateLibs: Boolean) {


}

open class EditableURLClassloader(
    urls: Array<URL> = arrayOf(),
    parent: ClassLoader = EditableURLClassloader::class.java.classLoader
) : URLClassLoader(urls, parent) {
    fun addURLl(url: URL) {
        super.addURL(url)
    }

    fun addFile(file: File) {
        super.addURL(file.toURI().toURL())
    }
}

class PluginClassloader(parent: ClassLoader, pluginFile: File) :
    EditableURLClassloader(arrayOf(pluginFile.toURI().toURL()), parent) {

}

class LibClassloader(parent: PluginClassloader) : EditableURLClassloader(parent = parent)