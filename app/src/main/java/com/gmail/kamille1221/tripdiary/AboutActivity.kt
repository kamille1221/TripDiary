package com.gmail.kamille1221.tripdiary

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import kotlinx.android.synthetic.main.activity_about.*
import java.lang.ref.WeakReference
import java.util.*

/**
 * Created by Kamille on 2018-06-14.
 **/
class AboutActivity: AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_about)

		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		supportActionBar?.setHomeButtonEnabled(true)

		tvVersion.text = String.format(Locale.getDefault(), "%s %s", getString(R.string.version), BuildConfig.VERSION_NAME)

		tvLicense.setOnClickListener { startActivity(Intent(this, OssLicensesMenuActivity::class.java)) }

		SpendUtils.VersionAsyncTask(packageManager, packageName, WeakReference(btnUpdate)).execute()
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			android.R.id.home -> {
				this.onBackPressed()
				return true
			}
		}
		return false
	}
}
