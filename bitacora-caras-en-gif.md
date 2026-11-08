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

## 2026-11-08 — Seguimiento nuevo por flujo óptico (sigue los píxeles donde se clavan A y B)
- Problema real del usuario (GIF de fútbol, cabeza pequeña, cámara en movimiento): la imagen se iba de la cabeza y la línea de tiempo lo marcaba en verde. Reproducido con una prueba semirreal: cara real del primer GIF reducida a unos 9 px entre ojos sobre césped, con cámara en movimiento, desenfoque, compresión, otra cabeza oscura cerca y público. La v1.0.7 daba 30,1 px de error medio y 48,5 de máximo, sin ningún fotograma en rojo.
- `index.html`:
  - Escala de grises para seguir a mayor resolución que la de trabajo: hasta 560 px desde el original (`S.ts=TW/W`, `Uint8Array`); `S.pyr` es una caché de pirámides (máximo 6).
  - `buildPyr` (hasta 4 niveles, suavizado 1-2-1), `bil` (interpolación bilineal) y `lkTrack` (Lucas-Kanade piramidal con ventana `win`, filtro de autovalor mínimo `LK_MIN_EIG=2` y 12 iteraciones).
  - `cloudFlow`: 25 píxeles alrededor del punto (radio `rc`=0,3·d, entre 3 y 12), ida y vuelta (FB); se quedan los de error FB ≤ max(mediana, 0,5) y < 3 px, y se usa la mediana de su desplazamiento.
  - `trackSeq` (sustituye a trackHead y trackEyes):
    - nubes en A y B por separado (control de rigidez: distancia 0,85–1,18, giro < 0,3 rad);
    - hipótesis "misma velocidad" y "quieta" (la segunda solo si la primera no convence);
    - plan B con búsqueda NCC en el nivel 1/4 (radio max(3·hb, 80) px y prioridad a la trayectoria), que solo gana si puntúa claramente más;
    - selección por `q` = 0,5·FB + 0,5·parecido − penalización por alejarse de la trayectoria;
    - ajuste fino de ±2 px y rescate amplio con la plantilla original (≥0,4 y +0,2).
  - `scoreWarp`: parecido con la "foto" original girada y escalada según A-B (solo si d ≥ 16 px; con puntos muy juntos el giro tiene demasiado ruido).
  - Confianza = min(0,6·FB + 0,4·parecido, 0,25 + parecido), más bajada si el resultado se aleja de la predicción. Ya no salen rojos falsos en la cara que gira.
  - Vídeos a 15 fps (antes 10), máximo 12 s.
- Resultados (Chrome sin interfaz; error del centro):
  - Cara real pequeña: **2,3 px de media, máximo 3,6** (antes 30,1 / 48,5).
  - Vídeo 640×360 de 7,5 s con fotogramas repetidos y saltos de 45 px: **2,4 / 4,2**.
  - Cabeza tapada en 4 fotogramas: se recupera (2,6 px después).
  - Puntos casi juntos 2,1; separados 3,1; empezando a mitad 2,4.
  - Fútbol sintético 2,4 / 4,3; cara grande que gira 2,1 / 3,9, sin rojos.
  - Planos, pegatinas y panel de WhatsApp sin cambios.
- Pendiente: probar con el GIF real de fútbol del usuario. Si en algún fotograma extremo se pierde, se corrige poniendo un punto clave ahí (se sigue en las dos direcciones).

### Pendiente / ideas
- Probar en un móvil real (gestos, `ImageDecoder`, guardado desde la app de Claude).
- Conservar la transparencia de los GIF de origen (ahora se aplana sobre blanco).
- Paleta por fotograma o difuminado opcional para vídeos con degradados.
- Varias pegatinas o varias caras a la vez.
- Detección automática de la cara: no es posible en esta página porque la política de seguridad bloquea la descarga de modelos (MediaPipe).

## 2026-11-08 — Seguidor nuevo de la cabeza: filtro de correlación que aprende (tipo CSRT/KCF)
- Problema real del usuario (clip de fútbol): hacia el fotograma 30 la cámara se mueve, el jugador se levanta y gira la cara; la imagen se iba a la izquierda y acababa fuera de cuadro. Causa: el seguimiento por flujo óptico usaba nubes de píxeles muy pequeñas y una plantilla fija del punto clave, así que al girar la cabeza o al moverse el fondo se despegaba (24–30 px en una cabeza de unos 40 px).
- Datos de prueba:
  - Grabación de pantalla del usuario → fotogramas reconstruidos por el contador «N/91» (OCR). Del 39 al 90 la cabeza se ve limpia; la posición real se tomó con CSRT de OpenCV en las dos direcciones y se revisó a ojo.
  - Vídeo original del usuario (1080×1080, recortado con un editor), a 15 fps y 560 px: fotogramas 0–60 con la posición real (CSRT revisado a ojo).
  - Vídeo sintético que gira ±25°, cambia de tamaño ±15 % y se desplaza (para medir el giro).
