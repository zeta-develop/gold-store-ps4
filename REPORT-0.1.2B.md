# REPORT-0.1.2B: Persistencia, Escaneo y Gestión de Errores

**Proyecto:** Gold Store PS4  
**Fase:** 0.1 — Sistema de Almacenamiento Android  
**Sub-etapa:** Incremento 0.1.2B — Persistencia, Escaneo y Gestión de Errores  
**Fecha:** 2026-10-08  
**Estado:** **COMPLETE (Validado en CI)**  
*(Validación en dispositivos Android físicos de USB OTG / microSD pendiente para pruebas de integración con hardware real).*

---

## 1. Resumen de Implementación 0.1.2B

Se completaron las capacidades de persistencia y escaneo de almacenamiento seguro cumpliendo todas las directivas:

1. **Persistencia mediante DataStore (`DataStoreStorageSourceRepository.kt`):**
   - Serialización resiliente de fuentes SAF a JSON almacenado en DataStore Preferences.
   - Prevención de duplicados por identidad de URI.
   - Preservación de fuentes aunque se desconecte la unidad (no se borran automáticamente fuentes inaccesibles).
   - Verificación de permisos persistentes mediante `SafUriHelper.hasPersistedPermission`.
   - Liberación de permisos (`releasePersistableUriPermission`) únicamente cuando la URI ya no esté referenciada por ninguna otra fuente.

2. **Escaneo Recursivo y Cooperativo (`DocumentFilePackageScanner.kt`):**
   - Recorrido de árboles de almacenamiento SAF mediante `DocumentFile`.
   - Detección insensible a mayúsculas/minúsculas de `.pkg`.
   - Uso de `Long` para soportar paquetes superiores a 4 GB.
   - Ejecución asíncrona sobre `Dispatchers.IO` y emisión reactiva de progreso mediante `Flow<ScanProgress>`.
   - Cancelación cooperativa con `currentCoroutineContext().ensureActive()` (sin dejar estados inconsistentes).
   - Manejo de excepciones individuales por archivo o subdirectorio para evitar interrumpir la exploración completa.
   - Prevención de ciclos de directorios mediante un set de identidades documentales URI.

3. **Catálogo en Memoria Resiliente (`MemoryPackageLibraryCatalog.kt`):**
   - Mapeo desacoplado de paquetes por cada fuente de almacenamiento.
   - Deduplicación por identidad documental de URI.
   - Conservación del último catálogo conocido ante desconexión física de almacenamiento, cambiando el estado a `isAvailable = false` en lugar de destruir los metadatos.

4. **Pruebas Unitarias Integrales (`StorageAndCatalogTest.kt` y `FakePreferencesDataStore.kt`):**
   - Serialización y deserialización completa de fuentes y metadatos.
   - Prevención de duplicados de URIs.
   - Preservación y marcado de disponibilidad de paquetes ante desconexión de USB OTG/microSD.
   - Paquetes de más de 4 GB (`sizeBytes > 4GB`).

---

## 2. Archivos Creados o Modificados

| Archivo | Tipo | Descripción |
|---|---|---|
| `android/gradle/libs.versions.toml` | Build | Añadidas librerías `androidx-datastore-preferences`, `androidx-documentfile` y `kotlinx-coroutines-test`. |
| `android/core/storage/build.gradle.kts` | Build | Inclusión de dependencias de DataStore y DocumentFile en `:core:storage`. |
| `android/core/storage/.../DataStoreStorageSourceRepository.kt` | Kotlin | Implementación de repositorio con persistencia en DataStore y control de permisos SAF. |
| `android/core/storage/.../DocumentFilePackageScanner.kt` | Kotlin | Escáner recursivo no bloqueante con manejo de errores y cancelación. |
| `android/core/storage/.../MemoryPackageLibraryCatalog.kt` | Kotlin | Catálogo en memoria que preserva resultados ante desconexión. |
| `android/core/storage/.../FakePreferencesDataStore.kt` | Test | Doble de prueba para DataStore Preferences en memoria. |
| `android/core/storage/.../StorageAndCatalogTest.kt` | Test | Pruebas unitarias completas de serialización, deduplicación y desconexión. |
| `.github/workflows/android-ci.yml` | CI | Actualización de `actions/setup-java@v5` y optimización de caché. |

---

## 3. Decisiones Técnicas

- **DataStore Preferences:** Para almacenar la lista de carpetas SAF autorizadas de forma ligera y asíncrona sin requerir una base de datos pesada antes de la fase de biblioteca formal.
- **Tolerancia a fallos:** El escaneo no aborta si una subcarpeta tiene permisos denegados o si un archivo está corrupto; registra el error, incrementa el contador de fallos y continúa con el resto del árbol.
- **Soporte > 4 GB:** El tamaño de los archivos se mapea desde `child.length()` a `Long` directamente.

---

## 4. Validación de Integración Continua (GitHub Actions)

- **Workflow:** `.github/workflows/android-ci.yml`
- **Tareas ejecutadas:**
  - `./gradlew testDebugUnitTest --no-daemon`
  - `./gradlew assembleDebug --no-daemon`
  - Carga de artefactos `app-debug` y `unit-test-reports`.
