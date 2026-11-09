# EchoStock — Wear OS App

App de smartwatch para auditoría de inventario por voz. Requisitos en `README.md`,
contrato de API en `docs/api-contract.md` (copia del repo `EchoStock-backend`, mantener sincronizado).

## Stack
- Kotlin, Jetpack Compose for Wear OS (Material 3), minSdk 30, targetSdk 34.
- Room (cola offline de auditorías), WorkManager (sincronización con restricción de red),
  Retrofit + OkHttp + kotlinx.serialization.
- Voz: `RecognizerIntent` / `SpeechRecognizer` (es-ES) · TTS: `TextToSpeech`.
- Demo en emulador Wear OS 5 (API 34). El backend se alcanza en `http://10.0.2.2:8080`.

## Comandos
- `JAVA_HOME` debe apuntar al JDK 17 (`C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot`).
- Compilar y probar: `./gradlew testDebugUnitTest assembleDebug`

## Reglas
- Cada cambio es un commit, imprescindible. Mensajes en español con Conventional Commits.
- Autor: Juan33-Felipe. Nunca añadir `Co-Authored-By` ni "Generated with Claude Code".
- Push solo cuando se pida.
- Sin teclados QWERTY (RNF-001), máximo 2 toques por interacción.
