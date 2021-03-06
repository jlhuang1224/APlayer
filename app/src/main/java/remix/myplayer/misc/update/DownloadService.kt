package remix.myplayer.misc.update

import android.annotation.TargetApi
import android.app.IntentService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.support.v4.app.NotificationCompat
import remix.myplayer.R
import remix.myplayer.bean.github.Release
import remix.myplayer.request.network.OkHttpHelper
import remix.myplayer.util.LogUtil
import remix.myplayer.util.ToastUtil
import remix.myplayer.util.Util
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Creates an IntentService.  Invoked by your subclass's constructor.
 *
 * @param name Used to name the worker thread, important only for debugging.
 */
class DownloadService : IntentService("DownloadService") {
    private val mNotificationManager: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        //todo 创建通知栏通道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannelIfNeed()
        }
    }


    @TargetApi(Build.VERSION_CODES.O)
    private fun createNotificationChannelIfNeed() {
        val playingNotificationChannel = NotificationChannel(UPDATE_NOTIFICATION_CHANNEL_ID, getString(R.string.update_notification), NotificationManager.IMPORTANCE_LOW)
        playingNotificationChannel.setShowBadge(false)
        playingNotificationChannel.enableLights(false)
        playingNotificationChannel.enableVibration(false)
        playingNotificationChannel.description = getString(R.string.update_notification_description)
        mNotificationManager.createNotificationChannel(playingNotificationChannel)
    }

    override fun onHandleIntent(intent: Intent?) {
        if (intent == null || !intent.hasExtra(EXTRA_RESPONSE)) {
            return
        }
        val release: Release = intent.getParcelableExtra(EXTRA_RESPONSE) as Release
        var inStream: InputStream? = null
        var outStream: FileOutputStream? = null

        try {
            val downloadUrl = release.assets[0].browser_download_url
            val downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: throw RuntimeException("下载目录不存在")
            if (!downloadDir.exists() && !downloadDir.mkdirs()) {
                throw RuntimeException("下载目录创建失败")
            }
            val downloadFile = File(downloadDir, release.name + ".apk")
            //已经下载完成
            if (downloadFile.exists()) {
                if (downloadFile.length() == release.assets[0].size) {
                    sendCompleteBroadcast(downloadFile.absolutePath)
                    return
                } else {
                    //删除原来的文件并重新创建
                    if (!downloadFile.delete()) {
                        throw RuntimeException("Can't delete old file")
                    }
                }
            }
            postNotification(release.assets[0].size, 0)
            if (isForce(release)) {
                sendBroadcast(Intent(ACTION_SHOW_DIALOG))
            }
            HttpsURLConnection.setDefaultSSLSocketFactory(OkHttpHelper.getSSLSocketFactory())
            val url = URL(downloadUrl)

            val conn = url.openConnection()
            conn.connectTimeout = TIME_OUT
            conn.readTimeout = TIME_OUT
            conn.connect()
            inStream = conn.getInputStream()
            val fileSize = conn.contentLength//根据响应获取文件大小
            if (fileSize <= 0) throw RuntimeException("Can't get size of file")
            if (inStream == null) throw RuntimeException("Can't get InputStream")
            outStream = FileOutputStream(downloadFile)
            val buf = ByteArray(1024)
            var downloadSize = 0L
            do {
                //循环读取
                val numRead = inStream.read(buf)
                if (numRead == -1) {
                    break
                }
                outStream.write(buf, 0, numRead)
                downloadSize += numRead
                postNotification(release.assets[0].size, downloadSize)
                //更新进度条
            } while (true)
            outStream.flush()
            sendCompleteBroadcast(downloadFile.absolutePath)
        } catch (ex: Exception) {
            ToastUtil.show(this, R.string.update_error, ex.message)
        } finally {
            sendBroadcast(Intent(ACTION_DISMISS_DIALOG))
            Util.closeStream(inStream)
            Util.closeStream(outStream)
        }
        mNotificationManager.cancel(UPDATE_NOTIFICATION_ID)
    }

    private fun sendCompleteBroadcast(path: String) {
        sendBroadcast(Intent(ACTION_DOWNLOAD_COMPLETE)
                .putExtra(EXTRA_PATH, path))
    }

    private fun postNotification(targetSize: Long, downloadSize: Long) {
        val builder = NotificationCompat.Builder(this, UPDATE_NOTIFICATION_CHANNEL_ID)
                .setContentIntent(null)
                .setContentTitle(getString(R.string.downloading))
//                .setContentText(getString(if(isFinish) R.string.download_complete_to_do else R.string.please_wait))
                .setProgress(targetSize.toInt(), downloadSize.toInt(), false)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(false)
                .setShowWhen(false)
                .setOngoing(true)
        if (targetSize == 0L) {
            builder.setTicker(getString(R.string.downloading))
        }
        mNotificationManager.notify(UPDATE_NOTIFICATION_ID, builder.build())
        LogUtil.d(TAG, "TargetSize: $targetSize DownloadSize: $downloadSize")
    }

    private fun getContentIntent(isFinish: Boolean, path: String): PendingIntent? {
        return null
//        return if(isFinish){
//            val intent = Intent(MainActivity.UpdateReceiver.ACTION_DOWNLOAD_COMPLETE)
//                    .putExtra(EXTRA_PATH,path)
//            PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
//        }else{
//            val intent = Intent(this, MainActivity::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
//            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
//        }
    }


    private fun isForce(release: Release?): Boolean {
        val split = release?.name?.split("-")
        return split != null && split.size > 2
    }

    companion object {
        const val ACTION_DOWNLOAD_COMPLETE = "remix.myplayer.ACTION.DOWNLOAD_COMPLETE"
        const val ACTION_SHOW_DIALOG = "remix.myplayer.ACTION_SHOW_DIALOG"
        const val ACTION_DISMISS_DIALOG = "remix.myplayer.ACTION_DISMISS_DIALOG"
        const val EXTRA_RESPONSE = "update_info"
        const val EXTRA_PATH = "file_path"
        private const val TAG = "DownloadService"
        private const val UPDATE_NOTIFICATION_CHANNEL_ID = "update_notification"
        private const val UPDATE_NOTIFICATION_ID = 10
        private const val TIME_OUT = 5000
    }
}
