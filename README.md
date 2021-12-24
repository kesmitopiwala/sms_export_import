# sms_export_import

This package is used for import and export messages in your app.

<br>

# SMS Import and Export Flutter
<br>

<br>

| SMS & MMS export                                                                             |                                                                |
| -------------------------------------------------------------------------------------------- | ---------------------------------------------------------------|
| ![](https://github.com/kesmitopiwala/sms_export_import/blob/master/assets/ss1.png) | ![](https://github.com/kesmitopiwala/sms_export_import/blob/master/assets/ss4.png) |
| ![](https://github.com/kesmitopiwala/sms_export_import/blob/master/assets/ss5.png) | ![](https://github.com/kesmitopiwala/sms_export_import/blob/master/assets/ss6.png) |


<br>

| SMS & MMS import                                                                             |                                                                 |
| -------------------------------------------------------------------------------------------- | ----------------------------------------------------------------|
| ![](https://github.com/kesmitopiwala/sms_export_import/blob/master/assets/ss7.png) | ![](https://github.com/kesmitopiwala/sms_export_import/blob/master/assets/ss8.png) |
| ![](https://github.com/kesmitopiwala/sms_export_import/blob/master/assets/ss9.png) |                                                                                    |                                                                              

<br>


A flutter package sms export import which will help to export and import SMS and MMS in your app.

## Features 💚

- You can Export and Import the SMS in your app.
- You can Export and Import the MMS in your app.

## Installation

First, add `sms_export_import` as a [dependency in your pubspec.yaml file](https://flutter.dev/using-packages/).

##  Android

Add below permission in your AndroidManifest.xml file ,also add thest receivers and activity for by default make your app SMS app.

```
    <uses-permission android:name="android.permission.READ_SMS" />
    <uses-permission android:name="android.permission.WRITE_SMS"/>
    <uses-permission android:name="android.permission.READ_CONTACTS" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    
     <!-- BroadcastReceiver that listens for incoming SMS messages -->
       <receiver
           android:name=".SmsReceiver"
           android:permission="android.permission.BROADCAST_SMS"
           android:exported="true">
           <intent-filter>
               <action android:name="android.provider.Telephony.SMS_DELIVER" />
           </intent-filter>
       </receiver>

       <!-- BroadcastReceiver that listens for incoming MMS messages -->
       <receiver
           android:name=".MmsReceiver"
           android:permission="android.permission.BROADCAST_WAP_PUSH"
           android:exported="true">
           <intent-filter>
               <action android:name="android.provider.Telephony.WAP_PUSH_DELIVER" />
               <data android:mimeType="application/vnd.wap.mms-message" />
           </intent-filter>
       </receiver>

       <!-- Activity that allows the user to send new SMS/MMS messages -->
       <activity android:name=".ComposeSmsActivity"
           android:exported="true">
           <intent-filter>
               <action android:name="android.intent.action.SEND" />
               <action android:name="android.intent.action.SENDTO" />
               <category android:name="android.intent.category.DEFAULT" />
               <category android:name="android.intent.category.BROWSABLE" />
               <data android:scheme="sms" />
               <data android:scheme="smsto" />
               <data android:scheme="mms" />
               <data android:scheme="mmsto" />
           </intent-filter>
       </activity>

       <!-- Service that delivers messages from the phone "quick response" -->
       <service
           android:name=".HeadlessSmsSendService"
           android:exported="true"
           android:permission="android.permission.SEND_RESPOND_VIA_MESSAGE">
           <intent-filter>
               <action android:name="android.intent.action.RESPOND_VIA_MESSAGE" />
               <category android:name="android.intent.category.DEFAULT" />
               <data android:scheme="sms" />
               <data android:scheme="smsto" />
               <data android:scheme="mms" />
               <data android:scheme="mmsto" />
           </intent-filter>
       </service>
```

## How to use

- SMS & MMS export : Call this method on your onTap for sms and mms export.
```Dart
SmsExportImport.export(totalMessageStream: (data) {
      print('export sink ---> $data');
      setState(() {totalSMS = data['totalSms'];
      totalMMS = data['totalMms'];
      exportSmsStatus = data['status'];
      clickOnImport = 'export';
      });
}).then((value) {
  print(
  'Flutter --> MessageTotal ---> export messageTotal --> $value');
  setState(() {
    totalSMS = value!['totalSms'];
    totalMMS = value['totalMms'];
    exportSmsStatus = value['status'];
    clickOnImport = 'export';
});
});
```

- SMS & MMS Import : Call this method on your onTap for sms and mms import.
```Dart
SmsExportImport.import(totalMessageStream: (data) {
  print('import sink ---> $data');
  setState(() {
    totalSMS = data['totalSms'];
    totalMMS = data['totalMms'];
    importSmsStatus = data['status'];
    clickOnImport = 'import';
  });
}).then((value) {
  print(
  'Flutter --> MessageTotal ---> import messageTotal --> $value');
  setState(() {
    totalSMS = value!['totalSms'];
    totalMMS = value['totalMms'];
    importSmsStatus = value['status'];
    clickOnImport = 'import';
});
});
```

Run the example app in the exmaple folder to find out more about how to use it.


