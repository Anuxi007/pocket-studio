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
                title = "Enlace no compatible",
                text = "yt-dlp no reconoce este enlace como un video de un sitio que soporte.",
                hint = "Revisa que el enlace sea el del video y no el de la página principal.",
                detail = detail,
                action = ErrorAction.Clear,
                actionLabel = "Probar otro enlace",
            )

            has("unable to extract", "extractorerror", "cannot parse", "failed to parse",
                "nsig", "unable to find", "no video formats found", "unexpected response") -> AppError(
                title = "La página cambió su estructura",
                text = "yt-dlp ya no entiende cómo está armada esta página. " +
                    "Suele pasar cuando la plataforma hace cambios.",
                hint = "Prueba actualizar yt-dlp. Si ya tienes la última versión, toca esperar: " +
                    "sus desarrolladores suelen corregirlo en pocos días.",
                detail = detail,
                action = ErrorAction.UpdateYtdlp,
                actionLabel = "Buscar actualización de yt-dlp",
            )

            has("video unavailable", "private video", "this video is private", "has been removed",
                "sign in", "log in", "login", "members-only", "confirm your age",
                "not available", "does not exist", "been terminated", "copyright") -> AppError(
                title = "El video no está disponible",
                text = "Puede ser privado, haber sido eliminado o pedir inicio de sesión. " +
                    "Pocket Studio no puede acceder a contenido privado.",
                hint = "Revisa que el enlace abra el video en tu navegador.",
                detail = detail,
                action = ErrorAction.Clear,
                actionLabel = "Probar otro enlace",
            )

            has("geo", "your country", "your region") -> AppError(
                title = "Bloqueado en tu país",
                text = "El video no está disponible en tu región, así que tampoco se puede descargar.",
                hint = "Es una restricción del sitio, no de la app.",
                detail = detail,
                action = ErrorAction.Clear,
                actionLabel = "Probar otro enlace",
            )

            has("429", "too many requests", "rate limit") -> AppError(
                title = "Demasiados intentos",
                text = "El sitio pidió esperar un momento antes de seguir.",
                hint = "Espera unos minutos e inténtalo otra vez.",
                detail = detail,
                action = ErrorAction.Retry,
                actionLabel = "Reintentar",
            )

            has("unable to download", "timed out", "temporary failure", "network is unreachable",
                "name or service not known", "connection", "urlerror", "unknownhost",
                "resolve host") -> AppError(
                title = "No se pudo conectar",
                text = "Revisa tu conexión a internet. Si el video está bloqueado en tu país, " +
                    "tampoco se podrá descargar.",
                hint = "Si el problema sigue, intenta con otra red.",
                detail = detail,
                action = ErrorAction.Retry,
                actionLabel = "Reintentar",
            )

            has("no space left", "enospc") -> AppError(
                title = "No hay espacio",
                text = "Tu celular no tiene espacio suficiente para guardar el archivo.",
                hint = "Libera espacio y vuelve a intentarlo.",
                detail = detail,
                action = ErrorAction.Retry,
                actionLabel = "Reintentar",
            )

            else -> AppError(
                title = "No pudimos descargar este video",
                text = "Ocurrió un error y no pudimos saber la causa exacta.",
                hint = "Copia los detalles técnicos si quieres reportarlo en GitHub.",
                detail = detail,
                action = ErrorAction.Retry,
                actionLabel = "Reintentar",
            )
        }
    }
}
