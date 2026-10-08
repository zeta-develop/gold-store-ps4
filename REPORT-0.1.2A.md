# REPORT-0.1.2A: Modelos, Contratos y Selección SAF

**Proyecto:** Gold Store PS4  
**Fase:** 0.1 — Sistema de Almacenamiento Android  
**Sub-etapa:** Incremento 0.1.2A — Modelos, Contratos y Selección SAF  
**Fecha:** 2026-10-08  
**Estado:** **COMPLETE**

---

## 1. Resumen de Implementación 0.1.2A

En esta primera etapa del incremento 0.1.2 se han establecido los fundamentos arquitectónicos y los contratos modulares para el almacenamiento:

1. **Modularización del Proyecto:**
   - Creación del submódulo `:core:model` para aislar los modelos de dominio.
   - Creación del submódulo `:core:storage` para la lógica SAF, repositorios y contratos de escaneo.
   - Integración modular en `settings.gradle.kts` y vinculación con `:app`.

2. **Modelos de Dominio (`StorageModels.kt`):**
   - `StorageType`: Identifica el tipo de almacenamiento (`INTERNAL`, `PRIMARY_EXTERNAL`, `SD_CARD`, `USB_OTG`, `UNKNOWN`).
   - `StorageSource`: Representa cada carpeta agregada mediante Storage Access Framework (SAF), con ID único, URI persistente, nombre para mostrar, tipo estimado, disponibilidad y timestamp de último escaneo.
   - `DiscoveredPackage`: Representa un archivo `.pkg` detectado. Utiliza `sizeBytes: Long` para admitir archivos superiores a 4 GB sin desbordamiento.

3. **Contratos e Interfaces Reutilizables (`StorageContracts.kt`):**
   - `StorageSourceRepository`: Contrato para consultar, añadir, eliminar y verificar permisos persistentes de fuentes de almacenamiento.
   - `PackageScanner`: Contrato para escaneo recursivo reactivo mediante Kotlin Coroutines `Flow<ScanProgress>`.
   - `PackageLibraryCatalog`: Contrato desacoplado para consulta futura desde la biblioteca y el servidor HTTP.

4. **Utilidades SAF (`SafUriHelper.kt`):**
   - `takePersistablePermissions`: Adquisición segura de permisos con `ContentResolver.takePersistableUriPermission`.
   - `hasPersistedPermission`: Comprobación de vigencia de permisos en `persistedUriPermissions`.
   - `estimateStorageType`: Inferencia no destructiva de almacenamiento interno vs microSD / USB OTG basada en patrones de URI de documento.
   - `isPkgFile`: Detección insensible a mayúsculas/minúsculas de extensiones `.pkg`.

5. **Pruebas Unitarias (`SafUriHelperTest.kt`):**
   - Validación de detección de archivos `.pkg` con múltiples variantes de mayúsculas/minúsculas.
   - Validación de soporte de tamaños superiores a 4 GB con tipo `Long`.

6. **Identidad Visual del Proyecto:**
   - Se diseñó y generó el logotipo oficial en `assets/logo.jpg`.

---

## 2. Validación de Integración Continua (GitHub Actions)

La implementación completa de 0.1.2A y su estructura multi-módulo fue verificada con éxito en GitHub Actions:
- **Ejecución del Workflow:** [Run #37820018035](https://github.com/zeta-develop/gold-store-ps4/actions/runs/37820018035)
- **Resultado:** **SUCCESS (en 3m 01s)**
- **Pruebas Unitarias:** Pasadas con éxito (`:app:testDebugUnitTest`, `:core:storage:testDebugUnitTest`).
- **Compilación de APK:** Generado exitosamente en `android/app/build/outputs/apk/debug/app-debug.apk`.
- **Enlace de Descarga APK (Run 37820018035):** [Descargar app-debug.apk](https://github.com/zeta-develop/gold-store-ps4/actions/runs/37820018035/artifacts/11568173849)
- **Enlace de Descarga Reportes de Tests:** [Descargar unit-test-reports](https://github.com/zeta-develop/gold-store-ps4/actions/runs/37820018035/artifacts/11569620122)

---

## 3. Archivos Creados o Modificados

| Archivo | Tipo | Descripción |
|---|---|---|
| `assets/logo.jpg` | Binario | Logo oficial generado para Gold Store PS4. |
| `android/core/model/build.gradle.kts` | Build | Configuración Gradle de biblioteca Android para `:core:model`. |
| `android/core/model/src/main/java/com/goldstore/core/model/StorageModels.kt` | Código | Modelos de datos `StorageSource`, `DiscoveredPackage` y `StorageType`. |
| `android/core/storage/build.gradle.kts` | Build | Configuración Gradle de biblioteca Android para `:core:storage`. |
| `android/core/storage/src/main/java/com/goldstore/core/storage/StorageContracts.kt` | Código | Interfaces `StorageSourceRepository`, `PackageScanner` y estados de progreso. |
| `android/core/storage/src/main/java/com/goldstore/core/storage/SafUriHelper.kt` | Código | Helper SAF para permisos persistentes y tipos de almacenamiento. |
| `android/core/storage/src/test/java/com/goldstore/core/storage/SafUriHelperTest.kt` | Pruebas | Pruebas unitarias de detección de extensiones y tamaños > 4GB. |
| `android/settings.gradle.kts` | Build | Registro de módulos `:core:model` y `:core:storage`. |
| `android/build.gradle.kts` | Build | Inclusión del plugin `android-library`. |
| `android/gradle/libs.versions.toml` | Config | Alias de plugin `android-library`. |
| `android/app/build.gradle.kts` | Build | Dependencias hacia `:core:model` y `:core:storage`. |

---

## 3. Decisiones Técnicas

- **Cero permisos invasivos:** No se requiere ni solicita `MANAGE_EXTERNAL_STORAGE`. Toda la interacción se basa exclusivamente en Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`) y `takePersistableUriPermission`.
- **Desacoplamiento de módulos:** La lógica de persistencia y análisis no depende de Compose ni de la capa de UI.
- **Soporte > 4 GB:** El tamaño de los paquetes PKG se maneja estrictamente con `Long` para soportar imágenes completas de juegos (10 GB - 50 GB+).
- **Inferencia tolerante:** Si un dispositivo USB OTG o microSD no proporciona identificadores estándar, el sistema recurre a `StorageType.UNKNOWN` sin interrumpir la operación.

---

## 4. Próxima Etapa

- **Incremento 0.1.2B — Escaneo, persistencia y gestión de errores:**
  - Implementación del repositorio de persistencia de URIs SAF (vía DataStore o base local).
  - Implementación de `PackageScanner` con `DocumentFile` traversal recursivo, soporte de cancelación de coroutines y manejo de errores aislados por archivo.
