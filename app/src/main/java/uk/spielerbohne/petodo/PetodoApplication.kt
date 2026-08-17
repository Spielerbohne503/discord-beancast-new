package uk.spielerbohne.petodo

import android.app.Application
import uk.spielerbohne.petodo.di.AppContainer

class PetodoApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
