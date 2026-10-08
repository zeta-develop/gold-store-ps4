# REPORT-FPKGI-INTEGRATION — INTEGRACIÓN DE COMPATIBILIDAD FPKGi EN GOLD STORE ANDROID

**Fecha:** 2026-10-08  
**Estado:** `COMPLETE`  
**Repositorio:** [zeta-develop/gold-store-ps4](https://github.com/zeta-develop/gold-store-ps4)  
**Workflow Run:** [#37828392261 (SUCCESS)](https://github.com/zeta-develop/gold-store-ps4/actions/runs/37828392261)  

---

## 1. Resumen de la Integración

Se ha implementado con éxito la compatibilidad completa con el formato de catálogos JSON de **FPKGi** ([ItsJokerZz/FPKGi](https://github.com/ItsJokerZz/FPKGi)) en **Gold Store Android**. 

La aplicación ahora es capaz de:
1. Importar y consumir catálogos remotos vía HTTPS que utilicen la estructura oficial `DATA` o el mapeo de categorías `CONTENT_URLS`.
2. Normalizar metadatos de paquetes: `title_id` (CUSA), `region` (USA, EUR, JPN, ALL), `name`, `version`, `release`, `min_fw`, `cover_url` y `size`.
3. Interpretar múltiples representaciones de tamaño: numéricas enteras (bytes en `Long`), números flotantes y cadenas con sufijos legibles (`GB`, `MB`, `KB`, `B`).
4. Prevenir ataques de Server-Side Request Forgery (SSRF) bloqueando esquemas no autorizados (`file://`, `ftp://`) e interfaces locales (`localhost`, `127.0.0.1`, `0.0.0.0`, redes de enlace local `169.254.x.x`).
5. Deduplicar entradas por URL de descarga directa y evitar la importación o descarga automática indiscriminada.
6. Encolar y descargar paquetes hacia almacenamiento SAF (interno, microSD o USB OTG) con progreso en tiempo real y reanudación HTTP Range.
7. Publicar automáticamente los paquetes completados en la biblioteca del servidor HTTP local embebido (`Ktor CIO`) para que la consola PS4 con GoldHEN los instale.

---

## 2. Esquema FPKGi Soportado

El adaptador `FpkgiCatalogProvider` soporta tanto el esquema nativo de FPKGi como formatos alternativos agregados:

### Estructura Oficial FPKGi (`DATA`)
```json
{
  "DATA": {
    "https://example.com/games/homebrew1.pkg": {
      "title_id": "CUSA01234",
      "region": "USA",
      "name": "Super Homebrew Bros",
      "version": "1.02",
      "release": "2024-05-01",
      "size": 4294967296,
      "min_fw": "5.05",
      "cover_url": "https://example.com/covers/cusa01234.png"
    }
  }
}
```

### Normalización de Atributos hacia el Dominio Android
| Atributo FPKGi | Atributo `CatalogItem` Android | Transformación / Manejo |
|---|---|---|
| Clave de mapa (URL) | `downloadUrl` | Validada por URL HTTP/HTTPS, deduplicada. |
| `title_id` | `titleId` | Normalizado (por defecto `CUSA00000`). |
| `name` | `name` | Sanitizado con fallback a basename de la URL. |
| `size` | `sizeBytes` (`Long`) | Conversión de números o cadenas (`1.5 GB` -> `1610612736L`). |
| `region` | `region` | Almacenado como metadato y filtrable en buscador. |
| `min_fw` | `minFw` | Incluido en la descripción técnica para verificar compatibilidad. |
| `cover_url` | `iconUrl` | URL de la portada para renderizado visual. |
| `release` | `releaseDate` | Fecha de lanzamiento original. |

---

## 3. Seguridad y Restricciones Aplicadas

- **Protección contra SSRF:** `validateSecurityUrl()` verifica que el host remoto no apunte a bucles locales, rangos privados prohibidos ni esquemas de archivos internos.
- **Límite de tamaño de catálogo:** Respuestas HTTP superiores a 20 MB son rechazadas para proteger la memoria RAM y evitar denegación de servicio.
- **Descargas intencionales:** La importación de un catálogo solo puebla la vista de la tienda; ningún paquete es descargado automáticamente sin intervención explícita del usuario.
- **Almacenamiento controlado:** Las descargas se gestionan únicamente dentro de árboles SAF previamente aprobados por el usuario con `takePersistableUriPermission`.

---

## 4. Limitaciones y Diferencias: FPKGi (PS4/PS5) vs. Gold Store (Android)

1. **Almacenamiento y Sistema de Archivos:**
   - *FPKGi:* Escribe directamente en las particiones del disco duro interno de la consola (`/data/pkg/` o `/user/app/`).
   - *Gold Store Android:* Emplea Storage Access Framework (SAF) para escribir en almacenamiento interno, microSD o pendrives USB OTG, actuando como servidor intermedio y repositorio portátil sin requerir privilegios de superusuario (`root`).
2. **Instalación:**
   - *FPKGi:* Realiza la llamada interna a los módulos del sistema PS4 (GoldHEN Package Installer API o bgft) de forma directa en el mismo dispositivo.
   - *Gold Store Android:* Descarga el paquete al dispositivo móvil y lo expone a través de un servidor HTTP local embebido con cabeceras `Accept-Ranges: bytes` y streaming 206, permitiendo que la PS4 descargue e instale el paquete por red local.

---

## 5. Resultados de Validación y Compilación

El pipeline automatizado en **GitHub Actions** confirmó la integridad de todos los módulos:

- **Ejecución:** [Workflow Run #37828392261](https://github.com/zeta-develop/gold-store-ps4/actions/runs/37828392261)
- **Estado:** `SUCCESS` (3m 38s)
- **Pruebas unitarias aprobadas:**
  - `parseFpkgiOfficialDataSchema_handlesAllFieldsAndDeduplication`: Parseo de esquema `DATA`, deduplicación, `Long` > 4 GB, portadas y fechas.
  - `parseSizeBytes_supportsVariousSizeRepresentations`: Conversión de `2.5 GB`, `512 MB`, `1024 KB` y bytes numéricos.
  - `ssrfProtection_blocksLocalhostAndInvalidSchemes`: Bloqueo de `localhost`, `127.0.0.1` y `file://`.
  - `fetchFromUrl_parsesRemoteCatalogViaMockWebServer`: Descarga y procesamiento de catálogos mediante MockWebServer.
  - `downloadTask_computesProgressPercentAccurately`: Precisión de cálculo de avance de descargas.
  - `StorageAndCatalogTest` y `SafUriHelperTest`: Persistencia DataStore y contratos SAF.
- **Artefacto generado:** `app-debug.apk` (11.97 MB) listo para instalación.
