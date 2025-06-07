import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'authentication.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]);
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Protection',
      theme: ThemeData(
        primarySwatch: Colors.blue,
        visualDensity: VisualDensity.adaptivePlatformDensity,
      ),
      home: AuthenticationPage(),
      routes: {
        '/home': (context) => HomePage(),
      },
    );
  }
}

class HomePage extends StatefulWidget {
  @override
  _HomePageState createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  static const platform = MethodChannel('com.example.protection/security');
  bool _isServiceRunning = false;

  @override
  void initState() {
    super.initState();
    _checkServiceStatus();
  }

  Future<void> _checkServiceStatus() async {
    try {
      final bool isRunning = await platform.invokeMethod('startSecurityService');
      setState(() {
        _isServiceRunning = isRunning;
      });
    } on PlatformException catch (e) {
      print('Erreur lors de la vérification du service: ${e.message}');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Protection'),
        actions: [
          IconButton(
            icon: Icon(_isServiceRunning ? Icons.security : Icons.security_update_warning),
            onPressed: () {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text(_isServiceRunning 
                    ? 'Service de protection actif' 
                    : 'Service de protection inactif'),
                ),
              );
            },
          ),
        ],
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              _isServiceRunning ? Icons.security : Icons.security_update_warning,
              size: 100,
              color: _isServiceRunning ? Colors.green : Colors.red,
            ),
            SizedBox(height: 16),
            Text(
              _isServiceRunning 
                ? 'Protection active' 
                : 'Protection inactive',
              style: Theme.of(context).textTheme.headlineMedium,
            ),
            SizedBox(height: 8),
            Text(
              _isServiceRunning
                ? 'Votre appareil est protégé'
                : 'Le service de protection n\'est pas actif',
              style: Theme.of(context).textTheme.bodyLarge,
            ),
          ],
        ),
      ),
    );
  }
}
