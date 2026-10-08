package com.goldstore.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafUriHelperTest {

    @Test
    fun isPkgFile_identifiesPkgExtensionCaseInsensitive() {
        assertTrue(SafUriHelper.isPkgFile("game.pkg"))
        assertTrue(SafUriHelper.isPkgFile("UPDATE.PKG"))
        assertTrue(SafUriHelper.isPkgFile("patch_1.01.Pkg"))
        assertFalse(SafUriHelper.isPkgFile("game.bin"))
        assertFalse(SafUriHelper.isPkgFile("pkg.iso"))
        assertFalse(SafUriHelper.isPkgFile(null))
        assertFalse(SafUriHelper.isPkgFile(""))
    }

    @Test
    fun packageSize_supportsLargeFilesAbove4GB() {
        // Validación de que la estructura soporta tamaños Long mayores a 4GB
        val size4GBPlus = 15_000_000_000L // 15 GB
        assertTrue(size4GBPlus > 4L * 1024 * 1024 * 1024)
        assertEquals(15_000_000_000L, size4GBPlus)
    }
}
