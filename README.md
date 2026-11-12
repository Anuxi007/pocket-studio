# Pocket Studio

App gratuita y de código abierto para Android: descarga video o audio a partir de un enlace y, más adelante, edítalo.

> **Versión alpha 0.0.1.** Puede tener errores. Úsala solo con contenido propio, libre o con permiso.

## Qué hace la 0.0.1

- **Downloader:** pega un enlace, elige **Video** o **Audio** y descarga.
- **Video:** lista por resolución con fps, códec, contenedor y tamaño aproximado. Bloquea lo que el chip de tu celular no puede reproducir (por ejemplo 4K en AV1) y marca una opción **Recomendada**.
- **Audio:** AAC (M4A) o MP3, con tres calidades y tamaño aproximado.
- **Compartir desde otras apps:** aparece en el menú *Compartir* con dos opciones: **Descarga rápida** o **Abrir**.
- **Errores honestos:** explica qué falló (video privado, sin conexión, página cambió, etc.) y muestra los detalles técnicos para reportarlos.
- **Carpeta de descargas:** `Download/Pocket Studio` por defecto, o la que elijas (la app la recuerda).
- **Actualizaciones:** en *Opciones* puedes actualizar yt-dlp y buscar nuevas versiones de la app en los [Releases](../../releases) de este repositorio.

## Instalar

1. Entra a [Releases](../../releases) y descarga el APK que termina en `arm64-v8a` (casi todos los celulares actuales). Si tu celular es muy antiguo, usa `armeabi-v7a`.
2. Ábrelo y permite instalar apps de esta fuente cuando Android lo pida.

Requiere Android 10 o superior.

## Aviso de uso responsable

Pocket Studio no incluye ni distribuye contenido. Descargar contenido protegido sin permiso puede ir contra los términos de las plataformas y, según tu país, contra la ley de derechos de autor. Tú eres responsable de cómo usas la app.

## Compilar

Cada subida a GitHub compila el APK con GitHub Actions (los APK quedan como *artifact* de cada ejecución). Para publicar una versión en Releases, de cualquiera de estas dos formas:

- **Desde GitHub:** pestaña *Actions* → *Build APK* → *Run workflow* → escribe la versión (por ejemplo `0.0.1`) → *Run workflow*.
- **Con una etiqueta:** `git tag v0.0.2 && git push origin v0.0.2`.

El flujo crea un *pre-release* con los APK adjuntos. Para compilar en tu computadora necesitas Android Studio (o JDK 17 + Android SDK + Gradle 8.9) y ejecutar `gradle assembleRelease`.

### Firma de la alpha

El APK se firma con la llave de pruebas `signing/alpha.jks`, que es **pública** (está en este repositorio) para que las actualizaciones de la alpha se puedan instalar unas sobre otras. No es una llave segura: antes de una versión estable se cambiará por una privada (secretos `KEYSTORE_B64`, `KS_PASS`, `KEY_ALIAS` y `KEY_PASS` en GitHub) y habrá que desinstalar la alpha una vez.

## Hecho con

- [youtubedl-android](https://github.com/JunkFood02/youtubedl-android), que empaqueta [yt-dlp](https://github.com/yt-dlp/yt-dlp), Python y FFmpeg.
- Kotlin y Jetpack Compose.

## Pendiente

- Gestor de componentes (descargar yt-dlp desde su GitHub oficial en lugar de incluirlo).
- Descargas en segundo plano con notificación.
- Editor de video, letras sincronizadas y reconocimiento de canciones.

## Licencia

[GPL-3.0](LICENSE), igual que las librerías en las que se apoya.
