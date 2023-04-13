package com.daniel.freepbx001

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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


class MainActivity : AppCompatActivity() {
    private var serverUp = false
    // Request code for READ_CONTACTS. It can be any number > 0.
    private val REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // get reference to button

        var btn_start = findViewById(R.id.startbtn) as Button
        var label = findViewById(R.id.number) as TextView
        label.text = "Server is offline"

        val port = 8000
        btn_start.setOnClickListener {
            serverUp = if(!serverUp){
                startServer(port)
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
            var telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            var phoneStateListener: PhoneStateListener = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, incomingNumber: String) {
                    when (state) {
                        TelephonyManager.CALL_STATE_IDLE -> {}
                        TelephonyManager.CALL_STATE_RINGING -> {}
                        TelephonyManager.CALL_STATE_OFFHOOK -> {}
                    }
                }
            }
            //register listener
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
            statusCall.text = telephonyManager.callState.toString()
        }
        // Check the SDK version and whether the permission is already granted or not.
        val permissions = arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE
        )

        requestPermissions(permissions, REQUEST_CODE)

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
//
//            //After this point you wait for callback in onRequestPermissionsResult(int, String[], int[]) overriden method
//        }
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
            mHttpServer!!.createContext("/check", messageHandler)
            mHttpServer!!.createContext("/call", callHandler)

            mHttpServer!!.start()//startServer server;
            var label = findViewById(R.id.number) as TextView
            label.text = "Server is online"
            var btn_start = findViewById(R.id.startbtn) as Button
            btn_start.text = "Stop Server"
//            serverTextView.text = getString(R.string.server_running)
//            serverButton.text = getString(R.string.stop_server)
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
                    Log.d("bingo", "here00")
                    val jsonObject = JSONObject()
                    val keyValuePairs = requestBody.split("&")
                    for (pair in keyValuePairs) {
                        val (key, value) = pair.split("=")
                        jsonObject.put(key, value)
                    }
                    Log.d("bingo", "here01")
                    val phone = jsonObject.getString("phoneNumber")

//                    var label = findViewById(R.id.number) as TextView
//                    label.text = phone
                    Log.d("bingo", phone)
                    initiateWhatsAppCall(this, phone)
                    sendResponse(httpExchange, "calling")
                }

            }
        }

    }
    @SuppressLint("Range")
    private val messageHandler = HttpHandler { httpExchange ->
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

                    Log.d("bingo", jsonObject.toString())
                    val responseObj = JSONObject()
                    // add contact list
                    if (addPhoneNumberToContact(this, name, phone)) {
                        responseObj.put("isAddedToContactList", true)
                    }
                    else {
                        responseObj.put("isAddedToContactList", false)
                    }

                    sendResponse(httpExchange, responseObj.toString())

                }
            }
        }
    }

    @SuppressLint("Range")
    fun initiateWhatsAppCall(context: Context, phoneNumber: String) {
//        val intent = Intent(Intent.ACTION_VIEW)
//        intent.data = Uri.parse("https://api.whatsapp.com/send?phone=$phoneNumber")
//        try {
//            context.startActivity(intent)
//        } catch (e: ActivityNotFoundException) {
//            val fallbackIntent = Intent(Intent.ACTION_VIEW)
//            fallbackIntent.data = Uri.parse("http://www.whatsapp.com")
//            context.startActivity(fallbackIntent)
//        }

//        val intent = Intent()
//        intent.action = Intent.ACTION_VIEW
//        intent.setDataAndType(
//            Uri.parse("content://com.android.contacts/data/9"),
//            "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
//        )
//        intent.setPackage("com.whatsapp")
//        startActivity(intent)

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
            }
        }
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
//            .withValue(ContactsContract.Data.MIMETYPE, "vnd.android.cursor.item/vnd.com.whatsapp.profile")
//            .withValue(ContactsContract.Data.MIMETYPE, "vnd.android.cursor.item/vnd.org.telegram.messenger.android.profile")
            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
//            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Phone.TYPE_MOBILE)
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
    fun isWhatsAppNumberRegistered(phoneNumber: String): Boolean {
//        val client = OkHttpClient().newBuilder()
//            .build()
//        val mediaType = "text/plain".toMediaTypeOrNull()
//        val body: RequestBody = create(mediaType, "")
//        val request: Request = Builder()
//            .url("https://phone.watverifyapi.live/is-whatsapp-no/get?api_key=key&phone=1238893")
//            .method("POST", body)
//            .build()
//        val response = client.newCall(request).execute()
        return true
    }

}