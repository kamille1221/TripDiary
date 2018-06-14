package com.gmail.kamille1221.tripdiary

import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.ActionBar
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmResults
import io.realm.Sort
import io.realm.exceptions.RealmMigrationNeededException
import kotlin.properties.Delegates

/**
 * Created by Kamille on 2018-06-14.
 **/
class MapsActivity: AppCompatActivity(), OnMapReadyCallback {
	private lateinit var mMap: GoogleMap
	private var realm: Realm by Delegates.notNull()
	private var realmConfig: RealmConfiguration by Delegates.notNull()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_maps)
		val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
		mapFragment.getMapAsync(this)

		initRealm()

		val actionBar: ActionBar? = supportActionBar
		if (actionBar != null) {
			actionBar.setTitle(R.string.history_map)
			actionBar.setDisplayHomeAsUpEnabled(true)
			actionBar.setHomeButtonEnabled(true)
		}
	}

	override fun onMapReady(googleMap: GoogleMap) {
		mMap = googleMap
		mMap.isMyLocationEnabled = true
		val uiSettings: UiSettings = mMap.uiSettings
		uiSettings.isMapToolbarEnabled = false
		uiSettings.isRotateGesturesEnabled = false
		uiSettings.isTiltGesturesEnabled = false
		uiSettings.isZoomControlsEnabled = true
		uiSettings.isZoomGesturesEnabled = false

		val markers: ArrayList<Marker> = ArrayList()
		getSpends().forEach {
			val latLng = LatLng(it.lat, it.lng)
			if (it.lat >= 0.0 && it.lng >= 0.0) {
				markers.add(mMap.addMarker(MarkerOptions().position(latLng).title(it.title)))
			}
		}
		if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
			mMap.isMyLocationEnabled = true
			uiSettings.isMyLocationButtonEnabled = true
		}
		when {
			markers.size > 1 -> {
				val builder = LatLngBounds.Builder()
				for (marker in markers) {
					builder.include(marker.position)
				}
				val bounds = builder.build()
				mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0))
				mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mMap.cameraPosition.target, mMap.cameraPosition.zoom - 1f))
			}
			markers.size == 1 -> {
				mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(markers[0].position.latitude, markers[0].position.longitude), 15f))
			}
			else -> {
				mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(SpendUtils.DEFAULT_LAT, SpendUtils.DEFAULT_LNG), 15f))
			}
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

	private fun getSpends(): RealmResults<Spend> {
		return realm.where(Spend::class.java).findAll().sort("date", Sort.ASCENDING)
	}
}
