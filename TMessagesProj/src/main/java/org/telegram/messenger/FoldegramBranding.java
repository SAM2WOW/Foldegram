package org.telegram.messenger;

import java.util.HashSet;

/** Client labels only; Telegram network, Premium, account, support and legal references stay intact. */
final class FoldegramBranding {
    private static final HashSet<Integer> CLIENT_LABELS = new HashSet<>();
    private static final HashSet<String> CLIENT_KEYS = new HashSet<>();
    static {
        CLIENT_LABELS.add(R.string.AppName); CLIENT_KEYS.add("AppName");
        CLIENT_LABELS.add(R.string.AppNameBeta); CLIENT_KEYS.add("AppNameBeta");
        CLIENT_LABELS.add(R.string.NoChats); CLIENT_KEYS.add("NoChats");
        CLIENT_LABELS.add(R.string.ContactsPermissionAlert); CLIENT_KEYS.add("ContactsPermissionAlert");
        CLIENT_LABELS.add(R.string.NotificationsPermissionAlertSubtitle); CLIENT_KEYS.add("NotificationsPermissionAlertSubtitle");
        CLIENT_LABELS.add(R.string.ImportImportingInfo); CLIENT_KEYS.add("ImportImportingInfo");
        CLIENT_LABELS.add(R.string.TelegramVersion); CLIENT_KEYS.add("TelegramVersion");
        CLIENT_LABELS.add(R.string.UpdateTelegram); CLIENT_KEYS.add("UpdateTelegram");
        CLIENT_LABELS.add(R.string.PermissionStorageWithHint); CLIENT_KEYS.add("PermissionStorageWithHint");
        CLIENT_LABELS.add(R.string.PermissionNoAudioWithHint); CLIENT_KEYS.add("PermissionNoAudioWithHint");
        CLIENT_LABELS.add(R.string.PermissionNoBluetoothWithHint); CLIENT_KEYS.add("PermissionNoBluetoothWithHint");
        CLIENT_LABELS.add(R.string.PermissionNoAudioVideoWithHint); CLIENT_KEYS.add("PermissionNoAudioVideoWithHint");
        CLIENT_LABELS.add(R.string.PermissionNoCameraWithHint); CLIENT_KEYS.add("PermissionNoCameraWithHint");
        CLIENT_LABELS.add(R.string.PermissionNoCameraMicVideo); CLIENT_KEYS.add("PermissionNoCameraMicVideo");
        CLIENT_LABELS.add(R.string.PermissionNoLocation); CLIENT_KEYS.add("PermissionNoLocation");
        CLIENT_LABELS.add(R.string.PermissionNoLocationStory); CLIENT_KEYS.add("PermissionNoLocationStory");
        CLIENT_LABELS.add(R.string.PermissionNoLocationFriends); CLIENT_KEYS.add("PermissionNoLocationFriends");
        CLIENT_LABELS.add(R.string.PermissionNoLocationNavigation); CLIENT_KEYS.add("PermissionNoLocationNavigation");
        CLIENT_LABELS.add(R.string.PermissionNoContactsSharing); CLIENT_KEYS.add("PermissionNoContactsSharing");
        CLIENT_LABELS.add(R.string.PermissionNoContactsSaving); CLIENT_KEYS.add("PermissionNoContactsSaving");
        CLIENT_LABELS.add(R.string.PermissionNoStorageAvatar); CLIENT_KEYS.add("PermissionNoStorageAvatar");
        CLIENT_LABELS.add(R.string.PermissionNoAudioStorageStory); CLIENT_KEYS.add("PermissionNoAudioStorageStory");
        CLIENT_LABELS.add(R.string.PermissionDrawAboveOtherApps); CLIENT_KEYS.add("PermissionDrawAboveOtherApps");
        CLIENT_LABELS.add(R.string.PermissionDrawAboveOtherAppsGroupCall); CLIENT_KEYS.add("PermissionDrawAboveOtherAppsGroupCall");
        CLIENT_LABELS.add(R.string.PermissionXiaomiLockscreen); CLIENT_KEYS.add("PermissionXiaomiLockscreen");
        CLIENT_LABELS.add(R.string.PermissionFSILockscreen); CLIENT_KEYS.add("PermissionFSILockscreen");
        CLIENT_LABELS.add(R.string.PermissionBackgroundLocation); CLIENT_KEYS.add("PermissionBackgroundLocation");
        CLIENT_LABELS.add(R.string.ClearTelegramCache); CLIENT_KEYS.add("ClearTelegramCache");
        CLIENT_LABELS.add(R.string.TelegramCacheSize); CLIENT_KEYS.add("TelegramCacheSize");
        CLIENT_LABELS.add(R.string.Page1Title); CLIENT_KEYS.add("Page1Title");
        CLIENT_LABELS.add(R.string.VoipNeedMicPermissionWithHint); CLIENT_KEYS.add("VoipNeedMicPermissionWithHint");
        CLIENT_LABELS.add(R.string.VoipNeedMicCameraPermissionWithHint); CLIENT_KEYS.add("VoipNeedMicCameraPermissionWithHint");
        CLIENT_LABELS.add(R.string.VoipNeedCameraPermission); CLIENT_KEYS.add("VoipNeedCameraPermission");
        CLIENT_LABELS.add(R.string.BotLocationPermissionRequestDeniedApp); CLIENT_KEYS.add("BotLocationPermissionRequestDeniedApp");
        CLIENT_LABELS.add(R.string.AgeVerificationNeedCameraPermission); CLIENT_KEYS.add("AgeVerificationNeedCameraPermission");
    }
    static String clientLabel(String key, int id, String value) {
        if (id == R.string.Page1Message || "Page1Message".equals(key)) return ApplicationLoader.applicationContext.getString(R.string.FoldegramIntroBody);
        if (value == null || !(CLIENT_LABELS.contains(id) || CLIENT_KEYS.contains(key))) return value;
        String branded = value.replaceFirst("Telegram", "Foldegram");
        if (id == R.string.TelegramVersion || "TelegramVersion".equals(key)) branded += "\nUnofficial client · Based on Telegram";
        return branded;
    }
}
