package com.gmail.kamille1221.tripdiary

import android.Manifest
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Toast
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.UiSettings
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import io.realm.OrderedRealmCollection
import io.realm.Realm
import io.realm.RealmRecyclerViewAdapter
import io.realm.RealmResults
import kotlinx.android.synthetic.main.card_spend.view.*
import kotlinx.android.synthetic.main.dialog_add_spend.view.*
import java.util.*

/**
 * Created by Kamille on 2018-06-14.
 **/
class SpendAdapter(mContext: Context, private var mSpends: RealmResults<Spend>, private var realm: Realm, private val callback: RefreshTotalSpends, autoUpdate: Boolean = true): RealmRecyclerViewAdapter<Spend, RecyclerView.ViewHolder>(mContext, mSpends as OrderedRealmCollection<Spend>?, autoUpdate) {
	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		val spend: Spend? = mSpends[position]
		if (spend != null) {
			(holder as SpendHolder).bindSpend(spend)
		}
	}

	override fun getItemCount(): Int {
		return mSpends.size
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		return SpendHolder(LayoutInflater.from(parent.context).inflate(R.layout.card_spend, parent, false)).listen { position, _ ->
			val spend: Spend? = mSpends[position]
			if (spend != null) {
				showSpendDetail(spend)
			}
		}
	}

	private fun <T: RecyclerView.ViewHolder> T.listen(event: (position: Int, type: Int) -> Unit): T {
		itemView.setOnClickListener {
			event.invoke(adapterPosition, itemViewType)
		}
		return this
	}

	private fun showSpendDetail(spend: Spend) {
		val resource: Int = R.layout.dialog_add_spend
		val view = LayoutInflater.from(context).inflate(resource, null)
		val builder = AlertDialog.Builder(context)
		var date: Long = spend.date
		var currency: String = spend.currency
		var lat: Double
		var lng: Double
		builder.setTitle(context.getString(R.string.title_modify_spend))
		builder.setView(view)
		builder.setPositiveButton(context.getString(R.string.save), null)
		builder.setNeutralButton(context.getString(R.string.delete), null)
		builder.setNegativeButton(context.getString(R.string.cancel), null)
		view.etTitle.setText(spend.title)
		view.etContent.setText(spend.content)
		view.etDate.setText(SpendUtils.dateLongToString(date))
		view.etDate.setOnClickListener {
			val calendar: Calendar = Calendar.getInstance()
			calendar.timeInMillis = date
			val datePickerDialog = DatePickerDialog(context, DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
				val timePickerDialog = TimePickerDialog(context, TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
					calendar.set(year, month, dayOfMonth, hourOfDay, minute)
					date = calendar.timeInMillis
					view.etDate.setText(SpendUtils.dateLongToString(date))
				}, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false)
				timePickerDialog.show()
			}, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
			datePickerDialog.show()
		}
		view.etPrice.setText(spend.price.toString())
		view.spnCurrency.adapter = ArrayAdapter.createFromResource(context, R.array.currency, R.layout.spinner_item)
		view.spnCurrency.setSelection(SpendUtils.currencyStringToPosition(context, currency))
		view.spnCurrency.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
			override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
				currency = parent.getItemAtPosition(position).toString()
				SpendUtils.setLastCurrency(context, position)
			}

			override fun onNothingSelected(parent: AdapterView<*>) {}
		}
		lat = spend.lat
		lng = spend.lng
		MapsInitializer.initialize(context)
		view.mvLocation.onCreate(null)
		view.mvLocation.onResume()
		view.mvLocation.getMapAsync { googleMap ->
			googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 15f))
			googleMap.addMarker(MarkerOptions().position(LatLng(lat, lng)).title(""))
			val uiSettings: UiSettings = googleMap.uiSettings
			if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
				googleMap.isMyLocationEnabled = true
				uiSettings.isMyLocationButtonEnabled = true
			}
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
				val price: Int = view.etPrice.text.toString().toInt()
				if (TextUtils.isEmpty(title)) {
					Toast.makeText(context, context.getString(R.string.toast_empty_title_or_price), Toast.LENGTH_SHORT).show()
					view.etTitle.requestFocus()
				} else {
					updateRealm(spend.id, title, content, date, currency, price, spend.lat, spend.lng)
					dialog.dismiss()
					callback.refreshTotalSpends(date)
				}
			}
			val neutralButton: Button = alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
			neutralButton.setOnClickListener {
				val deleteBuilder = AlertDialog.Builder(context)
				deleteBuilder.setTitle(context.getString(R.string.title_delete_spend))
				deleteBuilder.setMessage(context.getString(R.string.message_delete_spend))
				deleteBuilder.setPositiveButton(context.getString(R.string.delete), { _, _ ->
					realm.beginTransaction()
					val deleteResult: RealmResults<Spend> = realm.where(Spend::class.java).equalTo("id", spend.id).findAll()
					deleteResult.deleteAllFromRealm()
					realm.commitTransaction()
					alertDialog.dismiss()
					callback.refreshTotalSpends(date)
				})
				deleteBuilder.setNegativeButton(context.getString(R.string.cancel), null)
				deleteBuilder.create().show()
			}
		}
		alertDialog.show()
	}

	@Synchronized
	private fun updateRealm(id: Int, title: String, content: String, date: Long, currency: String, price: Int, lat: Double, lng: Double) {
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
			realm.copyToRealmOrUpdate(spend)
			realm.commitTransaction()
		}
	}

	inner class SpendHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
		fun bindSpend(spend: Spend) {
			itemView.tvTitle.text = spend.title
			itemView.tvPrice.text = String.format(Locale.getDefault(), "%s %s", SpendUtils.priceIntToString(spend.price), spend.currency)
			itemView.tvDate.text = SpendUtils.dateLongToString(spend.date)
		}
	}

	interface RefreshTotalSpends {
		fun refreshTotalSpends(date: Long)
	}
}