- `index.html`:
  - Se quitan `makeTpl`, `score2`, `searchPt`, `lkTrack`, `cloudFlow`, `scoreWarp`, `median` y el `trackSeq` antiguo. Se mantienen `bil`, `buildPyr` y `getPyr`.
  - FFT propia 32×32 (`fft1`, `fft2`).
  - `dcfFeat`: ventana cuadrada (2,5 × la caja de la cabeza, que puede estar girada) remuestreada a 128×128 desde el nivel de la pirámide adecuado. 12 canales por celda de 4×4 px: HOG de 9 orientaciones normalizado, luz media y dos de color (Cb/Cr del fotograma a color), con ventana de Hann.
  - `dcfNew`, `dcfTrain` y `dcfDetect`: filtro de correlación multicanal (A = conj(Y)·X, B = Σ|X|², λ = 0,02·media(B)); pico con subpíxel y PSR (lo que destaca el pico) como medida de confianza.
  - `trackSeq`:
    - caja = máx(1,6·d + 14; 10 % del lado menor), entre 24 px y el 60 % del cuadro;
    - en cada fotograma prueba inclinación ±0,07 rad (solo si d ≥ 16 px) y, uno sí y otro no, tamaño ±4 %;
    - aprende al 5 % (o al 2,5 % si el pico es flojo) y no aprende si el PSR ≤ 4,5;
    - modelo del punto clave guardado aparte (`anchor`);
    - si el PSR < 5, busca en 9 posiciones alrededor con el modelo actual y con el del punto clave;
    - **modo perdido**: si el PSR < 7 y tampoco se parece a la cabeza del punto clave (PSR del ancla < 5), deja de moverse y de aprender, lo marca en rojo (conf 0,05) y busca en 25 posiciones hasta encontrar algo con PSR ≥ 8;
    - los puntos 1 y 2 se colocan con su posición relativa al centro, girada y escalada como la cabeza;
    - confianza = (PSR − 3,5)/6.
- Resultados (error del centro respecto a la posición real):
  - Clip del usuario 40→90: **1,4 px de media, 3,4 de máximo** (antes 24,8 / 32,4). 90→39: 1,5 / 6,1 (antes 17,6 / 168,8).
  - Original 0→60: 2,7 / 6,1 (antes 12,3 / 26,2). 60→0: 2,3 / 7,7 (antes 13,2 / 24,0).
  - Puntos casi juntos (d = 6) o separados (d = 50): sin pérdidas.
  - Giro sintético: 1,1° de media, 3,2° de máximo; posición 0,5 px.
  - En la app (Chrome sin interfaz, vídeo a 15 fps, trabajo a 320 px): 3,6 px de media, sin errores de JavaScript, unos 20 ms por fotograma. Un GIF liso no da NaN (sale en rojo).
- Limitación conocida: en el original editado hay un salto de zoom en el fotograma 61 que deja la cabeza fuera de cuadro. La app lo detecta como cambio de plano. Si no lo detectara, el seguidor se quedaría en el hombro, que en ese momento es lo que más se parece. Se arregla poniendo un punto clave después del salto.
- Pendiente: probar en el móvil con el GIF real de WhatsApp (formato ancho) poniendo el punto clave en el fotograma 0.

## 2026-11-08 — Tamaño estable entre puntos clave y «lo que va por delante» (mano)
### Bug corregido: la imagen pasaba de pequeña a gigante (GIF de la cama)
- Causa: el tamaño de la imagen era proporcional a la separación de 1 y 2 en *cada* punto clave. El usuario los puso casi juntos en el fotograma 1 y más separados en el siguiente punto clave, así que la imagen saltaba de tamaño. (El seguidor sí medía bien el acercamiento real de la cámara: ×1,22 frente a unos ×1,25 de verdad.)
- `index.html`:
  - `normPts(kp,size,angFb)`: unos puntos de usuario se convierten en un segmento con su punto medio y su inclinación (si están a menos de 6 px se usa la inclinación de reserva), con la longitud indicada.
  - `runTracking()` reescrito por planos. El primer punto clave del plano fija el tamaño de referencia. Cada tramo se sigue hacia delante **hasta el siguiente punto clave incluido**; el tamaño estimado allí (si la confianza es ≥ 0,4) es el que se usa en ese punto clave, con la posición e inclinación del usuario. Después se sigue hacia atrás desde ahí y se mezclan los dos sentidos como antes. Sin seguimiento automático: todos los puntos clave usan el tamaño de referencia.
  - `keyNorm(f,kp)`: lo mismo mientras se arrastra (el primer punto clave del plano sí cambia el tamaño).
  - `dispPts(f)`: los puntos que se dibujan y se arrastran en un punto clave son los del usuario; la imagen usa los normalizados (`S.pts`).
  - `S.trackTotal` calculado exacto con el nuevo orden; la barra de progreso no pasa del 100 %.
  - Texto nuevo en la pestaña Puntos sobre el tamaño.
