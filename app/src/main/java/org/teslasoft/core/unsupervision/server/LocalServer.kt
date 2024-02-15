package org.teslasoft.core.unsupervision.server
import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class LocalServer(private val context: Context) : NanoHTTPD(36906) {
    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        if (uri == "/") {
            // Serve the index.html file
            return serveFile("index.html", session)
        } else if (uri.startsWith("/")) {
            // Serve other assets from the assets folder
            val assetPath = uri.substring("/".length)
            return serveFile(assetPath, session)
        }

        // If the requested resource is not found, return a 404 response
        return newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not Found")
    }

    private fun serveFile(filename: String, session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        try {
            val externalFilesDir: File? = context.getExternalFilesDir(null)
            val file = File(externalFilesDir, filename)
            val inputStream = file.inputStream()
            val mimeType = NanoHTTPD.getMimeTypeForFile(filename)
            return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, mimeType, inputStream,
                inputStream.available().toLong()
            )
        } catch (e: FileNotFoundException) {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "File not found")
        } catch (e: IOException) {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Internal Server Error\n" + e.stackTraceToString())
        }
    }
}
