package de.baseline.nutrition.core.config

import de.baseline.nutrition.BuildConfig

enum class BuildEnvironment {
    Local,
    InternalBeta,
    Production;

    companion object {
        fun from(value: String): BuildEnvironment = when (value) {
            "local" -> Local
            "internalBeta" -> InternalBeta
            "production" -> Production
            else -> error("Unbekannte Buildumgebung: $value")
        }
    }
}

data class AppConfiguration(
    val environment: BuildEnvironment,
    val apiBaseUrl: String,
) {
    companion object {
        fun current(): AppConfiguration = AppConfiguration(
            environment = BuildEnvironment.from(BuildConfig.ENVIRONMENT),
            apiBaseUrl = BuildConfig.API_BASE_URL,
        )
    }
}

