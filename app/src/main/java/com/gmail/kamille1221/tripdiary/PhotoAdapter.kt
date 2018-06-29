package com.gmail.kamille1221.tripdiary

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.support.v7.widget.AppCompatImageView
import android.support.v7.widget.RecyclerView
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import com.bumptech.glide.Glide
import com.gmail.kamille1221.tripdiary.SpendUtils.REQUEST_CODE_EXTERNAL_STORAGE
import com.gmail.kamille1221.tripdiary.SpendUtils.REQUEST_CODE_PICK_IMAGE
import io.realm.RealmList
import kotlinx.android.synthetic.main.dialog_photo.view.*
import java.util.*

/**
 * Created by Kamille on 2018-06-27.
 **/
class PhotoAdapter(private val context: Context, private val mPhotos: RealmList<String>, private val mHeight: Int): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
	companion object {
		private const val TYPE_HEADER: Int = 0
	}

	private val uploadList: ArrayList<String> = ArrayList()

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		val view: View = if (viewType == TYPE_HEADER) {
			LayoutInflater.from(context).inflate(R.layout.card_header, parent, false)
		} else {
			LayoutInflater.from(context).inflate(R.layout.card_photo, parent, false)
		}
		view.layoutParams.width = mHeight
		view.layoutParams.height = mHeight
		return if (viewType == TYPE_HEADER) {
			HeaderHolder(view)
		} else {
			PhotoHolder(view)
		}
	}

	override fun getItemCount(): Int {
		return if (mPhotos.isEmpty()) {
			1
		} else {
			mPhotos.size + 1
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		if (holder is HeaderHolder) {
			holder.flAdd.setOnClickListener {
				if (mPhotos.size >= 5) {
					Toast.makeText(context, context.getString(R.string.toast_max_photos), Toast.LENGTH_SHORT).show()
					return@setOnClickListener
				} else {
					if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
						(context as Activity).startActivityForResult(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI), REQUEST_CODE_PICK_IMAGE)
					} else {
						ActivityCompat.requestPermissions(context as Activity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_CODE_EXTERNAL_STORAGE)
						return@setOnClickListener
					}
				}
			}
		} else if (holder is PhotoHolder) {
			val adapterPosition = holder.adapterPosition - 1
			Glide.with(context).load(mPhotos[adapterPosition]).into(holder.ivPhoto)
			holder.ivPhoto.layoutParams.height = mHeight
			holder.ivPhoto.setOnClickListener {
				val resource: Int = R.layout.dialog_photo
				val view = (context as Activity).layoutInflater.inflate(resource, null)
				val builder = AlertDialog.Builder(context)
				builder.setView(view)
				view.ivPhoto.layoutParams.height = (mHeight * 3.64).toInt() // 3.64 ≈ 5 * 0.728
				Glide.with(context).load(mPhotos[adapterPosition]).into(view.ivPhoto)
				builder.setPositiveButton(context.getString(R.string.confirm), null)
				builder.create().show()
			}
			holder.ivPhoto.setOnLongClickListener {
				val photo: String? = mPhotos[adapterPosition]
				val builder = AlertDialog.Builder(context)
				builder.setTitle(context.getString(R.string.title_delete_photo))
				builder.setMessage(context.getString(R.string.message_delete_photo))
				builder.setCancelable(false)
				builder.setPositiveButton(R.string.delete) { _, _ ->
					if (photo != null && !TextUtils.isEmpty(photo)) {
						mPhotos.remove(photo)
					}
					notifyDataSetChanged()
				}
				builder.setNegativeButton(R.string.cancel, null)
				builder.create().show()
				true
			}
		}
	}

	override fun getItemViewType(position: Int): Int {
		return position
	}

	fun getPhotos(): RealmList<String> {
		return mPhotos
	}

	fun getUploadList(): ArrayList<String> {
		return uploadList
	}

	fun onActivityResult(data: Intent?) {
		if (data != null) {
			val uri: Uri = data.data
			val filePathColumn: Array<String> = arrayOf(MediaStore.Images.Media.DATA)
			val cursor: Cursor = context.contentResolver.query(uri, filePathColumn, null, null, null)
			cursor.moveToFirst()
			val columnIndex: Int = cursor.getColumnIndex(filePathColumn[0])
			val photoPath: String = cursor.getString(columnIndex)
			mPhotos.add(photoPath)
			uploadList.add(photoPath)
			notifyDataSetChanged()
			cursor.close()
		}
	}

	fun onRequestPermissionsResult() {
		(context as Activity).startActivityForResult(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI), REQUEST_CODE_PICK_IMAGE)
	}

	internal inner class HeaderHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
		var flAdd: FrameLayout = itemView.findViewById(R.id.flAdd)
	}

	internal inner class PhotoHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
		var ivPhoto: AppCompatImageView = itemView.findViewById(R.id.ivPhoto)
	}
}