- Cambios de plano: cada plano toma el tamaño de su primer punto clave (el usuario lo pidió así: en otro plano la cabeza tiene otro tamaño).
- Prueba (Chrome sin interfaz, GIF de la cama, puntos clave en 0 con d = 20 y en 10 con d = 61): el tamaño pasa de 19,8 a 23,4 poco a poco (antes ×3 de golpe). Arrastrar el punto 2 lejos en el fotograma 30 deja el tamaño en 23,2.

### Nuevo: «✋ Lo que va por delante» (pestaña Ajustar)
- El usuario pinta con el dedo la mano (u otro objeto) en un fotograma. Esa zona se sigue en las dos direcciones dentro de su plano y, en cada fotograma, se vuelven a dibujar encima de la imagen los píxeles originales del GIF.
- `index.html`:
  - `S.fronts` (fotograma → lienzo de máscara W×H) y `S.frontTr` (fotograma pintado → seguimiento por fotograma `{cx,cy,k,th,conf,sim}`).
  - `paintLine` (pincel con grosor en px de pantalla; goma con `destination-out`) y `maskBox`.
  - `trackFronts()`: usa `trackSeq` con su propio identificador de ejecución (`opt.key='frontRun'`, sin tocar la barra de progreso). Los puntos de partida se eligen para que la caja del seguidor mida lo mismo que lo pintado.
  - `maskSamples` + `simTo`: correlación de color entre lo pintado y lo que hay en esa zona en cada fotograma, menos una penalización por diferencia de tono medio. Solo va por delante si conf ≥ 0,4 y parecido ≥ 0,5. En el GIF de la cama: con la mano 0,53–1, sin la mano ≤ 0,39.
  - `frontAt(f)` elige la zona pintada mejor seguida. `drawFront(x,f,tint)` usa un lienzo aparte: máscara transformada con desenfoque de 1 px y `source-in` con el fotograma (o tinte rosa mientras se pinta).
  - Se dibuja en la vista y en la exportación (después de la pegatina).
  - Modo pintar: botones Goma, «Borrar lo pintado aquí» y Listo, y deslizador de grosor. Se sale del modo al cambiar de pestaña o al cargar otro GIF.
  - `trackSeq(seq,start,rid,opt)`: parámetro opcional para la clave de cancelación y para no mover la barra de progreso.
- Prueba (GIF de la cama, mano pintada en el fotograma 72): va por delante en 20 de 82 fotogramas (62–81, que es cuando aparece la mano); los dedos tapan el borde de la imagen como en el original. La exportación funciona (82 fotogramas, 1,6 MB). Sin errores de JavaScript.

### Pendiente
- Probar en el móvil: pintar con el dedo, rendimiento del desenfoque al reproducir.
- La forma pintada es fija (se mueve, gira y escala); si la mano cambia mucho de forma, hay que pintarla en más fotogramas.

## 2026-11-08 — Recortar cabezas o cuerpos al añadir fotos (solo app Android)
### Decisión técnica
- Estándar del sector: segmentación de personas con un modelo de aprendizaje automático en el propio dispositivo (ML Kit / MediaPipe) + detector de caras. Cuello de botella habitual: pesar poco, funcionar sin internet y no tardar. Descartado ONNX/MediaPipe en la página (los runtimes WASM y los modelos no se pueden descargar aquí ni la página puede traerlos con seguridad) y descartado hacerlo "a mano" en JavaScript (U²-Net en JS puro tarda decenas de segundos).
- Elegido: ML Kit incluido en el APK (≈ +12 MB): `segmentation-selfie:16.0.0-beta6` (máscara de persona) y `face-detection:16.1.7` (caja y ojos). Sin internet.

