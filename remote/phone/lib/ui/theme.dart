import 'package:flutter/material.dart';

/// Tokens. Same navy/blue family as the projector launcher so both feel like one product.
class Tokens {
  static const bg = Color(0xFF0B1020);
  static const surface = Color(0xFF161B2E);
  static const raised = Color(0xFF1E2438);
  static const ink = Color(0xFFF2F4FA);
  static const muted = Color(0xFF8E97B3);
  static const accent = Color(0xFF5B8CFF);
  static const accentPressed = Color(0xFF2D6CF6);
  static const warn = Color(0xFFFFB454);
}

ThemeData buildTheme() {
  final base = ThemeData(brightness: Brightness.dark, useMaterial3: true);
  return base.copyWith(
    scaffoldBackgroundColor: Tokens.bg,
    colorScheme: const ColorScheme.dark(
      primary: Tokens.accent,
      surface: Tokens.surface,
      onSurface: Tokens.ink,
      secondary: Tokens.accent,
    ),
    textTheme: base.textTheme.apply(bodyColor: Tokens.ink, displayColor: Tokens.ink),
    appBarTheme: const AppBarTheme(backgroundColor: Tokens.bg, foregroundColor: Tokens.ink, elevation: 0),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: Tokens.surface,
      indicatorColor: Tokens.accent.withValues(alpha: 0.18),
      labelTextStyle: WidgetStateProperty.all(const TextStyle(color: Tokens.muted, fontSize: 12)),
      iconTheme: WidgetStateProperty.resolveWith(
        (s) => IconThemeData(color: s.contains(WidgetState.selected) ? Tokens.accent : Tokens.muted),
      ),
    ),
    snackBarTheme: const SnackBarThemeData(backgroundColor: Tokens.raised, contentTextStyle: TextStyle(color: Tokens.ink)),
  );
}
