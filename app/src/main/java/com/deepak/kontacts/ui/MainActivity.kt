package com.deepak.kontacts.ui

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.View
import android.view.WindowManager
import com.deepak.kontacts.R
import com.deepak.kontacts.db.MyContacts
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast

class MainActivity : AppCompatActivity() {
    @Suppress("unused")
    companion object {
        private const val PERMISSION_READ_CONTACT = 101
        private const val PERMISSION_CALL_PHONE = 102

        private val CONTENT_URI = ContactsContract.Contacts.CONTENT_URI
        private val PHONE_URI = ContactsContract.CommonDataKinds.Phone.CONTENT_URI

        private const val ID = ContactsContract.Contacts._ID
        private const val DISPLAY_NAME = ContactsContract.Contacts.DISPLAY_NAME
        private const val HAS_PHONE_NUMBER = ContactsContract.Contacts.HAS_PHONE_NUMBER
        private const val PHONE_ID = ContactsContract.CommonDataKinds.Phone.CONTACT_ID
        private const val NUMBER = ContactsContract.CommonDataKinds.Phone.NUMBER

        private const val CONTACT_NAME = "CONTACT_NAME"
        private const val CONTACT_PHONE = "CONTACT_PHONE"
        private const val CONTACT_IMAGE = "CONTACT_IMAGE"
        private const val IS_FETCHED_FROM_CP = "CONTACT_IMAGE"
        private const val KONTACT_PREFERENCES = "KONTACT_PREFERENCES"
    }

    private var adapter: ContactsAdapter? = null
    private var myContacts : MutableList<MyContacts> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val itemDecoration = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        recycler_view.layoutManager = LinearLayoutManager(this)
        recycler_view.addItemDecoration(itemDecoration)
        adapter = ContactsAdapter(myContacts) { contact -> onItemClick(contact) }
        recycler_view.hasFixedSize()
        recycler_view.adapter = adapter

        checkPermission()
    }

    private fun onItemClick(contact: MyContacts?) {
        val phone = String.format("tel: %s", contact?.contactNumber!![0])
        val intent = Intent(Intent.ACTION_CALL, Uri.parse(phone))
        if (intent.resolveActivity(packageManager) != null) {
            if (ActivityCompat.checkSelfPermission(this,Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), PERMISSION_CALL_PHONE)
                }else {
                    toast("Allow this application to make phone calls")
                }
            }
            startActivity(intent)
        }else {
            toast("Can't make call without permission")
        }
    }

    private fun loadContactFromProvider() {
        log("loading from Provider")
        showProgressBar()
        doAsync {
            val contentResolver = contentResolver
            val cursor = contentResolver.query(CONTENT_URI, null, null, null, DISPLAY_NAME)

            log("loading started...")
            if (cursor != null && cursor.count > 0) {
                while (cursor.moveToNext()) {
                    log("loading name")
                    val id = cursor.getString(cursor.getColumnIndex(ID))
                    val name = cursor.getString(cursor.getColumnIndex(DISPLAY_NAME))
                    val hasPhoneNumber = cursor.getInt(cursor.getColumnIndex(HAS_PHONE_NUMBER))
                    val contacts = MyContacts()
                    if (hasPhoneNumber > 0) {
                        contacts.contactName = name
                        val phoneCursor = contentResolver.query(PHONE_URI, arrayOf(NUMBER), "$PHONE_ID = ?", arrayOf(id), null)
                        val phoneNumbers = ArrayList<String>()
                        phoneCursor!!.moveToFirst()
                        while (!phoneCursor.isAfterLast) {
                            val phoneNumber = phoneCursor.getString(phoneCursor.getColumnIndex(NUMBER)).replace(" ", "")
                            phoneNumbers.add(phoneNumber)
                            phoneCursor.moveToNext()
                        }
                        contacts.contactNumber = phoneNumbers
                        phoneCursor.close()
                    }
                    log("loading image")
                    val inputStream = ContactsContract.Contacts.openContactPhotoInputStream(contentResolver, ContentUris.withAppendedId(CONTENT_URI, id.toLong()), true)
                    if (inputStream != null) {
                        val bitmap: Bitmap? = BitmapFactory.decodeStream(inputStream)
                        contacts.contactImage = bitmap
                    } else {
                        contacts.contactImage = vectorDrawableToBitmap(R.drawable.ic_person)
                    }
                    log("""${contacts.contactName} ${contacts.contactNumber} ${contacts.contactImage.toString()}""")
                    myContacts.add(contacts)
                }
                adapter?.notifyDataSetChanged()
                cursor.close()
            }
        }
        hideProgressBar()
        log("loading done...")
    }

    private fun vectorDrawableToBitmap(drawableId: Int): Bitmap? {
        val drawable: Drawable? = ContextCompat.getDrawable(this, drawableId)
        val bitmap: Bitmap

        return when {
            drawable != null -> {
                bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
            else -> null
        }
    }

    private fun checkPermission() {
        val contactReadPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val callPhonePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        when {
            contactReadPermission && callPhonePermission -> loadContactFromProvider()
            else -> when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), PERMISSION_READ_CONTACT)
                    requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), PERMISSION_CALL_PHONE)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == PERMISSION_READ_CONTACT && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadContactFromProvider()
        }
    }

    private fun showProgressBar() {
        progress_bar.visibility = View.VISIBLE
//        constraint_layout.setBackgroundColor(Color.GRAY)
        window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    private fun hideProgressBar() {
        progress_bar.visibility = View.GONE
//        constraint_layout.setBackgroundColor(Color.WHITE)
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    private fun log(message: String) = Log.d("DEBUG",message)

}