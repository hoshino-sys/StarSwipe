package com.starswipe.app.crash

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

object CrashHandler {

    fun init(app: Application) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                handleCrash(app, thread, throwable)
            } catch (e: Exception) {
                Log.e("StarSwipeCrash", "Failed to handle uncaught crash", e)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun handleCrash(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()

        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val crashReport = buildString {
            appendLine("=== StarSwipe 崩溃异常诊断日志 ===")
            appendLine("时间: $time")
            appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("系统版本: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("线程: ${thread.name} (ID: ${thread.id})")
            appendLine("异常类型: ${throwable.javaClass.name}")
            appendLine("异常详情: ${throwable.message}")
            appendLine()
            appendLine("=== 异常堆栈追踪 (Stack Trace) ===")
            appendLine(stackTrace)
        }

        Log.e("StarSwipeCrash", crashReport)

        // Write to internal file
        try {
            val internalFile = File(context.filesDir, "last_crash.txt")
            internalFile.writeText(crashReport)
        } catch (_: Exception) {}

        // Write to external files directory
        try {
            context.getExternalFilesDir(null)?.let { extDir ->
                File(extDir, "last_crash.txt").writeText(crashReport)
            }
        } catch (_: Exception) {}

        // Start CrashActivity in a clean separate task
        val intent = Intent(context, CrashActivity::class.java).apply {
            putExtra(CrashActivity.EXTRA_CRASH_INFO, crashReport)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)

        // Kill crashed process
        android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess(10)
    }
}
