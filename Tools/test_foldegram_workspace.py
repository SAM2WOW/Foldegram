#!/usr/bin/env python3
"""Host-only geometry + source-boundary checks, not Android/device lifecycle tests."""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui'
ACTIVITY = (JAVA / 'FoldegramChatWindowActivity.java').read_text()


def section(start, end):
    return ACTIVITY.split(start, 1)[1].split(end, 1)[0]


class WorkspaceBoundaries(unittest.TestCase):
    def test_two_owned_stacks_without_global_close_broadcast(self):
        self.assertIn('final ArrayList<BaseFragment> firstStack = new ArrayList<>()', ACTIVITY)
        self.assertIn('final ArrayList<BaseFragment> secondStack = new ArrayList<>()', ACTIVITY)
        self.assertIn('layout.setFragmentStack(i == 0 ? firstStack : secondStack)', ACTIVITY)
        self.assertNotIn('postNotificationName(NotificationCenter.closeChats', ACTIVITY)
        self.assertNotIn('postNotificationName(NotificationCenter.closeOtherAppActivities', ACTIVITY)
        self.assertNotIn('FLAG_ACTIVITY_LAUNCH_ADJACENT', ACTIVITY)

    def test_original_chat_has_single_owner(self):
        launch = section('public static void showChatPicker', 'private static DialogsActivity createPicker')
        self.assertLess(launch.index('chat.saveDraft()'), launch.index('activity.startActivity(intent)'))
        self.assertLess(launch.index('activity.startActivity(intent)'), launch.index('chat.finishFragment(false)'))
        support = section('private static boolean canTransferComposition', 'public static void showChatPicker')
        for check in ('isEditingMessageMedia', 'isEditingMessage', 'isRecordingAudioVideo', 'hasAudioToSend'):
            self.assertIn(check, support)
        self.assertIn('hasFoldegramPendingForward()', support)
        chat = (JAVA / 'ChatActivity.java').read_text()
        self.assertIn('public boolean hasFoldegramPendingForward()', chat)

    def test_account_identity_checked_not_only_slot(self):
        read = section('private boolean readArguments', 'private void buildViews')
        self.assertIn('UserConfig.isValidAccount(currentAccount)', read)
        self.assertIn('accountUserId != UserConfig.getInstance(currentAccount).getClientUserId()', read)

    def test_security_reason_is_composed(self):
        secure = section('private void updateSecureFlag', 'private void cancelLockRunnable')
        self.assertIn('new FlagSecureReason', secure)
        self.assertNotIn('clearFlags', secure)
        self.assertIn('passcodeSecureReason.detach()', ACTIVITY)

    def test_unlock_resumes_after_overlay_hidden(self):
        hidden = section('protected void onHidden()', 'root.addView(passcodeView')
        self.assertIn('revealUnlockedContent()', hidden)
        notification = section('} else if (id == NotificationCenter.didSetPasscode)', '} else if (id == NotificationCenter.passcodeDismissed)')
        self.assertNotIn('showPasscode()', notification)
        self.assertIn('updateSecureFlag()', notification)

    def test_drop_activation_cannot_bypass_lock_or_visibility(self):
        drop = section('public boolean activateForDrop', '/** Avoid silently')
        for guard in ('!resumed', 'destroyed', 'SharedConfig.appLocked', 'SharedConfig.isWaitingForPasscodeEnter', 'pane.isShown()', 'getLastFragment() == owner'):
            self.assertIn(guard, drop)
        self.assertIn('setActivePane(i, false)', drop)

    def test_theme_rebuild_deferred_and_rebalanced(self):
        theme = section('private void applyPendingTheme', 'private boolean shouldLock')
        for guard in ('!resumed', '!chatsCreated', 'SharedConfig.appLocked', 'SharedConfig.isWaitingForPasscodeEnter', 'passcodeView.getVisibility() == View.VISIBLE'):
            self.assertIn(guard, theme)
        self.assertLess(theme.index('rebuildFragments('), theme.rindex('layout.onPause()'))
        self.assertLess(theme.rindex('layout.onPause()'), theme.index('resumeActivePane()'))

    def test_menu_eligibility_does_not_require_constructed_composer(self):
        eligibility = section('public static boolean canOpenFrom', 'private static boolean canTransferComposition')
        self.assertNotIn('getChatActivityEnterView()', eligibility)

    def test_platform_back_routes_to_focused_stack(self):
        self.assertIn('getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true)', ACTIVITY)
        self.assertIn('FoldegramChatWindowActivity.this.onBackPressed()', ACTIVITY)
        back = section('public void onBackPressed()', 'public boolean needCloseLastFragment')
        self.assertIn('paneLayouts[activePane].onBackPressed()', back)
        self.assertNotIn('getOnBackPressedDispatcher()', back)

    def test_strings_are_real_android_resources(self):
        resources = set()
        for xml in (ROOT / 'TMessagesProj/src/main/res/values').glob('*.xml'):
            for tag in ET.parse(xml).getroot():
                if tag.tag == 'string':
                    resources.add(tag.attrib['name'])
        self.assertFalse(set(re.findall(r'R\.string\.(\w+)', ACTIVITY)) - resources)
        self.assertNotRegex(ACTIVITY, r'LocaleController\.(?:getString|formatString)\([^\n]*Foldegram')

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK unavailable')
    def test_real_geometry_policy_at_fold_boundaries(self):
        harness = '''package org.telegram.ui;
public class GeometryTest {
  public static void main(String[] args) {
    for (int threshold : new int[]{600, 1200, 1800}) {
      for (int width : new int[]{0, 1, threshold - 1, threshold, threshold + 1, 2200}) {
        boolean dual = FoldegramPaneGeometry.isDualPane(width, threshold);
        if (dual != (width >= threshold)) throw new AssertionError("breakpoint");
        int a = FoldegramPaneGeometry.width(width, threshold, 1, 0);
        int b = FoldegramPaneGeometry.width(width, threshold, 1, 1);
        int x = FoldegramPaneGeometry.left(width, threshold, 1, 1);
        if (dual && (a + b + 1 != width || x != a + 1 || Math.abs(a-b)>1)) throw new AssertionError("partition");
        if (!dual && (a != width || b != width || x != 0)) throw new AssertionError("narrow");
      }
    }
  }
}'''
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp)
            (path / 'GeometryTest.java').write_text(harness)
            subprocess.run(['javac', '-d', temp, str(JAVA / 'FoldegramPaneGeometry.java'), str(path / 'GeometryTest.java')], check=True)
            subprocess.run(['java', '-cp', temp, 'org.telegram.ui.GeometryTest'], check=True)


if __name__ == '__main__':
    unittest.main()
