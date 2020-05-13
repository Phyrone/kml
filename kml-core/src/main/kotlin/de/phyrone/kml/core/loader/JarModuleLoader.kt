package de.phyrone.kml.core.loader

import de.phyrone.kml.api.JarModuleDescription
import de.phyrone.kml.api.ModuleDescription
import de.phyrone.kml.core.ModuleContainer
import de.phyrone.kml.core.ModuleManager
import de.phyrone.kml.core.lifecycle.*
import de.phyrone.kml.core.utils.gcSuggestSTW
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.net.URL
import java.net.URLClassLoader

interface JarModuleLoaderApi {
    /**
     * @param file a jarfile or a folder with jarfiles
     */
    fun discover(file: File)

    /**
     *
     */
    fun registerModule(url: URL, description: JarModuleDescription)
}

interface JarModuleContainer<T> : ModuleContainer<T> {
    fun initialise()
    fun instance()

}

abstract class AbstractJarModuleLoader<T>(
    val manager: ModuleManager<T>,
    val separateLibs: Boolean = false,
    val parentClassloader: ClassLoader = AbstractJarModuleLoader::class.java.classLoader
) :
    JarModuleLoaderApi {

    abstract fun getDescription(file: File): JarModuleDescription
    abstract fun JarModuleContainer<T>.createModuleInstance(classLoader: ClassLoader): T?

    /**
     * @param file a jarfile or a folder with jarfiles
     */
    override fun discover(file: File) {
        require(file.exists()) { "File doesnt exist!" }
        if (file.isDirectory) {
            val files = file.listFiles { listedFile, name -> name.endsWith(JAR_FILE_ENDING, true) && listedFile.isFile }
                ?.filterNotNull() ?: throw NullPointerException("file list is null")
            files.forEach { listedFile ->
                runCatching { discverFile(listedFile) }.getOrElse {
                    logger.warn("file could not be discovered", it)
                }
            }

        } else {
            discverFile(file)
        }
    }

    override fun registerModule(url: URL, description: JarModuleDescription) {

    }

    private fun discverFile(file: File) {
        require(file.name.endsWith(JAR_FILE_ENDING, true)) { "File isn't a jar" }
        val description = getDescription(file)
        registerModule(file.toURI().toURL(), description)
    }

    companion object Static {
        private val logger = LoggerFactory.getLogger(AbstractJarModuleLoader::class.java)
        private const val JAR_FILE_ENDING = ".jar"
    }

    private inner class ContainerImpl(override val description: ModuleDescription, val url: URL) :
        JarModuleContainer<T> {
        var instanceClassLoader: EditableURLClassloader? = null
        var linkClassloader: EditableURLClassloader? = null
        var moduleInstance: T? = null
        override fun instance() {
            moduleInstance = createModuleInstance(
                instanceClassLoader ?: throw IllegalStateException("not initialised yet")
            ) ?: throw  NullPointerException("module instance failed")
        }

        override fun initialise() {
            val moduleClassloader = ModuleClassloader(parentClassloader, url)
            if (separateLibs) {
                linkClassloader = moduleClassloader
                instanceClassLoader = LibClassloader(moduleClassloader)
            } else {
                linkClassloader = moduleClassloader
                instanceClassLoader = moduleClassloader
            }
        }

        override suspend fun runModuleAction(moduleAction: ModuleAction<T>) {
            val module = moduleInstance ?: throw IllegalStateException("there is no module instance yet")
            with(moduleAction) { module.runModule() }
        }

        @Suppress("blocking")
        override suspend fun destroy() {
            instanceClassLoader?.close()
            linkClassloader?.close()
            instanceClassLoader = null
            linkClassloader = null
            gcSuggestSTW()
        }

    }
}

open class InitialiseModuleStateClass<T>(formerStates: List<String>) : ModuleState<T> {
    override val name: String = STATE_NAME
    override val possibleFormerStates = formerStates
    override val order: Order = Order.ASCENDING
    override val actions: List<Action<T>> = listOf(ActionImpl())

    companion object Static {
        const val STATE_NAME = "initialised"
    }

    private inner class ActionImpl : ModuleContainerAction<T> {
        override suspend fun ModuleContainer<T>.runModuleContainerAction() {
            if (this is JarModuleContainer<T>)
                initialise()
        }
    }
}

open class InstanceModuleStateClass<T>(formerStates: List<String>) : ModuleState<T> {
    override val name: String = STATE_NAME
    override val possibleFormerStates = formerStates
    override val order: Order = Order.ASCENDING
    override val actions: List<Action<T>> = listOf(ActionImpl())

    companion object Static {
        const val STATE_NAME = "instantiated"
    }

    private inner class ActionImpl : ModuleContainerAction<T> {
        override suspend fun ModuleContainer<T>.runModuleContainerAction() {
            if (this is JarModuleContainer<T>)
                instance()
        }
    }
}

open class DestroyModuleStateClass<T>(formerStates: List<String>) : ModuleState<T> {
    override val name: String = STATE_NAME
    override val possibleFormerStates = formerStates
    override val order: Order = Order.DESCENDING
    override val actions: List<Action<T>> = listOf(ActionImpl())//TODO("implement")

    companion object Static {
        const val STATE_NAME = "destroy"
    }

    private inner class ActionImpl : ModuleContainerAction<T> {
        override suspend fun ModuleContainer<T>.runModuleContainerAction() {
            destroy()
        }
    }
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

class ModuleClassloader(parent: ClassLoader, url: URL) :
    EditableURLClassloader(arrayOf(url), parent) {

}

class LibClassloader(parent: ModuleClassloader) : EditableURLClassloader(parent = parent)