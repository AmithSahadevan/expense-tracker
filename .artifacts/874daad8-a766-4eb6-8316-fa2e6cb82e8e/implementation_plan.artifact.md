# Implementation Plan - Dark Mode UI Enhancements

Update the dark mode theme, colors, and components to match the requested aesthetic: new dark background color, high-contrast "Available Money" card, inverted bottom dock, and fixed profile picture visibility.

## User Review Required

> [!NOTE]
> The default avatar color for users will be changed from `#242426` to `#0C0F14` to match the new dark grey. For visibility in dark mode, the UI will fall back to `OffWhiteBg` if the user's avatar color is too similar to the background.

## Proposed Changes

### Theme & Colors

#### [MODIFY] [Color.kt](file:///C:/Users/Lenovo/Documents/expense-tracker/app/src/main/java/com/example/ui/theme/Color.kt)
- Update `DarkGrey` from `0xFF242426` to `0xFF0C0F14`.

#### [MODIFY] [Theme.kt](file:///C:/Users/Lenovo/Documents/expense-tracker/app/src/main/java/com/example/ui/theme/Theme.kt)
- Update `DarkColorScheme`:
    - Set `primary` to `OffWhiteBg`.
    - Set `onPrimary` to `DarkGrey`.
    - Set `primaryContainer` to `DarkGrey`.
    - Set `onPrimaryContainer` to `OffWhiteBg`.
    - Adjust `surface` and `surfaceVariant` for better contrast with the new background.

---

### Data Models & Repositories

#### [MODIFY] [Entities.kt](file:///C:/Users/Lenovo/Documents/expense-tracker/app/src/main/java/com/example/data/local/entities/Entities.kt)
- Update default `avatarColorHex` from `#242426` to `#0C0F14`.

#### [MODIFY] [AuthRepository.kt](file:///C:/Users/Lenovo/Documents/expense-tracker/app/src/main/java/com/example/data/repository/AuthRepository.kt)
- Update default `avatarColorHex` from `#242426` to `#0C0F14`.

---

### UI Components

#### [MODIFY] [UserAuthModal.kt](file:///C:/Users/Lenovo/Documents/expense-tracker/app/src/main/java/com/example/ui/components/UserAuthModal.kt)
- Update the color selection list to replace `#242426` with `#0C0F14`.

#### [MODIFY] [PlayfulTopBar.kt](file:///C:/Users/Lenovo/Documents/expense-tracker/app/src/main/java/com/example/ui/components/PlayfulTopBar.kt)
- Fix profile picture visibility by ensuring the icon color is visible against the background, even if the user's color is dark.
- Update hardcoded fallback color.

#### [MODIFY] [SettingsScreen.kt](file:///C:/Users/Lenovo/Documents/expense-tracker/app/src/main/java/com/example/ui/screens/SettingsScreen.kt)
- Similar visibility fix for the profile picture in the settings header.

---

## Verification Plan

### Manual Verification
- Deploy the app and toggle system Dark Mode.
- Verify the background is the new dark grey (`0C0F14`).
- Verify the "Available Money" card is Off-White (`FEF8E8`).
- Verify the bottom dock is Off-White and the moving bubble is Dark Grey.
- Verify all icons in the dock are correctly tinted (Dark Grey when unselected, Off-White when selected inside the bubble).
- Verify the profile picture in the TopBar and Settings is visible in both light and dark modes.
