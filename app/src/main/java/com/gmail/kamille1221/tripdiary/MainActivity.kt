package com.gmail.kamille1221.tripdiary

import android.Manifest
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.Toolbar
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Toast
import com.crashlytics.android.Crashlytics
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.UiSettings
import com.google.android.gms.maps.model.LatLng
import io.fabric.sdk.android.Fabric
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmResults
import io.realm.Sort
import io.realm.exceptions.RealmMigrationNeededException
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.dialog_add_spend.view.*
import java.util.*
import kotlin.properties.Delegates

/**
 * Created by Kamille on 2018-06-14.
 **/
class MainActivity: AppCompatActivity(), SpendAdapter.RefreshTotalSpends {
	companion object {
		const val REQUEST_CODE_ACCESS_FINE_LOCATION: Int = 0
	}

	private lateinit var mAdapter: SpendAdapter
	private var realm: Realm by Delegates.notNull()
	private var realmConfig: RealmConfiguration by Delegates.notNull()
	private var selectedDate: Long = System.currentTimeMillis()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		Fabric.with(this, Crashlytics())
		initRealm()

		val actionBar: Toolbar = toolbar
		setSupportActionBar(actionBar)

		cvSpends.setOnDateChangeListener { _, year, month, dayOfMonth ->
			val calendar: Calendar = Calendar.getInstance()
			calendar.set(year, month, dayOfMonth, 0, 0, 0)
			selectedDate = calendar.timeInMillis
			refreshSpends(selectedDate)
		}

		rvSpends.setHasFixedSize(true)
		rvSpends.layoutManager = LinearLayoutManager(this)
		refreshSpends(System.currentTimeMillis())
		showTotalSpends(System.currentTimeMillis())

		fabAdd.setOnClickListener { addSpend() }
	}

	override fun onCreateOptionsMenu(menu: Menu?): Boolean {
		menuInflater.inflate(R.menu.menu_main, menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem?): Boolean {
		return when (item?.itemId) {
			R.id.action_map -> {
				startActivity(Intent(this, MapsActivity::class.java))
				true
			}
			R.id.action_about -> {
				startActivity(Intent(this, AboutActivity::class.java))
				true
			}
			else ->
				super.onOptionsItemSelected(item)
		}
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
		when (requestCode) {
			REQUEST_CODE_ACCESS_FINE_LOCATION -> {
				if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				}
			}
		}
	}

	override fun refreshTotalSpends(date: Long) {
		showTotalSpends(date)
	}

	private fun refreshSpends(date: Long) {
		val spends: RealmResults<Spend> = if (date < 0L) {
			getSpends()
		} else {
			getSpends(date)
		}
		mAdapter = SpendAdapter(this, spends, realm, this)
		mAdapter.notifyDataSetChanged()
		rvSpends.adapter = mAdapter
		showTotalSpends(date)
	}

	private fun showTotalSpends(date: Long) {
		var millis: Long = date
		var total = 0
		var currency = ""
		if (millis < 0L) {
			millis = System.currentTimeMillis()
		}
		getSpends(millis).forEach {
			total += it.price
			currency = it.currency
		}
		if (total <= 0) {
			tvTotalSpends.visibility = View.GONE
		} else {
			tvTotalSpends.visibility = View.VISIBLE
			tvTotalSpends.text = String.format(Locale.getDefault(), "%s %s %s", getString(R.string.total), SpendUtils.priceIntToString(total), currency)
		}
	}

	private fun getSpends(): RealmResults<Spend> {
		return realm.where(Spend::class.java).findAll().sort("date", Sort.ASCENDING)
	}

	private fun getSpends(date: Long): RealmResults<Spend> {
		val startMillis: Calendar = Calendar.getInstance()
		val endMillis: Calendar = Calendar.getInstance()
		startMillis.timeInMillis = date
		startMillis.set(Calendar.HOUR_OF_DAY, 0)
		startMillis.set(Calendar.MINUTE, 0)
		startMillis.set(Calendar.SECOND, 0)
		startMillis.set(Calendar.MILLISECOND, 0)
		endMillis.timeInMillis = startMillis.timeInMillis + 86400000 // 1000ms * 60s * 60m * 24h
		return realm.where(Spend::class.java).greaterThanOrEqualTo("date", startMillis.timeInMillis).lessThanOrEqualTo("date", endMillis.timeInMillis).findAll().sort("date", Sort.ASCENDING)
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

	private fun commitRealm(title: String, content: String, date: Long, currency: String, price: Int, lat: Double, lng: Double) {
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
		realm.commitTransaction()
	}

	private fun addSpend() {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_CODE_ACCESS_FINE_LOCATION)
		}
		val resource: Int = R.layout.dialog_add_spend
		val view = this.layoutInflater.inflate(resource, null)
		val builder = AlertDialog.Builder(this)
		var date: Long = selectedDate
		var currency: Int = SpendUtils.getLastCurrency(this)
		var lat: Double = SpendUtils.DEFAULT_LAT
		var lng: Double = SpendUtils.DEFAULT_LNG
		builder.setTitle(getString(R.string.title_new_spend))
		builder.setView(view)
		builder.setPositiveButton(getString(R.string.save), null)
		builder.setNegativeButton(getString(R.string.cancel), null)
		view.etDate.setText(SpendUtils.dateLongToString(date))
		view.etDate.setOnClickListener {
			val calendar: Calendar = Calendar.getInstance()
			calendar.timeInMillis = date
			val datePickerDialog = DatePickerDialog(this, DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
				val timePickerDialog = TimePickerDialog(this, TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
					calendar.set(year, month, dayOfMonth, hourOfDay, minute)
					date = calendar.timeInMillis
					view.etDate.setText(SpendUtils.dateLongToString(date))
				}, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false)
				timePickerDialog.show()
			}, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
			datePickerDialog.show()
		}
		view.spnCurrency.adapter = ArrayAdapter.createFromResource(this, R.array.currency, R.layout.spinner_item)
		view.spnCurrency.setSelection(currency)
		view.spnCurrency.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
			override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
				currency = position
				SpendUtils.setLastCurrency(this@MainActivity, position)
			}

			override fun onNothingSelected(parent: AdapterView<*>) {
			}
		}

		MapsInitializer.initialize(this)
		view.mvLocation.onCreate(null)
		view.mvLocation.onResume()
		view.mvLocation.getMapAsync { googleMap ->
			val uiSettings: UiSettings = googleMap.uiSettings
			if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
				val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
				val location: Location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
				lat = location.latitude
				lng = location.longitude
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

		val alertDialog: AlertDialog = builder.create()
		alertDialog.setOnShowListener { dialog ->
			val positiveButton: Button = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
			positiveButton.setOnClickListener {
				val title: String = view.etTitle.text.toString()
				val content: String = view.etContent.text.toString()
				val price: String = view.etPrice.text.toString()
				when {
					TextUtils.isEmpty(title) -> {
						Toast.makeText(this, getString(R.string.toast_empty_title_or_price), Toast.LENGTH_SHORT).show()
						view.etTitle.requestFocus()
					}
					TextUtils.isEmpty(price) -> {
						Toast.makeText(this, getString(R.string.toast_empty_title_or_price), Toast.LENGTH_SHORT).show()
						view.etPrice.requestFocus()
					}
					else -> {
						commitRealm(title, content, date, SpendUtils.currencyPositionToString(this, currency), price.toInt(), lat, lng)
						dialog.dismiss()
						showTotalSpends(date)
					}
				}
			}
		}
		alertDialog.show()
	}
}
