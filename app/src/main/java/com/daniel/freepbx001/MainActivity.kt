package com.daniel.freepbx001

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.Executors


// Constants
// The authority for the sync adapter's content provider
const val AUTHORITY = "com.daniel.freepbx001.provider"
//
// An account type, in the form of a domain name
const val ACCOUNT_TYPE = "daniel.com"
// The account name
const val ACCOUNT = "placeholderaccount"
class MainActivity : AppCompatActivity() {
    private var serverUp = false
    private val PERMISSION_REQUEST_CODE = 100
    private val REQUEST_CODE_FOR_CALL = 1001
    private lateinit var mAccount: Account
    private val permissions = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.INTERNET,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_SYNC_SETTINGS,
        Manifest.permission.WRITE_SYNC_SETTINGS
    )
    private lateinit var telephonyManager: TelephonyManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // get reference to button
        if (!hasPermissions(this, *permissions)) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
        } else {
            // Permissions have already been granted
        }
        var btn_start = findViewById(R.id.startbtn) as Button
        var label = findViewById(R.id.number) as TextView
        label.text = "Server is offline"

        btn_start.setOnClickListener {
            serverUp = if(!serverUp){
                startServer(8001)
                true
            } else{
                stopServer()
                false
            }

        }

        var btn_check = findViewById(R.id.checkbtn) as Button
        var statusCall = findViewById(R.id.txtCallStatus) as TextView
        statusCall.text = "Unknown"

        btn_check.setOnClickListener {
            statusCall.text = telephonyManager.callState.toString()
        }

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        var phoneStateListener: PhoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, incomingNumber: String) {
                super.onCallStateChanged(state, incomingNumber)
                Log.d(state.toString(), incomingNumber)
                when (state) {
                    TelephonyManager.CALL_STATE_IDLE -> {
                        Log.d("BINGO", "IDLE")
                    }
                    TelephonyManager.CALL_STATE_RINGING -> {
                        Log.d("BINGO", "Ringing")
                    }
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        Log.d("BINGO", "offhook")
                    }
                }
            }
        }
        //register listener
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        // Create the placeholder account
        mAccount = createSyncAccount()
    }
    private fun hasPermissions(context: Context, vararg permissions: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (permission in permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false
                }
            }
        }
        return true
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permissions have been granted
                } else {
                    // Permissions have been denied
                }
            }
        }
    }
    /**
     * Create a new placeholder account for the sync adapter
     */
    private fun createSyncAccount(): Account {
        val accountManager = getSystemService(Context.ACCOUNT_SERVICE) as AccountManager
        return Account(ACCOUNT, ACCOUNT_TYPE).also { newAccount ->
            /*
             * Add the account and account type, no password or user data
             * If successful, return the Account object, otherwise report an error.
             */
            if (accountManager.addAccountExplicitly(newAccount, null, null)) {
                /*
                 * If you don't set android:syncable="true" in
                 * in your <provider> element in the manifest,
                 * then call context.setIsSyncable(account, AUTHORITY, 1)
                 * here.
                 */
            } else {
                /*
                 * The account exists or some other error occurred. Log this, report it,
                 * or handle it internally.
                 */
            }
        }
    }

    private fun streamToString(inputStream: InputStream): String {
        val s = Scanner(inputStream).useDelimiter("\\A")
        return if (s.hasNext()) s.next() else ""
    }

    private fun sendResponse(httpExchange: HttpExchange, responseText: String){
        httpExchange.sendResponseHeaders(200, responseText.length.toLong())
        val os = httpExchange.responseBody
        os.write(responseText.toByteArray())
        os.close()
    }

    private var mHttpServer: HttpServer? = null

    private fun startServer(port: Int) {
        try {
            mHttpServer = HttpServer.create(InetSocketAddress(port), 0)
            mHttpServer!!.executor = Executors.newCachedThreadPool()

            mHttpServer!!.createContext("/", rootHandler)
            mHttpServer!!.createContext("/index", rootHandler)
            // Handle /messages endpoint
            mHttpServer!!.createContext("/add", addHandler)
            mHttpServer!!.createContext("/call", callHandler)
            mHttpServer!!.createContext("/state", stateHandler)

            mHttpServer!!.start()//startServer server;
            var label = findViewById(R.id.number) as TextView
            label.text = "Server is online"
            var btn_start = findViewById(R.id.startbtn) as Button
            btn_start.text = "Stop Server"

        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    private fun stopServer() {
        if (mHttpServer != null){
            mHttpServer!!.stop(0)
            var label = findViewById(R.id.number) as TextView
            label.text = "Server is offline"
            var btn_start = findViewById(R.id.startbtn) as Button
            btn_start.text = "Start Server"
        }

    }

    // Handler for root endpoint
    private val rootHandler = HttpHandler { exchange ->
        run {
            // Get request method
            when (exchange!!.requestMethod) {
                "GET" -> {
                    sendResponse(exchange, "Welcome to my server")
                }

            }
        }

    }
    // Handler for root endpoint
    private val callHandler = HttpHandler { httpExchange ->
        run {
            // Get request method
            when (httpExchange!!.requestMethod) {
                "POST" -> {
                    val inputStream = httpExchange.requestBody
                    val requestBody = streamToString(inputStream)

                    val jsonObject = JSONObject()
                    val keyValuePairs = requestBody.split("&")
                    for (pair in keyValuePairs) {
                        val (key, value) = pair.split("=")
                        jsonObject.put(key, value)
                    }
                    val phone = jsonObject.getString("phoneNumber")

                    initiateWhatsAppCall(this, phone)
                    sendResponse(httpExchange, "calling...")
                }

            }
        }
    }
    // Handler for root endpoint
    private val stateHandler = HttpHandler { httpExchange ->
        run {
            // Get request method
            when (httpExchange!!.requestMethod) {
                "POST" -> {
                    var res = telephonyManager.callState.toString()
                    if (res == null) {
                        Log.d("BINGO", "here")
                        res = "hey"
                    }
                    sendResponse(httpExchange, res)
                }

            }
        }

    }
    @SuppressLint("Range")
    private val addHandler = HttpHandler { httpExchange ->
        run {
            when (httpExchange!!.requestMethod) {
                "GET" -> {
                    // Get all messages
                    sendResponse(httpExchange, "Would be all messages stringified json")
                }
                "POST" -> {
                    val inputStream = httpExchange.requestBody
                    val requestBody = streamToString(inputStream)

                    val jsonObject = JSONObject()
                    val keyValuePairs = requestBody.split("&")
                    for (pair in keyValuePairs) {
                        val (key, value) = pair.split("=")
                        jsonObject.put(key, value)
                    }
                    val name = jsonObject.getString("name")
                    val phone = jsonObject.getString("phone")

                    val responseObj = JSONObject()
                    // add contact list
                    if (addPhoneNumberToContact(this, phone, phone)) {
                        responseObj.put("isAddedToContactList", true)
                    }
                    else {
                        responseObj.put("isAddedToContactList", false)
                    }
                    syncWhatsappContacts()

                    sendResponse(httpExchange, responseObj.toString())
                }
            }
        }
    }

    @SuppressLint("Range")
    fun initiateWhatsAppCall(context: Context, phoneNumber: String) {
        val resolver = context.contentResolver
        val cursor = resolver.query(
            ContactsContract.Data.CONTENT_URI,
            null, null, null,
            ContactsContract.Contacts.DISPLAY_NAME
        )

        if (cursor != null) {
            while (cursor.moveToNext()) {
                val _id: Long = cursor.getLong(cursor.getColumnIndex(ContactsContract.Data._ID))
                val displayName: String =
                    cursor.getString(cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME))
                val mimeType: String =
                    cursor.getString(cursor.getColumnIndex(ContactsContract.Data.MIMETYPE))
                Log.d("Data", "$_id $displayName $mimeType")

                //check if name=phone number & mimetype=whatsapp
                if (displayName == phoneNumber && mimeType == "vnd.android.cursor.item/vnd.com.whatsapp.voip.call") {
                    //make direct call
                    val whatsappintent = Intent()
                    whatsappintent.action = Intent.ACTION_VIEW
                    whatsappintent.setDataAndType(
                        Uri.parse("content://com.android.contacts/data/$_id"),
                        "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
                    )
                    whatsappintent.setPackage("com.whatsapp")

                    startActivityForResult(whatsappintent, REQUEST_CODE_FOR_CALL)
//                    startActivity(whatsappintent)
                }
            }
        }
    }
    fun endWhatsAppCall() {
        finishActivity(REQUEST_CODE_FOR_CALL)
//        val disconnectIntent = Intent()
//        disconnectIntent.action = "com.whatsapp.action.CALL_DISCONNECTED"
//        sendBroadcast(disconnectIntent)

    }

    fun addPhoneNumberToContact(context: Context, contactName: String, phoneNumber: String): Boolean {
        val ops: ArrayList<ContentProviderOperation> = ArrayList()
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
            .build())

        // Define the display name insertion operation
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, contactName)
            .build())

        // Define the phone number insertion operation with WhatsApp MIME type
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            .withValue(CommonDataKinds.Phone.NUMBER, phoneNumber)
            .withValue(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_MOBILE)
            .build())

        // Apply the operations to the content provider
        val results: Array<ContentProviderResult>
        try {
            results = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

        return true
    }
    @SuppressLint("Range")
    private fun syncWhatsappContacts() {
        // Pass the settings flags by inserting them in a bundle
        val settingsBundle = Bundle().apply {
            putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        }
        /*
         * Request the sync for the default account, authority, and
         * manual sync settings
         */
        ContentResolver.requestSync(mAccount, AUTHORITY, settingsBundle)
    }
}