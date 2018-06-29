package com.gmail.kamille1221.tripdiary

import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

/**
 * Created by Kamille on 2018-06-14.
 **/
open class Spend: RealmObject() {
	@PrimaryKey
	open var id: Int = -1
	open var title: String = ""
	open var content: String = ""
	open var currency: String = ""
	open var price: Int = 0
	open var lat: Double = 0.0
	open var lng: Double = 0.0
	open var photos: RealmList<String> = RealmList()
	open var date: Long = 0L
}
