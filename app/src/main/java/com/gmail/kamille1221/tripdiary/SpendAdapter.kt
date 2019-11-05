package com.gmail.kamille1221.tripdiary

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gmail.kamille1221.tripdiary.SpendUtils.REQUEST_CODE_MODIFY_SPEND
import io.realm.OrderedRealmCollection
import io.realm.Realm
import io.realm.RealmRecyclerViewAdapter
import io.realm.RealmResults
import kotlinx.android.synthetic.main.card_spend.view.*
import java.util.*

/**
 * Created by Kamille on 2018-06-14.
 **/
class SpendAdapter(
	mContext: Context,
	private var mSpends: RealmResults<Spend>,
	private var realm: Realm,
	private val callback: RefreshTotalSpends,
	autoUpdate: Boolean = true
) : RealmRecyclerViewAdapter<Spend, RecyclerView.ViewHolder>(
	mContext,
	mSpends as OrderedRealmCollection<Spend>?,
	autoUpdate
) {
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
		return SpendHolder(
			LayoutInflater.from(parent.context).inflate(
				R.layout.card_spend,
				parent,
				false
			)
		).listen { position, _ ->
			val spend: Spend? = mSpends[position]
			if (spend != null) {
				showSpendDetail(spend)
			}
		}
	}

	private fun <T : RecyclerView.ViewHolder> T.listen(event: (position: Int, type: Int) -> Unit): T {
		itemView.setOnClickListener {
			event.invoke(adapterPosition, itemViewType)
		}
		return this
	}

	private fun showSpendDetail(spend: Spend) {
		val bundle = Bundle()
		bundle.putInt("id", spend.id)
		bundle.putString("title", spend.title)
		bundle.putString("content", spend.content)
		bundle.putLong("date", spend.date)
		bundle.putString("currency", spend.currency)
		bundle.putDouble("price", spend.price)
		bundle.putDouble("lat", spend.lat)
		bundle.putDouble("lng", spend.lng)
		val photos: ArrayList<String> = ArrayList()
		spend.photos.forEach {
			photos.add(it)
		}
		bundle.putStringArrayList("photos", photos)
		(context as Activity).startActivityForResult(
			Intent(
				context,
				AddSpendActivity::class.java
			).putExtras(bundle), REQUEST_CODE_MODIFY_SPEND
		)
	}

	inner class SpendHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
		fun bindSpend(spend: Spend) {
			itemView.tvTitle.text = spend.title
			itemView.tvPrice.text = String.format(
				Locale.getDefault(),
				"%s %s",
				SpendUtils.priceDoubleToString(spend.price),
				spend.currency
			)
			itemView.tvDate.text = SpendUtils.dateLongToString(spend.date)
		}
	}

	interface RefreshTotalSpends {
		fun refreshTotalSpends(date: Long)
	}
}
