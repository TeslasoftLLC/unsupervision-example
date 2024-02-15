/**************************************************************************
 * UNSupervision
 *
 * Copyright (c) 2023 Dmytro Ostapenko. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **************************************************************************/

package org.teslasoft.core.unsupervision

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.SurfaceColors
import com.google.gson.Gson
import org.teslasoft.core.api.network.RequestNetwork
import org.teslasoft.core.api.network.RequestNetworkController.Companion.GET
import org.teslasoft.core.unsupervision.Constants.Companion.API_PATH
import org.teslasoft.core.unsupervision.Constants.Companion.SERVER_ADDRESS
import org.teslasoft.core.unsupervision.server.LocalServer
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Date
import java.util.UUID


class MainActivity : FragmentActivity(), SwipeRefreshLayout.OnRefreshListener {

    private lateinit var splashScreen: SplashScreen

    private var btnDebug: ImageButton? = null

    private var debugLayout: ConstraintLayout? = null

    private var webview: WebView? = null

    private var localHttpsServer: LocalServer? = null

    private var refresh: SwipeRefreshLayout? = null

    private var updateChecker: RequestNetwork? = null

    private var fileDownloader: RequestNetwork? = null

    private var applyUpdate: RequestNetwork? = null

    private var telemetry: RequestNetwork? = null

    private var lastUpdate: String = ""

    private var lastAppliedUpdate: String = ""

    private var ignoreFileList = ArrayList<String>()

    private var debugLog: String = ""

    private var debug: TextView? = null

    private var loading: ConstraintLayout? = null

    private var deviceId: String = ""

    private var debugTelemetry: MaterialButton? = null

    private var updateCheckerListener: RequestNetwork.RequestListener = object : RequestNetwork.RequestListener {
        override fun onResponse(tag: String, message: String) {
            val response: HashMap<String, Any> = Gson().fromJson(message, HashMap::class.java) as HashMap<String, Any>

            val updatesList = response["updates"] as ArrayList<String>

            lastAppliedUpdate = updatesList[0]

            updatesList.sortDescending()

            lastUpdate = updatesList[0]

            for (update in updatesList) {
                applyUpdate?.startRequestNetwork(GET, SERVER_ADDRESS + API_PATH + "updates/" + update + "/manifest.json", update, applyUpdateListener)
            }
        }

        override fun onErrorResponse(tag: String, message: String) {
            log("Failed to check for updates. The server will shutdown now.", "UPDATER", "ERROR")
        }
    }

    private var applyUpdateListener: RequestNetwork.RequestListener = object : RequestNetwork.RequestListener {
        override fun onResponse(tag: String, message: String) {
            if (message == "404_not_found") {
                log("Update ID $tag manifest is missing. Please check your update manifest.", "UPDATER", "ERROR")
            } else {
                val response: HashMap<String, Any> =
                    Gson().fromJson(message, HashMap::class.java) as HashMap<String, Any>

                val id = response["updateId"] as String
                val files = response["fileList"] as ArrayList<String>

                for (file in files) {
                    fileDownloader?.startRequestNetwork(
                        GET,
                        SERVER_ADDRESS + API_PATH + "updates/GetLastFile.php?file=" + file,
                        "$id:$file",
                        fileDownloaderListener
                    )
                }

                lastAppliedUpdate = id

                if (lastUpdate == lastAppliedUpdate) {

                    Handler(Looper.getMainLooper()).postDelayed({
                        refresh?.isRefreshing = false
                        startLocalHttpsServer()
                        webview?.loadUrl("http://localhost:36906")
                        splashScreen.setKeepOnScreenCondition { false }
                        loading?.visibility = ConstraintLayout.GONE
                    }, 1200)
                }
            }
        }

        override fun onErrorResponse(tag: String, message: String) {
            log("Failed to apply update ID $tag. The server will shutdown now.", "UPDATER", "ERROR")
        }
    }

    private var fileDownloaderListener: RequestNetwork.RequestListener = object : RequestNetwork.RequestListener {
        override fun onResponse(tag: String, message: String) {
            val parts = tag.split(":")
            val id = parts[0]
            val fileName = parts[1]

            log("File $fileName in update ID $id sha256 is ${calculateSHA256(message)}.", "UPDATER", "INFO")

            if (ignoreFileList.contains(fileName)) {
                log("File $fileName in update ID $id has already been applied. Skipping...", "UPDATER", "WARNING")
                return
            }

            ignoreFileList.add(fileName)

            if (message == "404_not_found" || message == "") {
                log("File $fileName is missing in update ID $id. Please check your update manifest.", "UPDATER", "ERROR")
            } else {
                writeToFile(this@MainActivity, fileName, message)
            }
        }

        override fun onErrorResponse(tag: String, message: String) {
            log("Failed to download file $tag. The server will shutdown now.", "UPDATER", "ERROR")
        }
    }

