package io.github.lemonjelly76.babymilestones.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    /** An invite link opened from a message: babymilestones://join?code=…&server=… */
    private val invite = mutableStateOf<Invite?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        invite.value = Invite.from(intent)
        setContent {
            BabyMilestonesTheme {
                BabyMilestonesApp(viewModel, invite.value) { invite.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Invite.from(intent)?.let { invite.value = it }
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.state.value.signedIn) viewModel.refresh()
    }
}

data class Invite(val code: String, val server: String?) {
    companion object {
        fun from(intent: Intent?): Invite? {
            val uri = intent?.data ?: return null
            if (uri.scheme != "babymilestones" || uri.host != "join") return null
            return uri.getQueryParameter("code")?.let { Invite(it, uri.getQueryParameter("server")) }
        }
    }
}
