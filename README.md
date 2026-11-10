# EchoStock

## Descripción del Proyecto 
**EchoStock** es una aplicación de software diseñada específicamente para dispositivos SmartWatch, dirigida a trabajadores de grandes almacenes y centros de distribución. 

---

## Requisitos Funcionales (FR)

* **RF-001 - Búsqueda por Comandos de Voz:** El sistema debe permitir al usuario dictar el nombre de un producto, SKU o código de ubicación, convirtiendo el audio a texto para buscar el ítem en la base de datos.
* **RF-002 - Lectura de Estado por Audio (TTS):** El sistema debe sintetizar a voz (Text-to-Speech) la cantidad registrada en el sistema ("Sistema indica 50 unidades de cajas tipo A") para que el operario no tenga que mirar la pantalla.
* **RF-003 - Confirmación Rápida (Tap):** La interfaz gráfica debe mostrar un botón primario de pantalla completa o deslizable (Swipe) para confirmar que el conteo físico coincide con el sistema.
* **RF-004 - Registro de Discrepancias:** Si hay diferencia de inventario, la interfaz gráfica debe mostrar selectores rápidos (+ / -) u opciones numéricas grandes para registrar la cantidad real.
* **RF-005 - Sincronización con Backend (ERP/WMS):** El reloj debe enviar en tiempo real la confirmación o discrepancia al servidor central mediante una API REST/GraphQL.
* **RF-006 - Modo Offline :** El sistema debe almacenar las auditorías localmente en el reloj si se pierde la conexión WiFi en áreas aisladas del almacén, y sincronizar automáticamente al recuperar señal.

---

## Requisitos No Funcionales (NFR)

* **RNF-001 - Usabilidad (Micro-interacciones):** Ninguna interacción táctil debe requerir más de 2 toques (taps) en la pantalla. La interfaz gráfica no debe usar teclados QWERTY virtuales bajo ninguna circunstancia.
* **RNF-002 - Accesibilidad Visual:** La interfaz debe operar con un contraste alto adaptado a condiciones de baja iluminación o deslumbramiento en almacenes, usando códigos de color universales (ej. Verde = Correcto, Rojo = Discrepancia).
* **RNF-003 - Retroalimentación Háptica:** Cada confirmación de voz entendida o botón presionado debe generar un patrón de vibración específico en el reloj para confirmar el éxito de la acción sin mirar.
* **RNF-004 - Tolerancia al Ruido (Voice-to-Text):** El procesamiento de voz debe ser capaz de filtrar ruido de fondo moderado típico de un almacén (maquinaria, carretillas).
* **RNF-005 - Eficiencia de Batería:** La aplicación debe gestionar el encendido de pantalla y el uso del micrófono para garantizar que el smartwatch no consuma más del 10% de batería por hora de auditoría activa.

---

## App Wear OS

