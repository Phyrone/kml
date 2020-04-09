package de.phyrone.kml.core.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.SoftReference
import java.lang.ref.WeakReference


private val gFunLock = Mutex()

/**
 * tries to let the gc execute once
 * @see System.gc
 * @see System.runFinalization
 */
suspend fun gcSuggestSTW() = gFunLock.withLock {
    System.gc()
    System.runFinalization()
}

private lateinit var testRef: SoftReference<Any>

/**
 * not recommended! you may get an OutOfMemoryError in other threads
 * after an  unsuccesful suggest it provocates a gc stw by temporary allocating max heap
 * @see OutOfMemoryError
 * @see System.gc
 * @see System.runFinalization
 */
suspend fun gcProvocateRun() = gFunLock.withLock {
    testRef = SoftReference(Any())
    System.gc()
    System.runFinalization()
    delay(300)
    if (testRef.get() != null) {
        val mem = mutableListOf<ByteArray>()
        while (true) {
            try {
                mem += ByteArray(1024 * 1024).apply { fill(Byte.MAX_VALUE) }
            } catch (e: OutOfMemoryError) {
                break
            }
        }
    }
}