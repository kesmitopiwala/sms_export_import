package com.example.sms_export_import.sms_export_import

import android.app.Activity
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Base64
import android.util.JsonReader
import android.util.JsonWriter
import android.util.Log
import androidx.annotation.NonNull
import androidx.preference.PreferenceManager
import io.flutter.BuildConfig
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.*
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.StreamHandler


/** SmsExportImportPlugin */
class SmsExportImportPlugin: FlutterPlugin, MethodCallHandler, ActivityAware ,PluginRegistry.ActivityResultListener{
  private lateinit var channel : MethodChannel
  val EXPORT = 1
  val IMPORT = 2
  val DEFAULT_SMS_REQUEST = 3;
  val LOG_TAG = "DEBUG"
  val PDU_HEADERS_FROM = "137"
  private lateinit var prefs: SharedPreferences
  lateinit var context: Context
  lateinit var activity: Activity
  var flutterPluginBinding: FlutterPluginBinding? = null
  lateinit var activityPluginBinding: ActivityPluginBinding
  val handler: Handler = Handler(Looper.getMainLooper())
  var total = MessageTotal(0,0)
  private lateinit var eventChannel: EventChannel
  private lateinit var eventSink: EventChannel.EventSink
  var statusExporting = "None"
  var statusImporting = "None"

