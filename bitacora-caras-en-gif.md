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

### Pendiente / ideas
- Probar en un móvil real (gestos, `ImageDecoder`, guardado desde la app de Claude).
- Conservar la transparencia de los GIF de origen (ahora se aplana sobre blanco).
- Paleta por fotograma o difuminado opcional para vídeos con degradados.
- Varias pegatinas o varias caras a la vez.
- Detección automática de la cara: no es posible en esta página porque la política de seguridad bloquea la descarga de modelos (MediaPipe).
