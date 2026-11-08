# Bitácora — Caras en GIF

App instalable: https://tordenbro.github.io/caras-en-gif/ (GitHub Pages, rama main, raíz).

Artefacto publicado: https://claude.ai/artifact/JgfPjfaXpRnNsK2KRAbtoN (capacidad `downloads` declarada)

## 2026-11-08 — Versión 1

### Cambios por archivo
- `index.html` (todo en un solo archivo, sin librerías externas):
  - Decodificador GIF propio (LZW, entrelazado, transparencia, métodos de eliminación 2 y 3, retardos).
  - WebP animado mediante `ImageDecoder` (Chrome/Android); imagen fija como alternativa.
  - Vídeo: extracción a 10 fps, máximo 15 s, máximo 640 px.
  - Redimensionado a tamaño de trabajo (240/320/480 px) y fondo blanco.
  - Seguimiento de 2 puntos por plantilla (NCC) a ≤200 px: búsqueda gruesa (paso 2) y fina, subpíxel parabólico, predicción por velocidad (0,8), plantilla ancla + plantilla previa (55/45), zona de exclusión alrededor del otro punto (0,5·d), control de rigidez (distancia 0,8–1,25, giro ≤0,45 rad), la plantilla previa solo se actualiza si la puntuación del ancla es > 0,35.
  - Puntos clave manuales: seguimiento hacia delante y hacia atrás entre puntos clave, mezclado por confianza; modo sin seguimiento automático (interpolación lineal).
  - Suavizado gaussiano temporal (0–5); los puntos clave quedan fijos.
  - Pegatinas: gafas pixeladas, 6 emojis y foto propia (recorte en óvalo con borde suave si no tiene transparencia), opacidad y volteo.
  - Transformación relativa al segmento 1–2: tamaño, desplazamiento ↔/↕, giro; arrastre con un dedo y pellizco con dos.
  - Lupa al arrastrar los puntos, línea de tiempo con colores de confianza, "Ir al siguiente dudoso".
  - Codificador GIF propio: paleta global por corte de mediana (histograma de 5 bits), LZW con tabla por generación, bucle infinito, velocidad 0,25×–3×.
  - Guardado mediante `claude.use("downloads")`; si no está disponible, mantener pulsada la imagen.

### 100 % funcional (probado en Chrome sin interfaz)
- Carga de GIF y WebP animado (36 fotogramas, retardos correctos).
- Seguimiento en un GIF de prueba con movimiento, giro ±20° y escala ±15 %: error medio de 1,8 px y máximo de 3,3 px; unos 90 ms.
- Arrastre de puntos → punto clave → recálculo.
- Subir foto → recorte en óvalo automático.
- Exportación: el GIF se abre en PIL (36 fotogramas, bucle, 80 ms) y con el decodificador propio.

## 2026-11-08 — Arreglo: el botón de elegir archivo no respondía en el móvil
- `index.html`: los `input type=file` ocultos que se abrían con `.click()` se han cambiado por inputs reales y transparentes dentro de un `<label>`, en el botón "Elegir GIF" y en la casilla "Tu foto". En la app del móvil, el `.click()` programático no abría el selector de archivos.
- Probado con toques en la vista móvil: se abre el selector, carga el GIF y la foto, y la exportación funciona.

## 2026-11-08 — El selector de archivos sigue sin abrirse en la app de Claude
- Causa probable: el visor de páginas de la app de Claude en el móvil no abre el selector de archivos del sistema, aunque el input sea real. No es un fallo del código: en Chrome de escritorio y en la vista móvil de prueba funciona.
- Solución: abrir la página en Chrome del móvil con la sesión de Claude iniciada. Se ha añadido un aviso en la pantalla de inicio (edición directa del artefacto; el `index.html` local no la incluye).

## 2026-11-08 — Versión instalable (PWA) que recibe GIFs desde "Compartir" de WhatsApp
Motivo: el visor de páginas de Claude no abre el selector de archivos ni puede registrarse como destino de "Compartir". Se crea una versión web propia e instalable, pensada para publicarse en GitHub Pages (repositorio público).

### Archivos
- `index.html`: el mismo editor, más:
  - `<link rel=manifest>`, `theme-color` e iconos.
  - Registro de `sw.js`. Al abrirse con `?shared=1`, lee el archivo de la caché `caras-share` (clave `shared-file`), deduce el tipo por la extensión si falta y lo abre con `loadFile`. Con `?shared=error`, avisa.
  - Botón "Instalar app" (`beforeinstallprompt`) y aviso de cómo instalarla, que se oculta si ya está instalada (`display-mode: standalone`).
  - Exportar: botón "Compartir (WhatsApp…)" con `navigator.share({files})` cuando `canShare` lo permite, y "Guardar en el móvil" con `<a download>`. Se ha quitado la capacidad `downloads` de Claude.