    private var telemetryListener: RequestNetwork.RequestListener = object : RequestNetwork.RequestListener {
        override fun onResponse(tag: String, message: String) {
            log("Telemetry message sent.", "TELEMETRY", "INFO")
        }

        override fun onErrorResponse(tag: String, message: String) {
            log("Failed to send telemetry message.", "TELEMETRY", "ERROR")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        splashScreen = installSplashScreen()

        splashScreen.setKeepOnScreenCondition { true }

        setContentView(R.layout.activity_main)

        val preferences: SharedPreferences = getSharedPreferences("org.teslasoft.unsupervision", Context.MODE_PRIVATE)

        deviceId = preferences.getString("deviceId", "") ?: ""

        if (deviceId == "") {
            deviceId = UUID.randomUUID().toString()
            preferences.edit().putString("deviceId", deviceId).apply()
        }

        btnDebug = findViewById(R.id.btn_debug)
        debugLayout = findViewById(R.id.debug_layout)
        debugLayout?.visibility = ConstraintLayout.GONE
        webview = findViewById(R.id.webview)
        refresh = findViewById(R.id.refresh)
        debug = findViewById(R.id.debug_log)
        loading = findViewById(R.id.loading)
        debugTelemetry = findViewById(R.id.debug_telemetry)

        log("Starting UNSupervision...", "MAIN", "INFO")
        log("Device ID: $deviceId", "MAIN", "INFO")

        debugTelemetry?.setOnClickListener {
            sendTelemetryMessage("Test telemetry message", "info")
        }

        loading?.visibility = ConstraintLayout.VISIBLE
        debug?.setTextIsSelectable(true)
        debug?.setTypeface(android.graphics.Typeface.MONOSPACE)

        updateChecker = RequestNetwork(this)
        fileDownloader = RequestNetwork(this)
        applyUpdate = RequestNetwork(this)
        telemetry = RequestNetwork(this)

        refresh?.setColorSchemeResources(R.color.accent_900)
        refresh?.setProgressBackgroundColorSchemeColor(
            SurfaceColors.SURFACE_2.getColor(this)
        )

        refresh?.setSize(SwipeRefreshLayout.LARGE)
        refresh?.setOnRefreshListener(this)

        webview?.settings?.javaScriptEnabled = true
        webview?.settings?.domStorageEnabled = true
        webview?.settings?.databaseEnabled = true
        webview?.settings?.allowFileAccess = true
        webview?.settings?.allowContentAccess = true

        webview?.setBackgroundColor(0x00000000)

        btnDebug?.setOnClickListener {
            if (debugLayout?.visibility == ConstraintLayout.GONE) {
                debugLayout?.visibility = ConstraintLayout.VISIBLE
            } else {
                debugLayout?.visibility = ConstraintLayout.GONE
            }
        }

        webview?.setWebChromeClient(object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                log(consoleMessage.sourceId() + ": " + consoleMessage.lineNumber() + ": " + consoleMessage.message(), "WEBVIEW", "INFO")

                sendTelemetryMessage(consoleMessage.sourceId() + ": " + consoleMessage.lineNumber() + ": " + consoleMessage.message(), "error")
                return super.onConsoleMessage(consoleMessage)
            }
        })

        updateChecker?.startRequestNetwork(GET,
            "$SERVER_ADDRESS$API_PATH/updates/versions.json", "A", updateCheckerListener)
    }

    private fun startLocalHttpsServer() {
        try {
            localHttpsServer = LocalServer(this)
            localHttpsServer?.start()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        localHttpsServer?.stop()
    }

    override fun onRefresh() {
        loading?.visibility = ConstraintLayout.VISIBLE
        ignoreFileList.clear()
        updateChecker?.startRequestNetwork(GET,
            "$SERVER_ADDRESS$API_PATH/updates/versions.json", "A", updateCheckerListener)
        webview?.reload()
    }

    fun writeToFile(context: Context, fileName: String, content: String) {
        try {
            val externalFilesDir: File? = context.getExternalFilesDir(null)

            if (externalFilesDir != null) {
                val file = File(externalFilesDir, fileName)
                val fileOutputStream = FileOutputStream(file)

                fileOutputStream.write(content.toByteArray())
                fileOutputStream.close()
            } else {
                log("Failed to write updates to the disk. The server will shutdown now.", "UPDATER", "ERROR")
            }
        } catch (e: IOException) {
            log("Failed to write updates to the disk. The server will shutdown now. Stacktrace:\n" + e.stackTraceToString(), "UPDATER", "ERROR")
        }
    }

    private fun readFile(filename: String): String {
        try {
            val externalFilesDir: File? = getExternalFilesDir(null)
            val file = File(externalFilesDir, filename)
            val inputStream = file.inputStream()
            return inputStream.bufferedReader().use { it.readText() }
        } catch (e: FileNotFoundException) {
            return ""
        } catch (e: IOException) {
            return ""
        }
    }

    fun calculateSHA256(input: String): String {
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val bytes = messageDigest.digest(input.toByteArray())

        val hexChars = StringBuilder()
        for (byte in bytes) {
            hexChars.append(String.format("%02x", byte))
        }

        return hexChars.toString()
    }

    fun sendTelemetryMessage(message: String, type: String) {
        val m = HashMap<String, String>()

        m["deviceId"] = deviceId
        m["message"] = message
        m["type"] = type
        m["timestamp"] = System.currentTimeMillis().toString()

        val requestBody: String = Gson().toJson(m)

        val data: ByteArray = requestBody.toByteArray(Charsets.UTF_8)
        val base64: String = Base64.encodeToString(data, Base64.DEFAULT)

        log("Sending telemetry message: $requestBody B64: $base64...", "TELEMETRY", "INFO")

        telemetry?.startRequestNetwork(GET, SERVER_ADDRESS + API_PATH + "Telemetry.php?data=" + base64, "A", telemetryListener)
    }

    fun log(message: String, tag: String, type: String) {
        val date = Date(System.currentTimeMillis())
        debugLog += "[${date}][$tag][$type] $message\n\n"
        debug?.text = debugLog
    }
}