App nativa para smartwatch Wear OS (Kotlin + Jetpack Compose for Wear OS). Se comunica con
[EchoStock-backend](https://github.com/Juan33-Felipe/EchoStock-backend) mediante la API REST descrita
en [`docs/api-contract.md`](docs/api-contract.md).

### Flujo del operario

```
 Inicio ──(1 toque: dictar)──► Producto ──(1 toque: ✓ Coincide)──► Confirmado (verde)
   │                             │
   │ varias coincidencias        └─(1 toque: ✎)──► Conteo real ──(1 toque: Registrar)──► Discrepancia (rojo)
   ▼                                                 (+/−, corona o voz)
 Resultados ──(1 toque)──► Producto
```

Al abrir un producto el reloj lee en voz alta lo que indica el sistema; al terminar vuelve solo al inicio.

### Cómo se cumple cada requisito

| Requisito | Implementación |
|---|---|
| RF-001 Búsqueda por voz | Reconocimiento **dentro del reloj** con [Vosk](https://alphacephei.com/vosk/) (modelo español, sin internet): un toque y escucha, transcripción en vivo y corte automático al terminar la frase. `VoiceText` normaliza números en palabras, nombres de letras ("se jota a" → `CJA`) y palabras de relleno ("pasillo", "sku"). Si hay un ganador claro se abre directamente el producto. Respaldo: el reconocedor del sistema. |
| RF-002 Lectura por audio | `Speaker` (TextToSpeech): *"Cajas tipo A, pasillo A, estante 1, nivel 3. Sistema indica 50 cajas."* Tocar la cifra la repite. |
| RF-003 Confirmación rápida | Botón verde grande "Coincide": un toque. |
| RF-004 Discrepancias | Pantalla de conteo con +/− grandes (mantener pulsado repite), corona giratoria y dictado del número ("cuarenta y ocho"). |
| RF-005 Sincronización | `AuditRepository` envía `POST /audits` en tiempo real; el servidor es idempotente por `clientAuditId`. |
| RF-006 Modo offline | Toda auditoría se guarda primero en Room. Sin servidor queda en cola y `WorkManager` la envía por lotes (`POST /audits/batch`) al volver la red. La búsqueda usa el catálogo cacheado con las mismas reglas. |
| RNF-001 Máx. 2 toques | Toda acción es de 1 toque; ninguna pantalla tiene teclado. |
| RNF-002 Alto contraste | Fondo negro puro, texto blanco, verde = correcto, rojo = discrepancia, ámbar = sin conexión. Pantalla de resultado a color completo. |
| RNF-003 Háptica | Patrones distintos en `Haptics`: toque, voz entendida (2 pulsos), confirmado (1 largo), discrepancia (3), en cola (largo-corto), error (zumbido). Cada vibración pinta además un anillo de color en el borde (visible en el emulador). |
| RNF-004 Ruido | Vocabulario cerrado (`VoiceGrammar`): el reconocedor solo puede producir palabras del catálogo, números y letras; el resto cae en `[unk]`. Además se buscan las 3 mejores hipótesis y se queda la que mejor encaja; la coincidencia aproximada (Levenshtein) tolera erratas. |
| RNF-005 Batería | Micrófono solo durante el dictado (máx. 8 s); pantalla encendida forzada solo al contar; sin sondeos: la sincronización espera a que haya red (restricción de WorkManager); timeouts de red cortos. |

### Arquitectura

```
ui/        Compose for Wear OS: pantallas, AuditViewModel (estado del flujo)
domain/    Lógica pura y testeada: VoiceText, ProductMatcher, Phrases, modelos
data/      ProductRepository, AuditRepository, Room (caché y cola), Retrofit, WorkManager
platform/  Voz a texto, texto a voz, vibración
```

### Ejecutar

Requisitos: Android Studio (o SDK + JDK 17) y el backend corriendo en el PC en el puerto 8080.

1. Arranca el backend (ver su README).
2. Crea un emulador **Wear OS 5 (API 34) – Large Round** y en *Extended controls → Microphone* activa
   *"Virtual microphone uses host audio input"* para dictar con el micrófono del PC.
3. `./gradlew installDebug` y abre **EchoStock** en el reloj. La primera compilación descarga el modelo
   de voz en español (~40 MB) a `.cache/` (no se versiona).

> El emulador de Wear OS no trae motor de texto a voz: lo que el reloj "dice" aparece también como
> subtítulo en la parte superior, y la vibración se ve como un anillo de color en el borde.

**Probar la voz sin micrófono (solo builds debug):** un WAV PCM 16 kHz mono copiado en
`files/voice-inject.wav` de la app se usa como entrada del siguiente dictado:

```
adb push frase.wav /data/local/tmp/v.wav
adb shell run-as com.echostock.wear cp /data/local/tmp/v.wav files/voice-inject.wav
```

Desde el emulador el backend se alcanza en `http://10.0.2.2:8080` (configurable en `app/build.gradle.kts`).
Para probar el modo offline basta con detener el backend: las auditorías se guardan en el reloj y se
envían solas al volver a levantarlo.

### Pruebas

```
./gradlew testDebugUnitTest
```

Pruebas unitarias de normalización de voz, ranking de búsqueda, frases, y de los repositorios
contra un servidor HTTP simulado (MockWebServer): envío en tiempo real, duplicados, rechazos,
cola offline y sincronización por lotes.
