package cc.abase.lsposed.utils

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

fun String.gzip(): String {
  return Base64.encodeToString(ByteArrayOutputStream().also { bos -> GZIPOutputStream(bos).use { it.write(this.toByteArray()) } }.toByteArray(), Base64.NO_WRAP)
}

fun String.unGzip(): String {
  return String(GZIPInputStream(Base64.decode(this, Base64.NO_WRAP).inputStream()).use { it.readBytes() }, Charsets.UTF_8)
}