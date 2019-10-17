package com.gmail.kamille1221.tripdiary

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_about.*
import kotlinx.android.synthetic.main.dialog_input.view.*
import java.io.File
import java.lang.ref.WeakReference
import java.util.*

/**
 * Created by Kamille on 2018-06-14.
 **/
class AboutActivity : AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_about)

		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		supportActionBar?.setHomeButtonEnabled(true)

		tvVersion.text = String.format(
			Locale.getDefault(),
			"%s %s",
			getString(R.string.version),
			BuildConfig.VERSION_NAME
		)

		tvLicense.setOnClickListener {
			startActivity(
				Intent(
					this,
					OssLicensesMenuActivity::class.java
				)
			)
		}
		btnBackup.setOnClickListener {
			if (ActivityCompat.checkSelfPermission(
					this,
					Manifest.permission.WRITE_EXTERNAL_STORAGE
				) == PackageManager.PERMISSION_GRANTED
			) {
				val resource: Int = R.layout.dialog_input
				val view = layoutInflater.inflate(resource, null)
				val builder = AlertDialog.Builder(this)
				builder.setView(view)
				builder.setTitle(R.string.backup)
				view.etInput.setRawInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
				builder.setPositiveButton(R.string.confirm) { _, _ -> backupRealm(view.etInput.text.toString()) }
				builder.setNegativeButton(R.string.cancel, null)
				builder.create().show()
			} else {
				ActivityCompat.requestPermissions(
					this,
					arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
					SpendUtils.REQUEST_CODE_EXTERNAL_STORAGE
				)
				return@setOnClickListener
			}
		}

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

	override fun onRequestPermissionsResult(
		requestCode: Int,
		permissions: Array<out String>,
		grantResults: IntArray
	) {
		if (requestCode == SpendUtils.REQUEST_CODE_EXTERNAL_STORAGE && permissions.isNotEmpty() && permissions[0] == Manifest.permission.WRITE_EXTERNAL_STORAGE && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			btnBackup.performClick()
		} else {
			super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		}
	}

	private fun backupRealm(email: String) {
		if (TextUtils.isEmpty(email)) {
			Toast.makeText(this, getString(R.string.toast_empty_email), Toast.LENGTH_SHORT).show()
			return
		}
		val realm = Realm.getDefaultInstance()
		val mStorageRef: StorageReference = FirebaseStorage.getInstance().reference
		try {
			val path = applicationContext.getExternalFilesDir(null)
			if (path != null) {
				val file = File(path.path + "/trips.realm")
				if (file.exists()) {
					file.delete()
				}
				realm.writeCopyTo(file)
				val photoRef: StorageReference = mStorageRef.child("backups/$email/trips.realm")
				val uri: Uri = Uri.fromFile(file)
				val uploadTask: UploadTask = photoRef.putFile(uri)
				uploadTask.addOnCompleteListener {
					if (it.isSuccessful) {
						Toast.makeText(
							this,
							getString(R.string.toast_backup_complete),
							Toast.LENGTH_SHORT
						).show()
					}
				}
			} else {
				Toast.makeText(this, "Backup ", Toast.LENGTH_SHORT).show()
			}
		} catch (e: Exception) {
			e.printStackTrace()
		} finally {
			realm.close()
		}
	}
}
