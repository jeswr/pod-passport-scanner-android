package org.jeswr.podpassport

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.jeswr.podpassport.service.NfcChipReader
import org.jeswr.podpassport.ui.AppRoot
import org.jeswr.podpassport.ui.AppViewModel
import org.jeswr.podpassport.ui.theme.PodPassportTheme

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null

    private val viewModel: AppViewModel by viewModels {
        // `--uitest` (instrumentation arg) forces both mocks + zero delays.
        val args = intent?.extras
        val uiTest = args?.getBoolean(ARG_UITEST, false) == true ||
            args?.getString("class") != null // espresso sets test args; conservative
        val mockChip = uiTest || args?.getBoolean(ARG_MOCK_CHIP, false) == true || !hasNfc()
        val mockUpload = uiTest || args?.getBoolean(ARG_MOCK_UPLOAD, false) == true
        viewModelFactory {
            initializer {
                AppViewModel(
                    application = application,
                    mockChip = mockChip,
                    mockUpload = mockUpload,
                    fast = uiTest,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        enableEdgeToEdge()
        setContent {
            PodPassportTheme {
                AppRoot(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Foreground dispatch: capture IsoDep tags while the app is in the
        // foreground so the system chooser never steals the passport tag.
        val adapter = nfcAdapter ?: return
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val pending = PendingIntent.getActivity(this, 0, intent, flags)
        runCatching {
            adapter.enableForegroundDispatch(
                this,
                pending,
                null,
                arrayOf(arrayOf(IsoDep::class.java.name)),
            )
        }
    }

    override fun onPause() {
        super.onPause()
        runCatching { nfcAdapter?.disableForegroundDispatch(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
        if (tag != null && IsoDep.get(tag) != null) {
            viewModel.pendingNfcReader = NfcChipReader(tag)
        }
    }

    private fun hasNfc(): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(this)
        return adapter != null && adapter.isEnabled
    }

    companion object {
        const val ARG_UITEST = "uitest"
        const val ARG_MOCK_CHIP = "mock_chip"
        const val ARG_MOCK_UPLOAD = "mock_upload"
    }
}
