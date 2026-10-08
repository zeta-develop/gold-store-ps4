# Requisitos de Entorno y Configuración en GitHub Codespaces

## 1. Stack Técnico Requerido
- **JDK:** Java 17 (OpenJDK 17)
- **Gradle:** 8.7 (gestionado mediante Gradle Wrapper `gradlew`)
- **Android SDK:**
  - `compileSdk`: 34
  - `targetSdk`: 34
  - `minSdk`: 26
  - `build-tools`: 34.0.0
- **Kotlin:** 1.9.24
- **Compose Compiler Extension:** 1.5.14
- **Compose BOM:** 2024.05.00

---

## 2. Configuración en GitHub Codespaces

Para trabajar directamente en GitHub Codespaces sin configurar el SDK manualmente en cada inicio, se incluye la definición `.devcontainer/devcontainer.json`.

El devcontainer utiliza:
- Imagen base: `mcr.microsoft.com/devcontainers/java:1-17-bookworm`
- Feature de Android SDK: `ghcr.io/devcontainers/features/android-sdk:1` con versión `34.0.0`

### Generar o regenerar Gradle Wrapper
Dentro del directorio `android/`:
```bash
gradle wrapper --gradle-version 8.7
```

### Ejecutar Pruebas Unitarias
```bash
./gradlew testDebugUnitTest
```

### Compilar APK Debug
```bash
./gradlew assembleDebug
```

---

## 3. Estado de Seguridad del Incremento 0.1.1
- Servidor se inicializa **detenido** (`ServerStatus.STOPPED`) por diseño.
- Sin dependencias de red activas en segundo plano.
- Arquitectura lista para los siguientes incrementos progresivos (SAF, Room, Ktor CIO, Foreground Service).
