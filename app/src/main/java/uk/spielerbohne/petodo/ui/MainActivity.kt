package uk.spielerbohne.petodo.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import uk.spielerbohne.petodo.PetodoApplication
import uk.spielerbohne.petodo.ui.theme.PetodoTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PetodoApplication).container
        setContent {
            PetodoTheme {
                PetodoApp(container = container)
            }
        }
    }
}
