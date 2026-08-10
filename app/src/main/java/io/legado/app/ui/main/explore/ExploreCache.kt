package io.legado.app.ui.main.explore

import java.util.concurrent.ConcurrentHashMap

/**
 * 发现页类目树内存缓存
 */
object ExploreCache {
    private val cache = ConcurrentHashMap<String, ExploreTree>()

    fun get(sourceUrl: String): ExploreTree? = cache[sourceUrl]

    fun put(sourceUrl: String, tree: ExploreTree) {
        cache[sourceUrl] = tree
    }

    fun evict(sourceUrl: String) {
        cache.remove(sourceUrl)
    }

    fun clear() {
        cache.clear()
    }
}