- `sw.js`: intercepta `POST …/share-target`, guarda el archivo `media` en la caché y redirige con 303 a `./?shared=1`. Para los GET usa primero la red y, sin conexión, la caché (`caras-v1`).
- `manifest.webmanifest`: `share_target` (POST multipart, campo `media`, acepta `image/*`, `video/*`, .gif, .webp y .mp4), `display: standalone` y rutas relativas (sirve en un subdirectorio de GitHub Pages).
- `icon-192.png`, `icon-512.png` e `icon-maskable-512.png`: icono propio (cara con gafas pixeladas).

### 100 % funcional (probado en Chrome con servidor local bajo /caras-en-gif/)
- El service worker toma el control, el manifest no da errores y la página es instalable (el único aviso venía del modo incógnito de la prueba).
- Compartir simulado (POST multipart con un MP4 como los de WhatsApp): se abre el editor con el vídeo (29 fotogramas) y la exportación funciona.
- Funciona sin conexión tras la primera visita.

### Pendiente
- Subido a GitHub (repo público Tordenbro/caras-en-gif, archivo `.nojekyll`). Falta probar en Android: instalar → Compartir desde WhatsApp → editar → Compartir de vuelta.
- El botón "Compartir" del resultado solo aparece en móviles compatibles (Chrome de escritorio en Linux no lo ofrece).

