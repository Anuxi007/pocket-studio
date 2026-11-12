package com.anuxi.pocketstudio.data

/** Convierte el texto de error de yt-dlp en una explicacion honesta en español. */
object Errors {
    fun classify(raw: String): AppError {
        val msg = raw.trim()
        val lower = msg.lowercase()
        val detail = msg.lines().firstOrNull { it.trimStart().startsWith("ERROR", ignoreCase = true) }
            ?.trim()
            ?: msg.take(400)

        fun has(vararg keys: String) = keys.any { lower.contains(it.lowercase()) }

        return when {
            has("unsupported url") -> AppError(
                title = tr("Enlace no compatible", "Unsupported link"),
                text = tr("yt-dlp no reconoce este enlace como un video de un sitio que soporte.", "yt-dlp doesn't recognize this link as a video from a supported site."),
                hint = tr("Revisa que el enlace sea el del video y no el de la página principal.", "Make sure the link is the video's and not the site's home page."),
                detail = detail,
                action = ErrorAction.Clear,
                actionLabel = tr("Probar otro enlace", "Try another link"),
            )

            has("unable to extract", "extractorerror", "cannot parse", "failed to parse",
                "nsig", "unable to find", "no video formats found", "unexpected response") -> AppError(
                title = tr("La página cambió su estructura", "The page changed its structure"),
                text = tr("yt-dlp ya no entiende cómo está armada esta página. Suele pasar cuando la plataforma hace cambios.", "yt-dlp no longer understands how this page is built. This usually happens when the platform makes changes."),
                hint = tr("Prueba actualizar yt-dlp. Si ya tienes la última versión, toca esperar: sus desarrolladores suelen corregirlo en pocos días.", "Try updating yt-dlp. If you already have the latest version, you'll have to wait: its developers usually fix it within a few days."),
                detail = detail,
                action = ErrorAction.UpdateYtdlp,
                actionLabel = tr("Buscar actualización de yt-dlp", "Check for yt-dlp update"),
            )

            has("video unavailable", "private video", "this video is private", "has been removed",
                "sign in", "log in", "login", "members-only", "confirm your age",
                "not available", "does not exist", "been terminated", "copyright") -> AppError(
                title = tr("El video no está disponible", "The video isn't available"),
                text = tr("Puede ser privado, haber sido eliminado o pedir inicio de sesión. Pocket Studio no puede acceder a contenido privado.", "It may be private, deleted or require signing in. Pocket Studio can't access private content."),
                hint = tr("Revisa que el enlace abra el video en tu navegador.", "Check that the link opens the video in your browser."),
                detail = detail,
                action = ErrorAction.Clear,
                actionLabel = tr("Probar otro enlace", "Try another link"),
            )

            has("geo", "your country", "your region") -> AppError(
                title = tr("Bloqueado en tu país", "Blocked in your country"),
                text = tr("El video no está disponible en tu región, así que tampoco se puede descargar.", "The video isn't available in your region, so it can't be downloaded either."),
                hint = tr("Es una restricción del sitio, no de la app.", "This is a restriction by the site, not the app."),
                detail = detail,
                action = ErrorAction.Clear,
                actionLabel = tr("Probar otro enlace", "Try another link"),
            )

            has("429", "too many requests", "rate limit") -> AppError(
                title = tr("Demasiados intentos", "Too many attempts"),
                text = tr("El sitio pidió esperar un momento antes de seguir.", "The site asked to wait a moment before continuing."),
                hint = tr("Espera unos minutos e inténtalo otra vez.", "Wait a few minutes and try again."),
                detail = detail,
                action = ErrorAction.Retry,
                actionLabel = tr("Reintentar", "Retry"),
            )

            has("unable to download", "timed out", "temporary failure", "network is unreachable",
                "name or service not known", "connection", "urlerror", "unknownhost",
                "resolve host") -> AppError(
                title = tr("No se pudo conectar", "Couldn't connect"),
                text = tr("Revisa tu conexión a internet. Si el video está bloqueado en tu país, tampoco se podrá descargar.", "Check your internet connection. If the video is blocked in your country, it can't be downloaded either."),
                hint = tr("Si el problema sigue, intenta con otra red.", "If the problem continues, try another network."),
                detail = detail,
                action = ErrorAction.Retry,
                actionLabel = tr("Reintentar", "Retry"),
            )

            has("no space left", "enospc") -> AppError(
                title = tr("No hay espacio", "No space left"),
                text = tr("Tu celular no tiene espacio suficiente para guardar el archivo.", "Your phone doesn't have enough space to save the file."),
                hint = tr("Libera espacio y vuelve a intentarlo.", "Free up some space and try again."),
                detail = detail,
                action = ErrorAction.Retry,
                actionLabel = tr("Reintentar", "Retry"),
            )

            else -> AppError(
                title = tr("No pudimos descargar este video", "We couldn't download this video"),
                text = tr("Ocurrió un error y no pudimos saber la causa exacta.", "An error occurred and we couldn't tell the exact cause."),
                hint = tr("Copia los detalles técnicos si quieres reportarlo en GitHub.", "Copy the technical details if you want to report it on GitHub."),
                detail = detail,
                action = ErrorAction.Retry,
                actionLabel = tr("Reintentar", "Retry"),
            )
        }
    }
}
