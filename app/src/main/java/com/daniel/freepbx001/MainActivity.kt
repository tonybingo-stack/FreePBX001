package com.daniel.freepbx001

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
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
    private val PERMISSIONS_REQUEST_READ_CONTACTS = 100

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

        // Check the SDK version and whether the permission is already granted or not.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.WRITE_CONTACTS),
                PERMISSIONS_REQUEST_READ_CONTACTS
            )
            //After this point you wait for callback in onRequestPermissionsResult(int, String[], int[]) overriden method
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
                    Log.d("bingo", "here0")

//                    var telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
//                    var phoneStateListener: PhoneStateListener = object : PhoneStateListener() {
//                        override fun onCallStateChanged(state: Int, incomingNumber: String) {
//                            when (state) {
//                                TelephonyManager.CALL_STATE_IDLE -> {}
//                                TelephonyManager.CALL_STATE_RINGING -> {}
//                                TelephonyManager.CALL_STATE_OFFHOOK -> {}
//                            }
//                        }
//                    }
                    //register listener
//                    telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
                    Log.d("bingo", "here1")
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
//                    responseObj.put("emulatorCallState", "")
//                    responseObj.put("isWhatsappRegistered", false)
//                    responseObj.put("isAddedToContactList", false)
                    Log.d("bingo", "here2")
                    //check if phone number has whatsapp account
//                    if (isWhatsAppNumberRegistered(phone)) {
//                        responseObj.put("isWhatsappRegistered", true)
//                        Log.d("bingo", "here4")
//                        val callState = telephonyManager.callState
//                        if (callState == TelephonyManager.CALL_STATE_IDLE) {
//                            // initiate whatsapp call
//                            responseObj.put("emulatorCallState", "idle")
//                            Log.d("bingo", "here5")
//                        }
//                        else if (callState == TelephonyManager.CALL_STATE_RINGING) {
//                            // initiate whatsapp call
//                            responseObj.put("emulatorCallState", "ringing")
//                            Log.d("bingo", "here6")
//                        }
//                        else {
//                            // Device is busy
//                            responseObj.put("emulatorCallState", "busy")
//                            Log.d("bingo", "here7")
//                        }
//                    }
//                    else {
//                        // not registered number
//                        responseObj.put("isWhatsappRegistered", false)
//                        Log.d("bingo", "here3")
//                    }
                    val mimeString = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
                    // add contact list
                    if (addPhoneNumberToContact(this, name, phone)) {
                        responseObj.put("isAddedToContactList", true)

                        var _id : Long
                        val resolver = getApplicationContext().getContentResolver();
                        var cursor = resolver.query(
                            ContactsContract.Data.CONTENT_URI,
                            null,
                            null,
                            null,
                            ContactsContract.Contacts.DISPLAY_NAME
                        );
                        while (cursor!!.moveToNext()) {
                            _id = cursor.getLong(cursor.getColumnIndex(ContactsContract.Data._ID));
                            val displayName = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME));
                            val mimeType = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.MIMETYPE));

                            Log.d("Data", _id.toString() + " "+ displayName + " " + mimeType );

                            if (displayName.equals(name)) {
                                if (mimeType.equals(mimeString)) {
                                    Log.d("Data", "called")
                                    val data = "content://com.android.contacts/data/" + _id;
                                    val sendIntent = Intent();
                                    sendIntent.setAction(Intent.ACTION_VIEW);
                                    sendIntent.setDataAndType(Uri.parse(data), mimeString);
                                    sendIntent.setPackage("com.whatsapp");
                                    startActivity(sendIntent);
                                }
                            }
                        }
                    }
                    else {
                        responseObj.put("isAddedToContactList", false)
                    }

                    sendResponse(httpExchange, responseObj.toString())

                }
            }
        }
    }

    fun initiateWhatsAppCall(context: Context, phoneNumber: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("https://api.whatsapp.com/send?phone=$phoneNumber")
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val fallbackIntent = Intent(Intent.ACTION_VIEW)
            fallbackIntent.data = Uri.parse("http://www.whatsapp.com")
            context.startActivity(fallbackIntent)
        }
    }


    fun addPhoneNumberToContact(context: Context, contactName: String, phoneNumber: String): Boolean {
        // Define the raw contact insertion operation
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

        // Define the phone number insertion operation
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
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