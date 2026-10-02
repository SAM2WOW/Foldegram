#!/usr/bin/env python3
"""Real reason reference counting + extracted binding; no screenshot/device claim."""
from pathlib import Path
import subprocess,tempfile,unittest
from test_chat_drop_policy import method
ROOT=Path(__file__).resolve().parents[2]
CHAT=(ROOT/'TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java').read_text()
class CapturePolicy(unittest.TestCase):
 def test_rebuild_and_two_pane_protection(self):
  harness='''import org.telegram.messenger.FlagSecureReason;
public class CaptureTest {
 FlagSecureReason flagSecure; android.view.Window flagSecureWindow;
 Object currentEncryptedChat; boolean protectedPeer,isFullyVisible=true;
 static class Activity { android.view.Window window=new android.view.Window(); android.view.Window getWindow(){return window;} }
 Activity activity; Activity getParentActivity(){return activity;} boolean isPeerNoForwards(){return protectedPeer;}
 METHOD
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] a){
  Activity shared=new Activity(); CaptureTest left=new CaptureTest(),right=new CaptureTest();left.activity=right.activity=shared;
  left.bindFoldegramCapturePolicy();right.bindFoldegramCapturePolicy();check(shared.window.flags==0);
  left.protectedPeer=true;left.flagSecure.invalidate();check(shared.window.flags!=0);
  FlagSecureReason original=left.flagSecure;
  for(int i=0;i<100;i++){left.bindFoldegramCapturePolicy();right.bindFoldegramCapturePolicy();check(left.flagSecure==original);check(shared.window.flags!=0);}
  // No leaked count after the last protected reason is removed.
  left.protectedPeer=false;left.flagSecure.invalidate();check(shared.window.flags==0);
  right.currentEncryptedChat=new Object();right.flagSecure.invalidate();check(shared.window.flags!=0);
  left.flagSecure.detach();check(shared.window.flags!=0);
  right.flagSecure.detach();check(shared.window.flags==0);
  // Independent passcode reason must survive ordinary chat teardown.
  FlagSecureReason passcode=new FlagSecureReason(shared.window,()->true);passcode.attach();
  left.bindFoldegramCapturePolicy();left.flagSecure.attach();left.flagSecure.detach();check(shared.window.flags!=0);
  passcode.detach();check(shared.window.flags==0);
  // Moving a still-visible protected fragment must release only its old-window ownership.
  right.flagSecure.attach();right.activity=new Activity();right.bindFoldegramCapturePolicy();
  check(shared.window.flags==0 && right.activity.window.flags!=0);
  right.flagSecure.detach();check(right.activity.window.flags==0);
 }
}'''.replace('METHOD',method(CHAT,'private void bindFoldegramCapturePolicy('))
  with tempfile.TemporaryDirectory() as d:
   p=Path(d)
   files={
    'CaptureTest.java':harness,
    'android/view/Window.java':'package android.view; public class Window { public int flags; public void addFlags(int f){flags|=f;} public void clearFlags(int f){flags&=~f;} }',
    'android/view/WindowManager.java':'package android.view; public class WindowManager { public static class LayoutParams { public static final int FLAG_SECURE=1; } }',
    'org/telegram/messenger/AndroidUtilities.java':'package org.telegram.messenger; public class AndroidUtilities { public static void logFlagSecure(){} }',
    'org/telegram/messenger/FlagSecureReason.java':(ROOT/'TMessagesProj/src/main/java/org/telegram/messenger/FlagSecureReason.java').read_text()}
   for name,body in files.items():
    f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(body)
   subprocess.run(['javac','-d',d]+[str(p/n) for n in files],check=True)
   subprocess.run(['java','-cp',d,'CaptureTest'],check=True)
 def test_protected_conditions_and_workspace_settings_remain(self):
  binding=method(CHAT,'private void bindFoldegramCapturePolicy(')
  self.assertIn('currentEncryptedChat != null || isPeerNoForwards()',binding)
  self.assertNotIn('clearFlags',CHAT)
  workspace=(ROOT/'TMessagesProj/src/main/java/org/telegram/ui/FoldegramChatWindowActivity.java').read_text()
  self.assertIn('!SharedConfig.passcodeHash.isEmpty() && !SharedConfig.allowScreenCapture',workspace)
if __name__=='__main__':unittest.main()
