package com.gmail.kamille1221.tripdiary

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.text.format.DateFormat
import android.view.View
import android.widget.Button
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by Kamille on 2018-06-14.
 **/
object SpendUtils {
	const val REQUEST_CODE_PERMISSION: Int = 1000
	const val REQUEST_CODE_ADD_SPEND: Int = 1001
	const val REQUEST_CODE_MODIFY_SPEND: Int = 1002
	const val REQUEST_CODE_EXTERNAL_STORAGE: Int = 1003
	const val REQUEST_CODE_PICK_IMAGE: Int = 1004
	const val CHANNEL_ID_UPLOAD: String = "uploadPhoto"
	const val DEFAULT_LAT: Double = 37.514530
	const val DEFAULT_LNG: Double = 127.106545
	private const val PREFERENCE_NAME: String = "com.gmail.kamille1221.tripdiary"
	private const val LAST_CURRENCY: String = "LAST_CURRENCY"
	private var sharedPreferences: SharedPreferences? = null

	private fun getSharedPreferences(context: Context): SharedPreferences? {
		if (sharedPreferences == null) {
			sharedPreferences = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
		}
		return sharedPreferences
	}

	// 0: KRW, 1: USD, 2: JPY, 3: EUR, 4: GBP, 5: TWD
	fun setLastCurrency(context: Context, currency: Int) {
		val editor = getSharedPreferences(context)?.edit()
		if (editor != null) {
			editor.putInt(LAST_CURRENCY, currency)
			editor.apply()
		}
	}

	fun getLastCurrency(context: Context): Int {
		return getSharedPreferences(context)?.getInt(LAST_CURRENCY, 0) ?: 0
	}

	fun dateLongToString(date: Long): String {
		val calendar: Calendar = Calendar.getInstance()
		calendar.timeInMillis = date
		val dateFormat = SimpleDateFormat(
			DateFormat.getBestDateTimePattern(
				Locale.getDefault(),
				"yyyy/MM/dd HH:mm"
			), Locale.getDefault()
		)
		return dateFormat.format(Date(calendar.timeInMillis))
	}

	fun priceIntToString(price: Int): String {
		val decimalFormat = DecimalFormat("###,###")
		return decimalFormat.format(price)
	}

	fun currencyPositionToString(context: Context, position: Int): String {
		return context.resources.getStringArray(R.array.currency)[position]
	}

	fun currencyStringToPosition(context: Context, currency: String): Int {
		val currencyList: Array<out String> = context.resources.getStringArray(R.array.currency)
		for (i in currencyList.indices) {
			if (currencyList[i] == currency) {
				return i
			}
		}
		return -1
	}

	class VersionAsyncTask(
		private val packageManager: PackageManager,
		private val packageName: String,
		private val btnReference: WeakReference<Button>
	) : AsyncTask<Void, Void, Boolean>() {
		private lateinit var current: String
		private lateinit var store: String
		override fun doInBackground(vararg params: Void?): Boolean {
			if (BuildConfig.DEBUG) {
				return false
			}
			current = packageManager.getPackageInfo(packageName, 0).versionName
			store = getMarketVersionFast(packageName)
			return true
		}

		override fun onPostExecute(result: Boolean) {
			super.onPostExecute(result)
			val btnUpdate: Button? = btnReference.get()
			btnUpdate?.visibility = if (result && current != store) {
				View.VISIBLE
			} else {
				View.GONE
			}
		}

		/**
		 * references by http://gun0912.tistory.com/8
		 */
		private fun getMarketVersionFast(packageName: String): String {
			var mData = ""
			var mVer: String
			try {
				val mUrl = URL("https://play.google.com/store/apps/details?id=$packageName")
				val mConnection = mUrl.openConnection() as HttpURLConnection
				mConnection.connectTimeout = 5000
				mConnection.useCaches = false
				mConnection.doOutput = true
				if (mConnection.responseCode == HttpURLConnection.HTTP_OK) {
					val mReader = BufferedReader(InputStreamReader(mConnection.inputStream))
					while (true) {
						val line = mReader.readLine() ?: break
						mData += line
					}
					mReader.close()
				}
				mConnection.disconnect()
			} catch (ex: Exception) {
				ex.printStackTrace()
				return ""
			}

			val startToken = "softwareVersion\">"
			val endToken = "<"
			val index = mData.indexOf(startToken)
			if (index == -1) {
				mVer = ""
			} else {
				mVer = mData.substring(index + startToken.length, index + startToken.length + 100)
				mVer = mVer.substring(0, mVer.indexOf(endToken)).trim { it <= ' ' }
			}
			return mVer
		}
	}
}
