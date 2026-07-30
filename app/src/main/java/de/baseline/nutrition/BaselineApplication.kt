package de.baseline.nutrition

import android.app.Application
import de.baseline.nutrition.core.di.AppContainer

class BaselineApplication : Application() {
    val container: AppContainer by lazy { AppContainer() }
}