## 2026-11-08 — App Android (APK) publicada en Releases por GitHub Actions
### Archivos
- `android/` (proyecto Gradle, Java, sin Kotlin): AGP 8.5.2, Gradle 8.9, compileSdk/targetSdk 34, minSdk 29, `androidx.webkit:webkit:1.11.0`.
  - `app/src/main/java/com/tordenbro/carasengif/MainActivity.java`:
    - WebView que sirve el editor desde `https://appassets.androidplatform.net/assets/www/index.html` con `WebViewAssetLoader`.
    - Ruta `/shared/` que entrega el archivo recibido por `ACTION_SEND`.
    - Selector de archivos (`onShowFileChooser` → `ACTION_GET_CONTENT` con `EXTRA_MIME_TYPES`).
    - Puente JS `window.Android.saveGif/shareGif(base64)`: guarda con MediaStore en Imágenes/Caras en GIF y comparte ese Uri con `ACTION_SEND image/gif`.
    - `onNewIntent` para compartir con la app ya abierta; atrás = volver en el WebView.
  - `AndroidManifest.xml`: `intent-filter SEND` para `image/*` y `video/*` (WhatsApp manda los GIF como mp4), `singleTask` y vertical.
  - Iconos: mipmap mdpi–xxxhdpi e icono adaptativo (fondo #FFCC33 + `drawable-nodpi/ic_launcher_foreground.png`).
  - `keystore/caras.jks` (alias `caras`, contraseña `carasengif`): firma fija para que cada versión se instale encima de la anterior. Está en un repo público; aceptable para uso personal.
- `.github/workflows/android.yml`: en cada push a main (sin contar cambios solo en .md) copia `index.html` e `icon-192.png` a `assets/www`, ejecuta `gradle assembleRelease` (`VERSION_CODE` = número de ejecución) y publica la release `v1.0.N` con `CarasEnGif-v1.0.N.apk` (softprops/action-gh-release@v2).
- `index.html`: si existe `window.Android`, lee lo compartido de `/shared/file?…` (nombre y tipo en la URL), muestra los botones "Compartir (WhatsApp…)" y "Guardar en la galería" del puente nativo, y no registra el service worker ni muestra el aviso de instalación.

### 100 % funcional
- Parte web con el puente simulado (Chrome sin interfaz): vídeo compartido → editor (29 fotogramas) → exportar → `shareGif`/`saveGif` reciben el GIF en base64 correcto (empieza por `R0lGODlh`, tamaño cuadra).

### Pendiente
- Compilación de GitHub Actions correcta: release `v1.0.1` publicada con el APK. Falta probar el APK en el móvil: instalar, compartir desde WhatsApp, guardar y compartir de vuelta.

## 2026-11-08 — Explorador "Mis GIFs de WhatsApp" (app Android)
### Archivos
- `android/.../MainActivity.java`:
  - `waConnect()` abre `ACTION_OPEN_DOCUMENT_TREE` con `EXTRA_INITIAL_URI` = `primary:Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Animated Gifs`. En `onActivityResult` (TREE_REQ) hace `takePersistableUriPermission` y guarda el árbol en las SharedPreferences "caras" con la clave `waTree`.
  - `waList()`, en un hilo aparte, recorre el árbol con `DocumentsContract` (profundidad ≤2; incluye Sent/Private) buscando .mp4, .gif, .webp, `video/*`, image/gif e image/webp. Ordena de más nuevo a más viejo (máximo 400) y responde con `window.onWaList({items,v})`, `{need:true}` si no hay permiso o `{error:true}`.
  - Ruta `/wa/` del asset loader:
    - `/wa/thumb/N`: miniatura JPEG de 220 px, de vídeo con `MediaMetadataRetriever.getScaledFrameAtTime` y de imagen con `ImageDecoder`; se guarda en `cacheDir/wathumb`.
    - `/wa/file/N`: el archivo original.
- `index.html`:
  - Botón "💬 Mis GIFs de WhatsApp" (solo si existe `Android.waList`).
  - Panel a pantalla completa con cuadrícula de 3 columnas (miniatura con carga diferida, fecha y hora).
  - Mensajes de permiso, carpeta vacía y error; botón "Carpeta" para cambiar de carpeta.
  - Al tocar un GIF: `fetch('/wa/file/N')` → `loadFile`.

### 100 % funcional
- Prueba de la parte web con el puente simulado: aviso de permiso → dar acceso → 2 miniaturas → tocar la primera → editor con 29 fotogramas, sin errores.

### Pendiente
- Probar en el móvil real: que la carpeta se abra sola, que salgan las miniaturas de los .mp4 de WhatsApp y que se abran.

## 2026-11-08 — Bug corregido: se perdía el rastro al cambiar de plano
- Causa: el seguimiento continuaba de un plano al siguiente y buscaba la cara del plano anterior en una escena distinta.
- `index.html`:
  - `detectCuts()`: diferencia media de gris entre fotogramas (a ≤200 px); hay cambio de plano si supera max(28, 2,8 × mediana). En el GIF del usuario el corte da 61,9 y el resto ≤18,3; el GIF de prueba sin cortes no da falsos positivos. Calcula `S.shots` y `S.shotOf`.
  - `runTracking()`: sigue cada plano por separado, solo con los puntos clave de ese plano. Un plano sin puntos clave queda como `none` (conf −2, puntos por defecto).
  - `smoothPts()` no mezcla planos distintos.
  - `drawSticker()` no dibuja nada en fotogramas `none` (tampoco al exportar). En pantalla aparece el aviso morado "Otro plano: arrastra 1 y 2 a los ojos".
  - Línea de tiempo: color morado para los planos sin puntos y raya blanca en cada corte; leyenda nueva.
  - "Ir al siguiente dudoso" salta también al primer fotograma de cada plano sin puntos.
  - Al cargar avisa de cuántos cambios de plano hay.
- 100 % funcional (prueba con el GIF del usuario): planos [0–14] y [15–20]; 15–20 en morado sin imagen; tras poner un punto clave en 15 se sigue con confianza de 0,86 a 0,93.

## 2026-11-08 — "Mis pegatinas" (biblioteca guardada), stickers de WhatsApp y compartir imágenes
### Archivos
- `index.html`:
  - IndexedDB `caras-en-gif`, almacén `stk` `{id, blob PNG ≤600 px, oval, w, u, v, created}`.
  - `addToLibrary(blob)`: óvalo automático si la imagen no tiene transparencia.
  - La lista de pegatinas = las incluidas + `LIB` + "Añadir imagen" (subida múltiple) + "De WhatsApp" (solo en la app). La selección se guarda por clave (`selKey`), ya no por índice.
  - `persistSel()` guarda tamaño, posición y óvalo de cada pegatina (con retardo de 500 ms) al tocar los deslizadores, arrastrar, pellizcar o cambiar el óvalo.
  - Botón "Borrar de mis pegatinas" (modo edición con ✕).
  - El panel de WhatsApp tiene dos modos, `gifs` y `stickers`. En stickers: fondo de cuadros, varios a la vez, "✓ Añadido"; al pulsar "Listo" vuelve a la pestaña Imagen con el último elegido.
  - Compartido: lee `/shared/0..n-1`. Si todo son imágenes (no GIF), pregunta con el diálogo `ask()` (sin `confirm`): guardar en Mis pegatinas o, si es una sola, editarla como GIF.
- `MainActivity.java`:
  - `waList(kind)` y `waConnect(kind)`, con la carpeta inicial "WhatsApp Animated Gifs" o "WhatsApp Stickers". El permiso se guarda por tipo en `waTree_<kind>` (el antiguo `waTree` sigue valiendo para gifs). Callbacks con `kind`.
  - En stickers, filtro .webp/.png. Miniaturas en PNG para imágenes (conserva la transparencia) y JPEG para vídeos.
  - `ACTION_SEND_MULTIPLE` (máximo 50): `sharedUris` → `/shared/N`, con el Content-Type del ContentResolver.
- `AndroidManifest.xml`: `intent-filter SEND_MULTIPLE image/*`.

### 100 % funcional (prueba con el puente simulado)
- Compartir 2 imágenes → pregunta → 2 en Mis pegatinas; siguen tras recargar.
- Elegir una pegatina y cambiar el tamaño a 5 → se mantiene tras recargar.
- "De WhatsApp" → tocar un sticker → "✓ Añadido" → "Listo" → queda seleccionado.
- Borrar → 2 → sigue en 2 tras recargar. Sin errores.

### Pendiente
- Probar en el móvil: carpeta WhatsApp Stickers y compartir desde Sticker Maker o la galería.
- Los stickers animados se usan como imagen fija (solo el primer fotograma).

## 2026-11-08 — Bug corregido: con caras pequeñas la imagen se movía y no se centraba (GIF de fútbol)
- Causas:
  1. El seguimiento iba a ≤200 px, así que una cabeza de unos 12 px quedaba en unos 7.
  2. Plantillas de 9×9 por punto con los dos puntos casi juntos.
  3. El suavizado (ventana asimétrica en los extremos y promedio sin límite) se retrasaba en los movimientos rápidos: el error real era de 1–3 px, pero tras suavizar llegaba a 17 px.
- `index.html`:
  - Escala de grises a la resolución completa de trabajo (`S.ts=1`).
  - `makeTpl`/`score2` con paso de muestreo (`st`) para plantillas grandes (unas 23×23 muestras).
  - `trackSeq` elige el método según la distancia entre los puntos clave:
    - **d ≥ 22 px → `trackEyes`:** dos puntos independientes como antes, más un rescate: si los dos puntos se parecen menos de 0,35 a la original, busca la cabeza en un radio 3R con la plantilla del centro y desplaza el par.
    - **d < 22 px → `trackHead`:**
      - Sigue la cabeza como bloque: Hc = clamp(0,8·d, 11, 40) y R = clamp(0,9·d, 18, 60).
      - La plantilla "previa" solo se renueva si se parece más de 0,5 a la original.
      - Si sale poco parecido, prueba sin predicción y luego un rescate amplio (2,5R, máximo 110) solo con la original.
      - Con caras grandes ajustaría además el giro y el tamaño con los dos puntos, pero `trackEyes` ya cubre ese caso.
  - `smoothPts()`: ventana simétrica (sin retraso al principio y al final del plano) y corrección limitada a max(1,5 px; 0,12·d) respecto a la posición seguida.
- Pruebas (Chrome sin interfaz):
  - GIF sintético difícil (cabeza de unos 14 px, cámara en movimiento, desenfoque, compresión, otra cabeza oscura al lado): de 4,3 px de media y 18,9 de máximo a **2,2 px de media y 4,2 de máximo**, ningún fotograma por encima de 8 px.
  - Cara grande que gira: de 1,8 a **1,5 px de media** (máximo 2,2).
  - GIF del usuario con cambio de plano: confianza 0,89–1.
- Pendiente: probar con el GIF de fútbol real del usuario.

## 2026-11-08 — Bug corregido: las casillas de stickers y GIFs de WhatsApp se montaban unas encima de otras
- Causa: casillas con `aspect-ratio` dentro de un `display:grid`; en el WebView del móvil la altura de las filas salía mal, así que las casillas se solapaban y las imágenes quedaban cortadas en tiras.
- `index.html`:
  - `#waGrid` ahora usa un contenedor `.tiles` (flex-wrap) con casillas `div.wa` de tamaño fijo `calc((100vw - 36px)/3)` (máximo 200 px), borde visible e imagen en `position:absolute`.
  - Stickers: fondo blanco, `object-fit:contain` al 88 % y borde verde con "✓ Añadido" al elegirlos. GIFs: `cover` y fecha abajo.
  - Pestaña Imagen (`.stk`): mismo arreglo, sin `aspect-ratio`; ancho y alto fijos `calc((min(100vw,640px) - 72px)/4)`.
- Comprobado a 390 px de ancho: 14 stickers y 14 GIFs en cuadrícula de 3 sin solaparse; la pestaña Imagen en cuadrícula de 4.

### Pendiente / ideas
- Probar en un móvil real (gestos, `ImageDecoder`, guardado desde la app de Claude).
- Conservar la transparencia de los GIF de origen (ahora se aplana sobre blanco).
- Paleta por fotograma o difuminado opcional para vídeos con degradados.
- Varias pegatinas o varias caras a la vez.
- Detección automática de la cara: no es posible en esta página porque la política de seguridad bloquea la descarga de modelos (MediaPipe).