### Archivos
- `android/app/build.gradle`: dependencias `com.google.mlkit:segmentation-selfie:16.0.0-beta6`, `com.google.mlkit:face-detection:16.1.7` y `play-services-tasks:18.2.0`.
- `android/.../Cutout.java` (nuevo):
  - Reduce la foto a ≤1600 px, saca la máscara (opacidad = smoothstep(0,4–0,8) de la confianza) y las caras (modo preciso, ojos, tamaño mínimo 4 %).
  - Cabeza: por cada cara, región = caja ampliada (±0,6·ancho, 0,9·alto por arriba para el pelo, 0,25·alto por debajo de la barbilla) ∩ máscara; fundido del cuello entre barbilla+0,02·alto y barbilla+0,25·alto; con varias caras, cada píxel va a la cara más cercana (distancia normalizada). Se descarta una cara si casi no hay persona (<15 % del área de la caja).
  - Cuerpo: componentes conexos de la máscara (4-vecinos); se quedan los de área ≥ max(1 % de la imagen, 12 % del mayor), máx. 6, de izquierda a derecha. Si hay una cara dentro, se guardan sus ojos.
  - Cada recorte sale con transparencia, recortado a su caja y ≤700 px, con metadatos: ancho/distancia entre ojos (`wE`), posición del punto medio de los ojos respecto al centro (`eu`, `ev`, en distancias entre ojos) e inclinación (`ang`).
- `android/.../MainActivity.java`: puente `Android.cutout(id, jpegBase64, 'head'|'body')` (hilo único aparte); deja los PNG en `cacheDir/cut/<id>_<n>.png`, los sirve por `/cut/<id>_<n>.png` y avisa con `window.onCutout({id, items|error})`. Se borran los recortes del trabajo anterior al empezar uno nuevo.
- `index.html`:
  - `pickCut(n)`: pregunta "Solo la cabeza / Cuerpo entero / Dejarla tal cual / Cancelar" (solo si existe `Android.cutout`; en la versión web no aparece).
  - `cutOut(blob, mode)`: reduce a ≤1280 px, fondo blanco, JPEG 0,92 → puente → espera `onCutout` (límite 90 s) → descarga los PNG.
  - `addImages(blobs, mode)`: usada al elegir imágenes ("Añadir imagen") y al compartirlas desde otra app; un recorte por persona/cabeza, aviso de cuántas fotos sin nadie.
  - `cutMeta`: convierte los metadatos a tamaño/posición/giro de la pegatina: `w = wE`, `r = −ang`, `(u,v) = −R(r)·(eu,ev)`. La cabeza queda justo sobre los puntos 1 y 2, con su tamaño y girada para igualar la línea de los ojos.
  - `addToLibrary(blob, {noOval, meta})`: los recortes no se ponen en óvalo y guardan `rec.def` y `rec.r` (el giro ahora también se guarda y se restaura; "Restablecer" vuelve al ajuste automático).
  - Límite del deslizador de tamaño y del pellizco: 10 → 16 (un cuerpo entero mide unas 7–9 distancias entre ojos).

### 100 % funcional (probado en Chrome sin interfaz con el puente simulado)
- Diálogo con 4 botones; cancelar no guarda nada; "tal cual" funciona como antes (óvalo automático).
- Cabeza: se guarda sin óvalo con `def`; el punto medio de los ojos del recorte cae exactamente en el punto medio de 1–2 (error 0 px), la distancia entre ojos coincide y la diferencia de ángulo es 0°.
- Cuerpo sin persona: aviso "No he encontrado a ninguna persona en la foto." y no guarda nada.
- Sin errores de JavaScript.
- Lógica de recorte de cabeza probada en Python sobre un retrato real (caja de cara a mano + máscara GrabCut): sale cabeza con pelo y cuello con fundido, sin hombros.

### Pendiente
- Java (`Cutout.java`, puente, `/cut/`) no se ha podido compilar ni ejecutar aquí (sin SDK de Android ni acceso a Maven de Google): lo comprobará la compilación de GitHub Actions. Probar en el móvil: cabeza y cuerpo con fotos reales, varias caras, calidad del borde del pelo.
- Limitaciones conocidas del modelo de selfie: pensado para personas grandes en el encuadre; personas muy pequeñas o de espaldas pueden salir mal (sin cara no hay recorte de cabeza). Dos personas abrazadas salen como un solo cuerpo.
- Idea si el borde del pelo no convence: pasar a MediaPipe Multiclass Selfie Segmenter (clases pelo/cara) o a Subject Segmentation de ML Kit.
