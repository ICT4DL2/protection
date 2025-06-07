import 'package:flutter/material.dart';
import 'package:local_auth/local_auth.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:camera/camera.dart';
import 'package:geolocator/geolocator.dart';
import 'package:mailer/mailer.dart';
import 'package:mailer/smtp_server.dart';
import 'dart:io';

class AuthenticationPage extends StatefulWidget {
  @override
  _AuthenticationPageState createState() => _AuthenticationPageState();
}

class _AuthenticationPageState extends State<AuthenticationPage> {
  final TextEditingController _pinController = TextEditingController();
  int _failedAttempts = 0;
  final String correctPin = "1234"; // À modifier selon vos besoins
  final LocalAuthentication _localAuth = LocalAuthentication();
  bool _isBiometricAvailable = false;

  @override
  void initState() {
    super.initState();
    _checkBiometrics();
    _checkPermissions();
  }

  Future<void> _checkBiometrics() async {
    try {
      final bool canAuthenticateWithBiometrics = await _localAuth.canCheckBiometrics;
      final bool canAuthenticate = canAuthenticateWithBiometrics || await _localAuth.isDeviceSupported();
      setState(() {
        _isBiometricAvailable = canAuthenticate;
      });
    } catch (e) {
      print('Erreur lors de la vérification des biométries: $e');
    }
  }

  Future<void> _checkPermissions() async {
    await Geolocator.requestPermission();
    final cameras = await availableCameras();
  }

  Future<void> _authenticateWithBiometrics() async {
    try {
      final bool didAuthenticate = await _localAuth.authenticate(
        localizedReason: 'Veuillez vous authentifier pour accéder à l\'application',
        options: const AuthenticationOptions(
          stickyAuth: true,
          biometricOnly: true,
        ),
      );

      if (didAuthenticate) {
        setState(() => _failedAttempts = 0);
        Navigator.pushReplacementNamed(context, '/home');
      } else {
        await _handleFailedAttempt();
      }
    } catch (e) {
      print('Erreur lors de l\'authentification biométrique: $e');
      await _handleFailedAttempt();
    }
  }

  Future<void> _handleFailedAttempt() async {
    setState(() {
      _failedAttempts++;
    });

    if (_failedAttempts == 1) {
      // Prendre une photo
      final cameras = await availableCameras();
      final frontCamera = cameras.firstWhere(
        (camera) => camera.lensDirection == CameraLensDirection.front,
      );

      final controller = CameraController(
        frontCamera,
        ResolutionPreset.medium,
        enableAudio: false,
      );

      await controller.initialize();
      final image = await controller.takePicture();

      // Obtenir la localisation
      final position = await Geolocator.getCurrentPosition();

      // Envoyer l'email
      await _sendEmail(
        imagePath: image.path,
        location: '${position.latitude}, ${position.longitude}',
      );

      await controller.dispose();
    }
  }

  Future<void> _sendEmail({
    required String imagePath,
    required String location,
  }) async {
    final smtpServer = gmail('roosevelt.tbr@gmail.com', 'tbr2005tbR@');

    final message = Message()
      ..from = Address('roosevelt.tbr@gmail.com')
      ..recipients.add('roosevelt.tbr@gmail.com')
      ..subject = 'Tentative de déverrouillage échouée'
      ..text = 'Localisation: $location'
      ..attachments = [
        FileAttachment(File(imagePath))
      ];

    try {
      await send(message, smtpServer);
    } catch (e) {
      print('Erreur lors de l\'envoi de l\'email: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Authentification'),
      ),
      body: Padding(
        padding: EdgeInsets.all(16.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            if (_isBiometricAvailable)
              ElevatedButton.icon(
                onPressed: _authenticateWithBiometrics,
                icon: Icon(Icons.fingerprint),
                label: Text('Authentification biométrique'),
                style: ElevatedButton.styleFrom(
                  padding: EdgeInsets.symmetric(horizontal: 32, vertical: 16),
                ),
              ),
            SizedBox(height: 16),
            TextField(
              controller: _pinController,
              keyboardType: TextInputType.number,
              obscureText: true,
              maxLength: 4,
              decoration: InputDecoration(
                labelText: 'Entrez votre code PIN',
                border: OutlineInputBorder(),
              ),
            ),
            SizedBox(height: 16),
            ElevatedButton(
              onPressed: () async {
                if (_pinController.text == correctPin) {
                  setState(() => _failedAttempts = 0);
                  Navigator.pushReplacementNamed(context, '/home');
                } else {
                  await _handleFailedAttempt();
                  _pinController.clear();
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text('Code PIN incorrect')),
                  );
                }
              },
              child: Text('Valider'),
            ),
          ],
        ),
      ),
    );
  }
} 