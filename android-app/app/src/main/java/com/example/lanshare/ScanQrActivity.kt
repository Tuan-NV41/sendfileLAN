package com.example.lanshare

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class ScanQrActivity : CaptureActivity() {
    private val launcher = registerForActivityResult(ScanContract()) { result ->
        if (result != null && result.contents != null) {
            val data = Intent().apply { putExtra("SCAN_RESULT", result.contents) }
            setResult(Activity.RESULT_OK, data)
        } else {
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val options = ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setPrompt("Đưa QR vào khung")
        options.setBeepEnabled(false)
        launcher.launch(options)
    }
}