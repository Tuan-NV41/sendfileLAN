package com.example.lanshare

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import okhttp3.*
import okio.BufferedSink
import okio.source
import java.security.MessageDigest
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private var baseUrl: String? = null
    private var sessionToken: String? = null

    private val http by lazy {
        OkHttpClient.Builder().retryOnConnectionFailure(true).build()
    }

    private val pickFiles = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (!uris.isNullOrEmpty()) uploadFiles(uris) }

    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { uploadFolder(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 64)
        }
        val btnScan = Button(this).apply { text = "Quét QR để ghép cặp" }
        val btnPickFiles = Button(this).apply { text = "Chọn FILE để gửi" }
        val btnPickFolder = Button(this).apply { text = "Chọn FOLDER để gửi" }
        val status = TextView(this)

        root.addView(btnScan)
        root.addView(btnPickFiles)
        root.addView(btnPickFolder)
        root.addView(status)
        setContentView(root)

        btnScan.setOnClickListener {
            startActivityForResult(Intent(this, ScanQrActivity::class.java), 1001)
        }

        btnPickFiles.setOnClickListener {
            if (sessionToken == null) { toast("Hãy ghép cặp trước"); return@setOnClickListener }
            pickFiles.launch(arrayOf("*/*"))
        }
        btnPickFolder.setOnClickListener {
            if (sessionToken == null) { toast("Hãy ghép cặp trước"); return@setOnClickListener }
            pickFolder.launch(null)
        }
    }

    override fun onActivityResult(reqCode: Int, resCode: Int, data: Intent?) {
        super.onActivityResult(reqCode, resCode, data)
        if (reqCode == 1001 && resCode == Activity.RESULT_OK) {
            val scanned = data?.getStringExtra("SCAN_RESULT") ?: return
            val idx = scanned.indexOf("/pair")
            baseUrl = scanned.substring(0, idx)
            doPair(scanned)
        }
    }

    private fun doPair(pairUrl: String) {
        val req = Request.Builder().url(pairUrl)
            .post(RequestBody.create(null, ByteArray(0)))
            .build()
        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                runOnUiThread { toast("Pair thất bại: ${e.message}") }
            }
            override fun onResponse(call: Call, resp: Response) {
                if (!resp.isSuccessful) {
                    runOnUiThread { toast("Pair lỗi: ${resp.code}") }
                    return
                }
                sessionToken = resp.header("X-Session-Token")
                runOnUiThread { toast("Đã ghép cặp!") }
            }
        })
    }

    private fun uploadFiles(uris: List<Uri>) {
        val url = "${baseUrl}/upload"
        val executor = Executors.newFixedThreadPool(autoThreads(uris))
        uris.forEach { uri ->
            executor.execute {
                val name = getName(uri)
                val size = getSize(uri)
                val sha256 = sha256(uri)
                val body = object : RequestBody() {
                    override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
                    override fun contentLength() = size
                    override fun writeTo(sink: BufferedSink) {
                        contentResolver.openInputStream(uri)!!.source().use { src ->
                            sink.writeAll(src)
                        }
                    }
                }
                val req = Request.Builder()
                    .url("$url?name=${name}&size=${size}&sha256=${sha256}")
                    .addHeader("Authorization", "Bearer $sessionToken")
                    .put(body)
                    .build()
                try {
                    http.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
                    }
                } catch (e: Exception) {
                    runOnUiThread { toast("Gửi $name lỗi: ${e.message}") }
                }
            }
        }
    }

    private fun uploadFolder(treeUri: Uri) {
        val doc = DocumentFile.fromTreeUri(this, treeUri) ?: return
        val files = doc.listFiles().filter { it.isFile }
        uploadFiles(files.map { it.uri })
    }

    private fun sha256(uri: Uri): String {
        val md = MessageDigest.getInstance("SHA-256")
        contentResolver.openInputStream(uri)!!.use { ins ->
            val buf = ByteArray(1 shl 20)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun getName(uri: Uri): String =
        DocumentFile.fromSingleUri(this, uri)?.name ?: "file.bin"

    private fun getSize(uri: Uri): Long =
        contentResolver.openAssetFileDescriptor(uri, "r")?.length ?: -1

    private fun autoThreads(uris: List<Uri>): Int {
        val total = uris.sumOf { getSize(it).coerceAtLeast(0) }
        return when {
            total >= 512L * 1024 * 1024 -> 8
            total >= 128L * 1024 * 1024 -> 4
            total >= 32L * 1024 * 1024  -> 2
            else -> 1
        }
    }

    private fun toast(s: String) = runOnUiThread {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }
}