package com.gmail.kamille1221.tripdiary

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.gmail.kamille1221.tripdiary.SpendUtils.CHANNEL_ID_UPLOAD
import com.gmail.kamille1221.tripdiary.SpendUtils.REQUEST_CODE_EXTERNAL_STORAGE
import com.gmail.kamille1221.tripdiary.SpendUtils.REQUEST_CODE_PERMISSION
import com.gmail.kamille1221.tripdiary.SpendUtils.REQUEST_CODE_PICK_IMAGE
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.UiSettings
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmList
import io.realm.RealmResults
import io.realm.exceptions.RealmMigrationNeededException
import kotlinx.android.synthetic.main.activity_add_spend.*
import java.io.File
import java.util.*
import kotlin.properties.Delegates

/**
 * Created by Kamille on 2018-06-27.
 **/
class AddSpendActivity : AppCompatActivity() {
	private var realm: Realm by Delegates.notNull()
	private var realmConfig: RealmConfiguration by Delegates.notNull()
	private var id: Int = -1
	private var date: Long = System.currentTimeMillis()
	private var currency: Int = SpendUtils.getLastCurrency(this)
	private var lat: Double = -1.0
	private var lng: Double = -1.0
	private var uploadCount: Int = 0

	private val mStorageRef: StorageReference = FirebaseStorage.getInstance().reference

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_add_spend)

		initRealm()

		if (ContextCompat.checkSelfPermission(
				this,
				Manifest.permission.ACCESS_FINE_LOCATION
			) != PackageManager.PERMISSION_GRANTED
		) {
			if (ActivityCompat.checkSelfPermission(
					this,
					Manifest.permission.WRITE_EXTERNAL_STORAGE
				) != PackageManager.PERMISSION_GRANTED
			) {
				ActivityCompat.requestPermissions(
					this,
					arrayOf(
						Manifest.permission.ACCESS_FINE_LOCATION,
						Manifest.permission.WRITE_EXTERNAL_STORAGE
					),
					REQUEST_CODE_PERMISSION
				)
			} else {
				ActivityCompat.requestPermissions(
					this,
					arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
					REQUEST_CODE_PERMISSION
				)
			}
		}

		val actionBar: ActionBar? = supportActionBar
		actionBar?.setTitle(R.string.title_new_spend)
		actionBar?.setDisplayHomeAsUpEnabled(true)
		actionBar?.setHomeButtonEnabled(true)

		val mNotificationManager: NotificationManager =
			getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(
				CHANNEL_ID_UPLOAD,
				getString(R.string.title_photo_upload),
				NotificationManager.IMPORTANCE_HIGH
			)
			channel.setSound(null, null)
			mNotificationManager.createNotificationChannel(channel)
		}
		val mBuilder: NotificationCompat.Builder =
			NotificationCompat.Builder(this, CHANNEL_ID_UPLOAD)

		etDate.setText(SpendUtils.dateLongToString(date))
		etDate.setOnClickListener {
			val calendar: Calendar = Calendar.getInstance()
			calendar.timeInMillis = date
			val datePickerDialog = DatePickerDialog(
				this,
				DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
					val timePickerDialog = TimePickerDialog(
						this,
						TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
							calendar.set(year, month, dayOfMonth, hourOfDay, minute)
							date = calendar.timeInMillis
							etDate.setText(SpendUtils.dateLongToString(date))
						},
						calendar.get(Calendar.HOUR_OF_DAY),
						calendar.get(Calendar.MINUTE),
						false
					)
					timePickerDialog.show()
				},
				calendar.get(Calendar.YEAR),
				calendar.get(Calendar.MONTH),
				calendar.get(Calendar.DAY_OF_MONTH)
			)
			datePickerDialog.show()
		}
		spnCurrency.adapter =
			ArrayAdapter.createFromResource(this, R.array.currency, R.layout.spinner_item)
		spnCurrency.setSelection(currency)
		spnCurrency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
			override fun onItemSelected(
				parent: AdapterView<*>,
				view: View,
				position: Int,
				id: Long
			) {
				currency = position
				SpendUtils.setLastCurrency(this@AddSpendActivity, position)
			}

			override fun onNothingSelected(parent: AdapterView<*>) {
			}
		}

		rvPhotos.layoutManager = GridLayoutManager(this, 1, GridLayoutManager.HORIZONTAL, false)
		rvPhotos.adapter = PhotoAdapter(this, RealmList(), resources.displayMetrics.widthPixels / 5)

		MapsInitializer.initialize(this)
		mvLocation.onCreate(null)
		mvLocation.onResume()
		mapAsync()

		btnSave.setOnClickListener {
			val title: String = etTitle.text.toString()
			val content: String = etContent.text.toString()
			val price: String = etPrice.text.toString()
			when {
				TextUtils.isEmpty(title) -> {
					Toast.makeText(
						this,
						getString(R.string.toast_empty_title_or_price),
						Toast.LENGTH_SHORT
					).show()
					etTitle.requestFocus()
				}
				TextUtils.isEmpty(price) -> {
					Toast.makeText(
						this,
						getString(R.string.toast_empty_title_or_price),
						Toast.LENGTH_SHORT
					).show()
					etPrice.requestFocus()
				}
				else -> {
					val photoAdapter: PhotoAdapter = rvPhotos.adapter as PhotoAdapter
					if (id < 0) {
						id = commitRealm(
							title,
							content,
							date,
							SpendUtils.currencyPositionToString(this, currency),
							price.toInt(),
							lat,
							lng,
							RealmList()
						)
					} else {
						updateRealm(
							id,
							title,
							content,
							date,
							SpendUtils.currencyPositionToString(this, currency),
							price.toInt(),
							lat,
							lng,
							RealmList()
						)
					}
					finish()
					if (photoAdapter.getPhotos().isEmpty()) {
						return@setOnClickListener
					} else {
						val photos: ArrayList<String> = ArrayList()
						photoAdapter.getPhotos().forEach {
							photos.add(it)
						}
						mBuilder.setContentTitle(getString(R.string.title_photo_upload))
						mBuilder.setContentText(getString(R.string.text_photo_upload))
						mBuilder.setSmallIcon(R.drawable.ic_file_upload_black_24dp)
						mBuilder.setSound(null)
						mNotificationManager.notify(0, mBuilder.build())
						val uploadList: ArrayList<String> =
							(rvPhotos.adapter as PhotoAdapter).getUploadList()
						uploadList.forEach {
							val index: Int = photos.indexOf(it)
							val file = File(it)
							val uri: Uri = Uri.fromFile(file)
							val photoRef: StorageReference =
								mStorageRef.child("photos/${System.currentTimeMillis()}.${file.extension}")
							val uploadTask: UploadTask = photoRef.putFile(uri)
							mBuilder.setProgress(100, 0, true)
							mNotificationManager.notify(0, mBuilder.build())
							uploadTask.addOnProgressListener {
								Log.e(
									"NotifyLog",
									"onProgress ::: $index ::: $uploadCount ::: ${uploadList.size}"
								)
							}
							uploadTask.addOnFailureListener {
								Toast.makeText(
									this,
									R.string.toast_photo_upload_failed,
									Toast.LENGTH_SHORT
								).show()
							}
							uploadTask.continueWithTask { task ->
								if (!task.isSuccessful) {
									Toast.makeText(
										this,
										R.string.toast_photo_upload_failed,
										Toast.LENGTH_SHORT
									).show()
								}
								photoRef.downloadUrl
							}.addOnCompleteListener { task ->
								if (task.isSuccessful) {
									photos[index] = task.result.toString()
									++uploadCount
									Log.e(
										"NotifyLog",
										"onComplete ::: $uploadCount ::: ${uploadList.size}"
									)
									if (uploadCount >= uploadList.size) {
										Log.e(
											"NotifyLog",
											"onComplete ::: $uploadCount ::: ${uploadList.size}"
										)
										mBuilder.setContentText(getString(R.string.text_photo_upload_complete))
										mBuilder.setSmallIcon(R.drawable.ic_complete_black_24dp)
										mBuilder.setProgress(0, 0, false)
										mNotificationManager.notify(0, mBuilder.build())
										val realmPhotos: RealmList<String> = RealmList()
										photos.forEach { photo ->
											realmPhotos.add(photo)
										}
										updateRealm(
											id,
											title,
											content,
											date,
											SpendUtils.currencyPositionToString(this, currency),
											price.toInt(),
											lat,
											lng,
											realmPhotos
										)
										return@addOnCompleteListener
									}
								} else {
									Toast.makeText(
										this,
										R.string.toast_photo_upload_failed,
										Toast.LENGTH_SHORT
									).show()
								}
							}
						}
					}
				}
			}
		}

		btnCancel.setOnClickListener {
			setResult(RESULT_CANCELED)
			finish()
		}

		btnDelete.setOnClickListener {
			val deleteBuilder = AlertDialog.Builder(this)
			deleteBuilder.setTitle(R.string.title_delete_spend)
			deleteBuilder.setMessage(R.string.message_delete_spend)
			deleteBuilder.setPositiveButton(R.string.delete) { _, _ ->
				realm.beginTransaction()
				val deleteResult: RealmResults<Spend> =
					realm.where(Spend::class.java).equalTo("id", id).findAll()
				deleteResult.deleteAllFromRealm()
				realm.commitTransaction()
				finish()
			}
			deleteBuilder.setNegativeButton(R.string.cancel, null)
			deleteBuilder.create().show()
		}

		val bundle: Bundle = intent.extras ?: Bundle()
		if (!bundle.isEmpty) {
			actionBar?.setTitle(R.string.title_modify_spend)
			id = bundle.getInt("id")
			etTitle.setText(bundle.getString("title"))
			etContent.setText(bundle.getString("content"))
			date = bundle.getLong("date")
			etDate.setText(SpendUtils.dateLongToString(date))
			currency = SpendUtils.currencyStringToPosition(
				this,
				bundle.getString("currency") ?: getString(R.string.krw)
			)
			spnCurrency.setSelection(currency)
			etPrice.setText(bundle.getInt("price").toString())
			lat = bundle.getDouble("lat")
			lng = bundle.getDouble("lng")
			val photos: RealmList<String> = RealmList()
			bundle.getStringArrayList("photos")?.forEach {
				photos.add(it)
			}
			rvPhotos.adapter = PhotoAdapter(this, photos, resources.displayMetrics.widthPixels / 5)
			btnDelete.visibility = View.VISIBLE
		}
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

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
			(rvPhotos.adapter as PhotoAdapter).onActivityResult(data)
		} else {
			super.onActivityResult(requestCode, resultCode, data)
		}
	}

	override fun onRequestPermissionsResult(
		requestCode: Int,
		permissions: Array<out String>,
		grantResults: IntArray
	) {
		if (requestCode == REQUEST_CODE_PERMISSION && permissions.isNotEmpty()) {
			if ((permissions[0] == Manifest.permission.ACCESS_FINE_LOCATION && grantResults[0] == PackageManager.PERMISSION_GRANTED) || (permissions[1] == Manifest.permission.ACCESS_FINE_LOCATION && grantResults[1] == PackageManager.PERMISSION_GRANTED)) {
				mapAsync()
			}
		} else if (requestCode == REQUEST_CODE_EXTERNAL_STORAGE && permissions.isNotEmpty() && permissions[0] == Manifest.permission.WRITE_EXTERNAL_STORAGE && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			(rvPhotos.adapter as PhotoAdapter).onRequestPermissionsResult()
		} else {
			super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		}
	}

	private fun mapAsync() {
		mvLocation.getMapAsync { googleMap ->
			val uiSettings: UiSettings = googleMap.uiSettings
			if (ContextCompat.checkSelfPermission(
					this,
					Manifest.permission.ACCESS_FINE_LOCATION
				) == PackageManager.PERMISSION_GRANTED
			) {
				val locationManager: LocationManager =
					getSystemService(Context.LOCATION_SERVICE) as LocationManager
				val providers: List<String> = locationManager.getProviders(true)
				var location: Location? = null
				providers.forEach {
					val l = locationManager.getLastKnownLocation(it) ?: return@forEach
					if (location == null || location!!.accuracy > l.accuracy) {
						location = l
					}
				}
				if (lat < 0 && lng < 0) {
					lat = location?.latitude ?: SpendUtils.DEFAULT_LAT
					lng = location?.longitude ?: SpendUtils.DEFAULT_LNG
				}
				googleMap.isMyLocationEnabled = true
				uiSettings.isMyLocationButtonEnabled = true
			}
			googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 15f))
			uiSettings.isMapToolbarEnabled = false
			uiSettings.isRotateGesturesEnabled = false
			uiSettings.isTiltGesturesEnabled = false
			uiSettings.isZoomControlsEnabled = true
			uiSettings.isZoomGesturesEnabled = false
			googleMap.setOnCameraIdleListener {
				lat = googleMap.cameraPosition.target.latitude
				lng = googleMap.cameraPosition.target.longitude
			}
		}
	}

	private fun initRealm() {
		Realm.init(this)
		realmConfig = RealmConfiguration.Builder().build()
		realm = try {
			Realm.getInstance(realmConfig)
		} catch (e: RealmMigrationNeededException) {
			e.printStackTrace()
			Realm.deleteRealm(realmConfig)
			Realm.getInstance(realmConfig)
		}
	}

	@Synchronized
	private fun commitRealm(
		title: String,
		content: String,
		date: Long,
		currency: String,
		price: Int,
		lat: Double,
		lng: Double,
		photos: RealmList<String>
	): Int {
		realm.beginTransaction()
		val id: Int = realm.where(Spend::class.java).max("id")?.toInt() ?: 1
		val spend = realm.createObject(Spend::class.java, id + 1)
		spend.title = title
		spend.content = content
		spend.date = date
		spend.currency = currency
		spend.price = price
		spend.lat = lat
		spend.lng = lng
		spend.photos = photos
		realm.commitTransaction()

		return id + 1
	}

	@Synchronized
	private fun updateRealm(
		id: Int,
		title: String,
		content: String,
		date: Long,
		currency: String,
		price: Int,
		lat: Double,
		lng: Double,
		photos: RealmList<String>
	) {
		realm.beginTransaction()
		val spend: Spend? = realm.where(Spend::class.java).equalTo("id", id).findFirst()
		if (spend != null) {
			spend.title = title
			spend.content = content
			spend.date = date
			spend.currency = currency
			spend.price = price
			spend.lat = lat
			spend.lng = lng
			spend.photos = photos
			realm.copyToRealmOrUpdate(spend)
			realm.commitTransaction()
		}
	}
}
