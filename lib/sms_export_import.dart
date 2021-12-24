import 'dart:async';
import 'package:flutter/services.dart';

class SmsExportImport {
  static const MethodChannel _channel = MethodChannel('sms_export_import');
  static const EventChannel _stream = EventChannel('sms_event_channel_data');

  ///For Sms export
  static Future<Map<dynamic, dynamic>?> export(
      {required Function totalMessageStream}) async {
    ///Set listener for get total sms and mms
    _stream.receiveBroadcastStream().listen((onData) {
      print("LISTEN::$onData");
      totalMessageStream(onData);
    });

    final result = await _channel.invokeMethod<Map<dynamic, dynamic>>('export');
    print('Flutter ---> export --> result -> $result');
    return result;
  }

  ///For Sms import
  static Future<Map<dynamic, dynamic>?> import(
      {required Function totalMessageStream}) async {
    ///Set listener for get total sms and mms
    _stream.receiveBroadcastStream().listen((onData) {
      print("LISTEN::$onData");
      totalMessageStream(onData);
    });

    final result = await _channel.invokeMethod<Map<dynamic, dynamic>>('import');
    print('Flutter ---> import --> result -> $result');
    return result;
  }
}
