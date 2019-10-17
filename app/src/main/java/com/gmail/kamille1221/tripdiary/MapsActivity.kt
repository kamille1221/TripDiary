package com.gmail.kamille1221.tripdiary

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.gmail.kamille1221.tripdiary.SpendUtils.REQUEST_CODE_PERMISSION
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
class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
	private lateinit var mMap: GoogleMap
	private var realm: Realm by Delegates.notNull()
	private var realmConfig: RealmConfiguration by Delegates.notNull()
	private lateinit var mapFragment: SupportMapFragment

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_maps)

		mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
		mapFragment.getMapAsync(this)

		initRealm()

		val actionBar: ActionBar? = supportActionBar
		actionBar?.setTitle(R.string.history_map)
		actionBar?.setDisplayHomeAsUpEnabled(true)
		actionBar?.setHomeButtonEnabled(true)
	}

	override fun onMapReady(googleMap: GoogleMap) {
		mMap = googleMap
		val uiSettings: UiSettings = mMap.uiSettings
		if (ContextCompat.checkSelfPermission(
				this,
				Manifest.permission.ACCESS_FINE_LOCATION
			) != PackageManager.PERMISSION_GRANTED
		) {
			ActivityCompat.requestPermissions(
				this,
				arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
				REQUEST_CODE_PERMISSION
			)
		} else {
			mMap.isMyLocationEnabled = true
			uiSettings.isMyLocationButtonEnabled = true
		}
		uiSettings.isMapToolbarEnabled = false
		uiSettings.isRotateGesturesEnabled = false
		uiSettings.isTiltGesturesEnabled = false
		uiSettings.isZoomControlsEnabled = true
		uiSettings.isZoomGesturesEnabled = false

		val markers: ArrayList<Marker> = ArrayList()
		getSpends().forEach {
			val latLng = LatLng(it.lat, it.lng)
			if (it.lat >= 0.0 && it.lng >= 0.0) {
				markers.add(
					mMap.addMarker(
						MarkerOptions().position(latLng).title(it.title).snippet(
							SpendUtils.dateLongToString(it.date)
						)
					)
				)
			}
		}
		if (ContextCompat.checkSelfPermission(
				this,
				Manifest.permission.ACCESS_FINE_LOCATION
			) == PackageManager.PERMISSION_GRANTED
		) {
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
				mMap.moveCamera(
					CameraUpdateFactory.newLatLngZoom(
						mMap.cameraPosition.target,
						mMap.cameraPosition.zoom - 1f
					)
				)
			}
			markers.size == 1 -> {
				mMap.moveCamera(
					CameraUpdateFactory.newLatLngZoom(
						LatLng(
							markers[0].position.latitude,
							markers[0].position.longitude
						), 15f
					)
				)
			}
			else -> {
				if (ContextCompat.checkSelfPermission(
						this,
						Manifest.permission.ACCESS_FINE_LOCATION
					) != PackageManager.PERMISSION_GRANTED
				) {
					mMap.moveCamera(
						CameraUpdateFactory.newLatLngZoom(
							LatLng(
								SpendUtils.DEFAULT_LAT,
								SpendUtils.DEFAULT_LNG
							), 15f
						)
					)
				} else {
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
					val lat = location?.latitude ?: SpendUtils.DEFAULT_LAT
					val lng = location?.longitude ?: SpendUtils.DEFAULT_LNG
					mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 15f))
				}
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

	override fun onRequestPermissionsResult(
		requestCode: Int,
		permissions: Array<out String>,
		grantResults: IntArray
	) {
		when (requestCode) {
			REQUEST_CODE_PERMISSION -> {
				if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					mapFragment.getMapAsync(this)
				}
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

	private fun getSpends(): RealmResults<Spend> {
		return realm.where(Spend::class.java).findAll().sort("date", Sort.ASCENDING)
	}
}
