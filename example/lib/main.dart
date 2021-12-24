import 'package:device_info/device_info.dart';
import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:sms_export_import/sms_export_import.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  int totalSMS = 0;
  int totalMMS = 0;
  var sdkInt;
  String clickOnImport = '';
  var exportSmsStatus = "None";
  var importSmsStatus = "None";

  @override
  void initState() {
    super.initState();
    deviceInfo();
  }

  deviceInfo() async {
    var androidInfo = await DeviceInfoPlugin().androidInfo;
    sdkInt = androidInfo.version.sdkInt;
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Sms import & export'),
        ),
        body: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: <Widget>[
                InkWell(
                  onTap: () async {
                    print('Export SMS');
                    Map<Permission, PermissionStatus> statuses =
                        await [Permission.sms, Permission.contacts].request();
                    if (statuses[Permission.sms]!.isGranted &&
                        statuses[Permission.contacts]!.isGranted) {
                      SmsExportImport.export(totalMessageStream: (data) {
                        print('export sink ---> $data');
                        setState(() {
                          totalSMS = data['totalSms'];
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
                    } else {
                      print('Read SMS and read contacts permission required');
                    }
                  },
                  child: Container(
                      padding: const EdgeInsets.all(12.0),
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(12.0),
                        color: Colors.blue,
                      ),
                      child: const Text(
                        'Export',
                        style: TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.bold,
                            fontSize: 16),
                      )),
                ),
                InkWell(
                  onTap: () {
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
                    print('Import SMS');
                  },
                  child: Container(
                      padding: const EdgeInsets.all(12.0),
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(12.0),
                        color: Colors.blue,
                      ),
                      child: const Text(
                        'Import',
                        style: TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.bold,
                            fontSize: 16),
                      )),
                ),
              ],
            ),
            const SizedBox(
              height: 30,
            ),
            clickOnImport == 'import'
                ? Align(
                    alignment: Alignment.center,
                    child: Container(
                      margin: const EdgeInsets.only(left: 12, right: 12),
                      child: Text(
                        importSmsStatus == "None" ? '' : importSmsStatus,
                        style: const TextStyle(
                            color: Colors.blue,
                            fontSize: 20,
                            fontWeight: FontWeight.bold),
                      ),
                    ),
                  )
                : Container(),
            clickOnImport == 'export'
                ? Align(
                    alignment: Alignment.center,
                    child: Container(
                      child: Text(
                        exportSmsStatus == "None" ? '' : exportSmsStatus,
                        style: const TextStyle(
                            color: Colors.blue,
                            fontSize: 20,
                            fontWeight: FontWeight.bold),
                      ),
                    ),
                  )
                : Container(),
          ],
        ),
      ),
    );
  }
}
