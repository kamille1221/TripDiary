package com.gmail.kamille1221.tripdiary

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import com.crashlytics.android.Crashlytics
import com.gmail.kamille1221.tripdiary.SpendUtils.REQUEST_CODE_ADD_SPEND
import com.gmail.kamille1221.tripdiary.SpendUtils.REQUEST_CODE_MODIFY_SPEND
import io.fabric.sdk.android.Fabric
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmResults
import io.realm.Sort
import io.realm.exceptions.RealmMigrationNeededException
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import java.util.*
import kotlin.properties.Delegates

/**
 * Created by Kamille on 2018-06-14.
 **/
class MainActivity : AppCompatActivity(), SpendAdapter.RefreshTotalSpends {
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

		fabAdd.setOnClickListener { addSpend() }
	}

	override fun onResume() {
		super.onResume()
		refreshSpends(selectedDate)
		showTotalSpends(selectedDate)
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		if (resultCode == RESULT_OK) {
			when (requestCode) {
				REQUEST_CODE_ADD_SPEND, REQUEST_CODE_MODIFY_SPEND -> refreshSpends(selectedDate)
			}
		}
		super.onActivityResult(requestCode, resultCode, data)
	}

	override fun onCreateOptionsMenu(menu: Menu?): Boolean {
		menuInflater.inflate(R.menu.menu_main, menu)
		when (SpendUtils.getLastCurrency(this)) {
			0 -> menu?.findItem(R.id.currency_krw)?.isChecked = true
			1 -> menu?.findItem(R.id.currency_usd)?.isChecked = true
			2 -> menu?.findItem(R.id.currency_jpy)?.isChecked = true
			3 -> menu?.findItem(R.id.currency_eur)?.isChecked = true
			4 -> menu?.findItem(R.id.currency_gbp)?.isChecked = true
			5 -> menu?.findItem(R.id.currency_twd)?.isChecked = true
		}
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		return when (item.itemId) {
			R.id.action_map -> {
				startActivity(Intent(this, MapsActivity::class.java))
				true
			}
			R.id.action_about -> {
				startActivity(Intent(this, AboutActivity::class.java))
				true
			}
			R.id.currency_krw -> {
				SpendUtils.setLastCurrency(this, 0)
				item.isChecked = true
				true
			}

			R.id.currency_usd -> {
				SpendUtils.setLastCurrency(this, 1)
				item.isChecked = true
				true
			}

			R.id.currency_jpy -> {
				SpendUtils.setLastCurrency(this, 2)
				item.isChecked = true
				true
			}

			R.id.currency_eur -> {
				SpendUtils.setLastCurrency(this, 3)
				item.isChecked = true
				true
			}

			R.id.currency_gbp -> {
				SpendUtils.setLastCurrency(this, 4)
				item.isChecked = true
				true
			}

			R.id.currency_twd -> {
				SpendUtils.setLastCurrency(this, 5)
				item.isChecked = true
				true
			}
			else -> {
				super.onOptionsItemSelected(item)
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
		var dailyTotal = 0
		val monthlyTotal: Int = getMonthlySpends(date)
		var currency: String =
			SpendUtils.currencyPositionToString(this, SpendUtils.getLastCurrency(this))
		if (millis < 0L) {
			millis = System.currentTimeMillis()
		}
		getSpends(millis).forEach {
			dailyTotal += it.price
			currency = it.currency
		}
		if (dailyTotal <= 0 && monthlyTotal <= 0) {
			tvTotalSpends.visibility = View.GONE
		} else {
			tvTotalSpends.visibility = View.VISIBLE
			tvTotalSpends.text = String.format(
				Locale.getDefault(),
				"%s %s / %s %s",
				getString(R.string.total),
				SpendUtils.priceIntToString(dailyTotal),
				SpendUtils.priceIntToString(monthlyTotal),
				currency
			)
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
		return realm.where(Spend::class.java).greaterThanOrEqualTo("date", startMillis.timeInMillis)
			.lessThan("date", endMillis.timeInMillis).findAll().sort("date", Sort.ASCENDING)
	}

	private fun getMonthlySpends(date: Long): Int {
		val startMillis: Calendar = Calendar.getInstance()
		val endMillis: Calendar = Calendar.getInstance()
		var result = 0
		startMillis.timeInMillis = date
		startMillis.set(Calendar.DAY_OF_MONTH, 1)
		startMillis.set(Calendar.HOUR_OF_DAY, 0)
		startMillis.set(Calendar.MINUTE, 0)
		startMillis.set(Calendar.SECOND, 0)
		startMillis.set(Calendar.MILLISECOND, 0)
		endMillis.timeInMillis = startMillis.timeInMillis
		endMillis.add(Calendar.DAY_OF_MONTH, endMillis.getActualMaximum(Calendar.DAY_OF_MONTH))
		realm.where(Spend::class.java).greaterThanOrEqualTo("date", startMillis.timeInMillis)
			.lessThan("date", endMillis.timeInMillis).findAll().sort("date", Sort.ASCENDING)
			.forEach {
				result += it.price
			}
		return result
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

	private fun addSpend() {
		startActivityForResult(Intent(this, AddSpendActivity::class.java), REQUEST_CODE_ADD_SPEND)
	}
}