  data class MessageTotal(var sms: Int = 0, var mms: Int = 0)

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext
    this.flutterPluginBinding = flutterPluginBinding
    prefs = PreferenceManager.getDefaultSharedPreferences(context)
  }

  private fun exportFile() {
      val date = getCurrentDateTime()
      val dateInString = date.toString("dd MMM yyyy hh:mm:ss Z")
      val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/json"
        putExtra(Intent.EXTRA_TITLE, "messages-$dateInString.json")
      }
      activity.startActivityForResult(intent, EXPORT)
  }

  private fun importFile() {
    Log.e("importFile", "importFile: Telephony.Sms.getDefaultSmsPackage(activity) ---> ${Telephony.Sms.getDefaultSmsPackage(activity)}")
    Log.e("importFile", "activity.packageName ---> ${activity.packageName}")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val roleManager = activity.getSystemService(RoleManager::class.java)
      if(roleManager.isRoleAvailable(RoleManager.ROLE_SMS)){
        importMessages()
        Log.e("importFile", "importFile: roleManager.isRoleAvailable(RoleManager.ROLE_SMS) ---> rolemanager is available import messages")
      }else{
        val roleManager = activity.getSystemService(RoleManager::class.java)
        val roleRequestIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
        activity.startActivityForResult(roleRequestIntent, DEFAULT_SMS_REQUEST)
        Log.e("importFile", "importFile: roleManager.isRoleAvailable(RoleManager.ROLE_SMS) ---> not match and get permission")
      }
    }else{
      if (!Telephony.Sms.getDefaultSmsPackage(activity).equals(activity.packageName)) {
        val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
        intent.putExtra(
          Telephony.Sms.Intents.EXTRA_PACKAGE_NAME,
          activity.packageName
        )
        activity.startActivityForResult(intent, DEFAULT_SMS_REQUEST)
        Log.e("importFile", "importFile: Telephony.Sms.getDefaultSmsPackage(activity) ---> telephone package is not match")
      }else{
        importMessages()
        Log.e("importFile", "importFile: import messages")
      }
    }

  }

  private fun importMessages(){
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
      addCategory(Intent.CATEGORY_OPENABLE)
      type =
        if (Build.VERSION.SDK_INT < 29) "*/*" else "application/json" //see https://github.com/tmo1/sms-ie/issues/3#issuecomment-900518890
    }
    activity.startActivityForResult(intent, IMPORT)
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?): Boolean {
    if(requestCode == DEFAULT_SMS_REQUEST && resultCode == Activity.RESULT_OK){
      Log.e("Call Export", "onActivityResult: sms import  --> $total")
      importMessages()
    }
    if (requestCode == EXPORT
      && resultCode == Activity.RESULT_OK
    ) {
      resultData?.data?.let {
        CoroutineScope(Dispatchers.Main).launch {
          total = exportJSON(it)
          activity.runOnUiThread {
            statusExporting = "${total.sms} SMS(s) and ${total.mms} MMS(s) exported."
            eventSink.success(hashMapOf("totalSms" to total.sms,
            "totalMms" to total.mms,
            "status" to statusExporting
          ).toMap()) }
          Log.e("Call Export", "onActivityResult: total --> $total")
        }
      }
    }
    if (requestCode == IMPORT
      && resultCode == Activity.RESULT_OK
    ) {
      resultData?.data?.let {
        CoroutineScope(Dispatchers.Main).launch {
          total = importJson(it)
          activity.runOnUiThread {
            statusImporting = "${total.sms} SMS(s) and ${total.mms} MMS(s) imported."
            eventSink.success(hashMapOf("totalSms" to total.sms,
            "totalMms" to total.mms,
              "status" to statusImporting
          ).toMap()) }
        }
      }
    }
    return true
  }

  private suspend fun exportJSON(file: Uri): MessageTotal {
    return withContext(Dispatchers.IO) {
      val totals = MessageTotal()
      val displayNames = mutableMapOf<String, String?>()
      activity.contentResolver.openOutputStream(file).use { outputStream ->
        BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
          val jsonWriter = JsonWriter(writer)
          jsonWriter.setIndent("  ")
          jsonWriter.beginArray()
          if (prefs.getBoolean("sms", true)) totals.sms = smsToJSON(jsonWriter, displayNames)
          if (prefs.getBoolean("mms", true)) totals.mms = mmsToJSON(jsonWriter, displayNames)
          jsonWriter.endArray()
        }
      }
      totals
    }
  }

  private fun smsToJSON(
    jsonWriter: JsonWriter,
    displayNames: MutableMap<String, String?>
  ): Int {
    var total = 0
    val smsCursor =
      activity.contentResolver.query(Telephony.Sms.CONTENT_URI, null, null, null, null)
    smsCursor?.use { it ->
      if (it.moveToFirst()) {
        val addressIndex = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
        do {
          jsonWriter.beginObject()
          it.columnNames.forEachIndexed { i, columnName ->
            val value = it.getString(i)
            if (value != null) jsonWriter.name(columnName).value(value)
          }
          val displayName =
            lookupDisplayName(displayNames, it.getString(addressIndex))
          if (displayName != null) jsonWriter.name("display_name").value(displayName)
          jsonWriter.endObject()
          total++
          if (BuildConfig.DEBUG && total == prefs.getString("max_messages", "")?.toIntOrNull() ?: -1) break
        } while (it.moveToNext())
      }
    }
    return total
  }

  private fun mmsToJSON(
    jsonWriter: JsonWriter,
    displayNames: MutableMap<String, String?>
  ): Int {
    var total = 0
    val mmsCursor =
      activity.contentResolver.query(Telephony.Mms.CONTENT_URI, null, null, null, null)
    mmsCursor?.use { it ->
      if (it.moveToFirst()) {
        val msgIdIndex = it.getColumnIndexOrThrow("_id")
        // write MMS metadata
        do {
          jsonWriter.beginObject()
          it.columnNames.forEachIndexed { i, columnName ->
            val value = it.getString(i)
            if (value != null) jsonWriter.name(columnName).value(value)
          }
          val msgId = it.getString(msgIdIndex)
          val addressCursor = activity.contentResolver.query(
            Uri.parse("content://mms/$msgId/addr"),
            null,
            null,
            null,
            null
          )
          addressCursor?.use { it1 ->
            val addressTypeIndex =
              addressCursor.getColumnIndexOrThrow(Telephony.Mms.Addr.TYPE)
            val addressIndex =
              addressCursor.getColumnIndexOrThrow(Telephony.Mms.Addr.ADDRESS)
            // write sender address object
            if (it1.moveToFirst()) {
              do {
                if (addressTypeIndex.let { it2 -> it1.getString(it2) } == PDU_HEADERS_FROM) {
                  jsonWriter.name("sender_address")
                  jsonWriter.beginObject()
                  it1.columnNames.forEachIndexed { i, columnName ->
                    val value = it1.getString(i)
                    if (value != null) jsonWriter.name(columnName).value(value)
                  }
                  val displayName =
                    lookupDisplayName(displayNames, it1.getString(addressIndex))
                  if (displayName != null) jsonWriter.name("display_name")
                    .value(displayName)
                  jsonWriter.endObject()
                  break
                }
              } while (it1.moveToNext())
            }
            // write array of recipient address objects
            if (it1.moveToFirst()) {
              jsonWriter.name("recipient_addresses")
              jsonWriter.beginArray()
              do {
                if (addressTypeIndex.let { it2 -> it1.getString(it2) } != PDU_HEADERS_FROM) {
                  jsonWriter.beginObject()
                  it1.columnNames.forEachIndexed { i, columnName ->
                    val value = it1.getString(i)
                    if (value != null) jsonWriter.name(columnName).value(value)
                  }
                  val displayName =
                    lookupDisplayName(displayNames, it1.getString(addressIndex))
                  if (displayName != null) jsonWriter.name("display_name")
                    .value(displayName)
                  jsonWriter.endObject()
                }
              } while (it1.moveToNext())
              jsonWriter.endArray()
            }
          }
          val partCursor = activity.contentResolver.query(
            Uri.parse("content://mms/part"),
            null,
            "mid=?",
            arrayOf(msgId),
            "seq ASC"
          )
          // write array of MMS parts
          partCursor?.use { it1 ->
            if (it1.moveToFirst()) {
              jsonWriter.name("parts")
              jsonWriter.beginArray()
              val partIdIndex = it1.getColumnIndexOrThrow("_id")
              val dataIndex = it1.getColumnIndexOrThrow("_data")
              do {
                jsonWriter.beginObject()
                it1.columnNames.forEachIndexed { i, columnName ->
                  val value = it1.getString(i)
                  if (value != null) jsonWriter.name(columnName).value(value)
                }

                if (prefs.getBoolean("include_binary_data", true) && it1.getString(dataIndex) != null) {
                  val inputStream = activity.contentResolver.openInputStream(
                    Uri.parse(
                      "content://mms/part/" + it1.getString(
                        partIdIndex
                      )
                    )
                  )
                  val data = inputStream.use {
                    Base64.encodeToString(
                      it?.readBytes(),
                      Base64.NO_WRAP
                    )
                  }
                  jsonWriter.name("binary_data").value(data)
                }
                jsonWriter.endObject()
              } while (it1.moveToNext())
              jsonWriter.endArray()
            }
          }
          jsonWriter.endObject()
          total++
          if (BuildConfig.DEBUG && total == prefs.getString("max_messages", "")?.toIntOrNull() ?: -1) break
        } while (it.moveToNext())
      }
    }
    return total
  }

  private fun lookupDisplayName(
    displayNames: MutableMap<String, String?>,
    address: String
  ): String? {
//        look up display name by phone number
    if (address == "") return null
    if (displayNames[address] != null) return displayNames[address]
    val displayName: String?
    val uri = Uri.withAppendedPath(
      ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
      Uri.encode(address)
    )
    val nameCursor = activity.contentResolver.query(
      uri,
      arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
      null,
      null,
      null
    )
    nameCursor.use {
      displayName = if (it != null && it.moveToFirst())
        it.getString(
          it.getColumnIndexOrThrow(
            ContactsContract.PhoneLookup.DISPLAY_NAME
          )
        )
      else null
    }
    displayNames[address] = displayName
    return displayName
  }

  private suspend fun importJson(uri: Uri): MessageTotal {
    return withContext(Dispatchers.IO) {
      val totals = MessageTotal()
      uri.let {
        activity.contentResolver.openInputStream(it).use { inputStream ->
          BufferedReader(InputStreamReader(inputStream)).use { reader ->
            val jsonReader = JsonReader(reader)
            val messageMetadata = ContentValues()
            val addresses = mutableSetOf<ContentValues>()
            val parts = mutableListOf<ContentValues>()
            val binaryData = mutableListOf<ByteArray?>()
            jsonReader.beginArray()
            while (jsonReader.hasNext()) {
              jsonReader.beginObject()
              messageMetadata.clear()
              addresses.clear()
              parts.clear()
              binaryData.clear()
              var name: String?
              var value: String?
              while (jsonReader.hasNext()) {
                name = jsonReader.nextName()
                when (name) {
                  "sender_address" -> {
                    jsonReader.beginObject()
                    val address = ContentValues()
                    while (jsonReader.hasNext()) {
                      val name1 = jsonReader.nextName()
                      val value1 = jsonReader.nextString()
                      if (name1 !in setOf(
                          Telephony.Mms.Addr._ID,
                          Telephony.Mms.Addr._COUNT,
                          Telephony.Mms.Addr.MSG_ID,
                          "display_name"
                        )
                      ) address.put(name1, value1)
                    }
                    addresses.add(address)
                    jsonReader.endObject()
                  }
                  "recipient_addresses" -> {
                    jsonReader.beginArray()
                    while (jsonReader.hasNext()) {
                      jsonReader.beginObject()
                      val address = ContentValues()
                      while (jsonReader.hasNext()) {
                        val name1 = jsonReader.nextName()
                        val value1 = jsonReader.nextString()
                        if (name1 !in setOf(
                            Telephony.Mms.Addr._ID,
                            Telephony.Mms.Addr._COUNT,
                            Telephony.Mms.Addr.MSG_ID,
                            "display_name"
                          )
                        ) address.put(name1, value1)
                      }
                      addresses.add(address)
                      jsonReader.endObject()
                    }
                    jsonReader.endArray()
                  }
                  "parts" -> {
                    jsonReader.beginArray()
                    while (jsonReader.hasNext()) {
                      jsonReader.beginObject()
                      val part = ContentValues()
                      var hasBinaryData = false
                      while (jsonReader.hasNext()) {
                        val name1 = jsonReader.nextName()
                        val value1 = jsonReader.nextString()
                        if (name1 !in setOf(
                            Telephony.Mms.Part.MSG_ID,
                            Telephony.Mms.Part._ID,
                            Telephony.Mms.Part._DATA,
                            Telephony.Mms.Part._COUNT,
                            "binary_data"
                          )
                        ) part.put(name1, value1)
                        if (name1 == "binary_data") {
                          binaryData.add(
                            Base64.decode(
                              value1,
                              Base64.NO_WRAP
                            )
                          )
                          hasBinaryData = true
                        }
                      }
                      if (!hasBinaryData) binaryData.add(null)
                      parts.add(part)
                      jsonReader.endObject()
                    }
                    jsonReader.endArray()
                  }
                  else -> {
                    value = jsonReader.nextString()
                    if (name !in setOf(
                        "_id",
                        "thread_id",
                        "display_name"
                      )
                    ) messageMetadata.put(name, value)
                  }
                }
              }
              jsonReader.endObject()
              if (!messageMetadata.containsKey("m_type")) { // it's SMS
                if (!prefs.getBoolean("sms", true) || totals.sms == prefs.getString("max_messages", "")?.toIntOrNull() ?: -1) continue
                val insertUri =
                  activity.contentResolver.insert(
                    Telephony.Sms.CONTENT_URI,
                    messageMetadata
                  )
                if (insertUri == null) {
                  Log.v(LOG_TAG, "SMS insert failed!")
                } else totals.sms++
              } else { // it's MMS
                if (!prefs.getBoolean("mms", true) || totals.mms == prefs.getString("max_messages", "")?.toIntOrNull() ?: -1) continue
                val threadId = Telephony.Threads.getOrCreateThreadId(
                  activity,
                  addresses.map { it1 -> it1.getAsString("address") }.toSet()
                )
                messageMetadata.put("thread_id", threadId)
                val insertUri =
                  activity.contentResolver.insert(
                    Telephony.Mms.CONTENT_URI,
                    messageMetadata
                  )
                if (insertUri == null) {
                  Log.v(LOG_TAG, "MMS insert failed!")
                } else {
                  totals.mms++
                  val messageId = insertUri.lastPathSegment
                  val addressUri = Uri.parse("content://mms/$messageId/addr")
                  addresses.forEach { address1 ->
                    address1.put(Telephony.Mms.Addr.MSG_ID, messageId)
                    val insertAddressUri =
                      activity.contentResolver.insert(addressUri, address1)
                    if (insertAddressUri == null) {
                      Log.v(LOG_TAG, "MMS address insert failed!")
                    }
                  }
                  val partUri = Uri.parse("content://mms/$messageId/part")
                  parts.forEachIndexed { j, part1 ->
                    part1.put(Telephony.Mms.Part.MSG_ID, messageId)
                    val insertPartUri =
                      activity.contentResolver.insert(partUri, part1)
                    if (insertPartUri == null) {
                      Log.v(
                        LOG_TAG,
                        "MMS part insert failed! Part metadata:$part1"
                      )
                    } else {
                      if (binaryData[j] != null) {
                        val os =
                          activity.contentResolver.openOutputStream(insertPartUri)
                        if (os != null) os.use { os.write(binaryData[j]) }
                        else Log.v(LOG_TAG, "Failed to open OutputStream!")
                      }
                    }
                  }
                }
              }
            }
            jsonReader.endArray()
          }
        }
        totals
      }
    }
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    when(call.method){
      "export" -> {
        statusExporting = "Exporting messages..."
        eventSink.success(hashMapOf("totalSms" to total.sms,
          "totalMms" to total.mms,
          "status" to statusExporting
        ).toMap())
        exportFile()
        Log.e("Call Export", "onMethodCall: Call exportFile")
        Log.e("Call Export", "onMethodCall: exportFile total --> $total")
        handler.post {
          result.success(hashMapOf("totalSms" to total.sms,
            "totalMms" to total.mms,
            "status" to statusExporting
            ).toMap())
          Log.e("Call Export", "onMethodCall: total exportFile inside handler--> $total")
        }
      }
      "import" -> {
        statusImporting = "Importing messages..."
        eventSink.success(hashMapOf("totalSms" to total.sms,
          "totalMms" to total.mms,
          "status" to statusImporting
        ).toMap())
        importFile()
        Log.e("Call Export", "onMethodCall: Call importFile")
        Log.e("Call Export", "onMethodCall: importFile total --> $total")
        handler.post {
          result.success(hashMapOf("totalSms" to total.sms,
            "totalMms" to total.mms,
            "status" to statusImporting
          ))
          Log.e("Call Export", "onMethodCall: total importFile inside handler--> $total")
        }
      }
      else -> result.notImplemented()
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPluginBinding) {
    this.flutterPluginBinding = null
    channel.setMethodCallHandler(null)
    total = MessageTotal(0,0)
    statusExporting = "None"
    statusImporting = "None"
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
    this.activityPluginBinding = binding
    setupChannels(flutterPluginBinding!!.binaryMessenger, binding.activity)
  }

  private fun setupChannels(messenger: BinaryMessenger, activity:Activity) {
    if (activityPluginBinding != null) {
      activityPluginBinding.addActivityResultListener(this)
      channel = MethodChannel(messenger, "sms_export_import")
      channel.setMethodCallHandler(this)
      eventChannel = EventChannel(flutterPluginBinding!!.binaryMessenger, "sms_event_channel_data")
      setStreamHandlerListener()
    }
  }

  private fun setStreamHandlerListener() {
    eventChannel.setStreamHandler(
      object : StreamHandler(), EventChannel.StreamHandler {
        override fun onListen(p0: Any?, sink: EventChannel.EventSink) {
          eventSink = sink
        }

        override fun onCancel(p0: Any?) {
        }
      }
    )
  }

  override fun onDetachedFromActivityForConfigChanges() {
    TODO("Not yet implemented")
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    TODO("Not yet implemented")
  }

  override fun onDetachedFromActivity() {
    TODO("Not yet implemented")
  }
}

fun Date.toString(format: String, locale: Locale = Locale.getDefault()): String {
  val formatter = SimpleDateFormat(format, locale)
  return formatter.format(this)
}

fun getCurrentDateTime(): Date {
  return Calendar.getInstance().time
}
