package com.goldstore.core.network

import com.goldstore.core.model.CatalogFeed
import com.goldstore.core.model.CatalogItem

interface CatalogProvider {
    suspend fun fetchFeed(feed: CatalogFeed): List<CatalogItem>
    fun parseCatalogJson(jsonString: String): List<CatalogItem>
}
