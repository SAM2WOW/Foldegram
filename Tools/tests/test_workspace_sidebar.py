#!/usr/bin/env python3
"""Host lifecycle and ownership regressions. No Android UI/touch/blur claims."""
from pathlib import Path
import subprocess,tempfile,unittest
from test_chat_drop_policy import method
ROOT=Path(__file__).resolve().parents[2]
SOURCE=(ROOT/'TMessagesProj/src/main/java/org/telegram/ui/FoldegramChatWindowActivity.java').read_text()
class Sidebar(unittest.TestCase):
 def test_picker_never_resumes_chat_behind_it(self):
  code='''public class SidebarTest {
 static class SharedConfig { static boolean appLocked,isWaitingForPasscodeEnter; }
 static class View { static final int VISIBLE=0; int visibility=8; int getVisibility(){return visibility;} }
 static class Layout { int resumes; void onResume(){resumes++;} }
 boolean resumed=true,destroyed,chatsCreated=true,paneResumed,sidebarOpen,finishing;
 int activePane; View passcodeView=new View(); Layout[] paneLayouts={new Layout(),new Layout()};
 boolean isFinishing(){return finishing;}
 METHOD
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  SidebarTest t=new SidebarTest();
  for(int i=0;i<100;i++){
   t.paneResumed=false;t.sidebarOpen=true;t.activePane=i%2;
   int n=t.paneLayouts[t.activePane].resumes;
   t.resumeActivePane();check(!t.paneResumed && t.paneLayouts[t.activePane].resumes==n);
   t.sidebarOpen=false;t.resumeActivePane();t.resumeActivePane();
   check(t.paneResumed && t.paneLayouts[t.activePane].resumes==n+1);
  }
  t.paneResumed=false;SharedConfig.appLocked=true;t.resumeActivePane();check(!t.paneResumed);
  SharedConfig.appLocked=false;SharedConfig.isWaitingForPasscodeEnter=true;t.resumeActivePane();check(!t.paneResumed);
  SharedConfig.isWaitingForPasscodeEnter=false;t.resumed=false;t.resumeActivePane();check(!t.paneResumed);
  t.resumed=true;t.destroyed=true;t.resumeActivePane();check(!t.paneResumed);
 }
}'''.replace('METHOD',method(SOURCE,'private void resumeActivePane('))
  with tempfile.TemporaryDirectory() as d:
   p=Path(d)/'SidebarTest.java';p.write_text(code)
   subprocess.run(['javac',str(p)],check=True);subprocess.run(['java','-cp',d,'SidebarTest'],check=True)
 def test_stack_and_composition_boundaries(self):
  picker=method(SOURCE,'private void showReplacementPicker(')
  self.assertNotIn('removeAllFragments()',picker)
  navigation=method(SOURCE,'private boolean openSidebarConversation(')
  self.assertIn('canTransferComposition(previous)',navigation)
  self.assertLess(navigation.index('savePaneDraft(targetPane)'),navigation.index('paneLayouts[targetPane].presentFragment(chat)'))
  self.assertNotIn('removeAllFragments()',navigation)
  self.assertIn('!resumed || shouldLock()',picker)
  self.assertIn('sidebarOpen',method(SOURCE,'public boolean activateForDrop('))
  for signature in ['protected void onPause(', 'protected void onDestroy(', 'private void showPasscode(']:
   self.assertIn('closeSidebar()',method(SOURCE,signature))
 def test_drag_session_account_lock_and_expiry(self):
  code='''import java.util.UUID;
public class SidebarDragTest {
 static class SystemClock { static long time=100; static long elapsedRealtime(){return time;} }
 static class UserConfig { static boolean valid=true; static long user=42; static boolean isValidAccount(int a){return valid;} static UserConfig getInstance(int a){return new UserConfig();} long getClientUserId(){return user;} }
 RECORD
 SidebarDrag sidebarDrag=new SidebarDrag(123,1,42);boolean sidebarOpen=true,resumed=true,destroyed,finishing,locked;
 int currentAccount=1;long accountUserId=42;boolean isFinishing(){return finishing;}boolean shouldLock(){return locked;}
 METHOD
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  SidebarDragTest t=new SidebarDragTest();check(t.isSidebarDragAuthorized());
  t.currentAccount=2;check(!t.isSidebarDragAuthorized());t.currentAccount=1;
  UserConfig.user=43;check(!t.isSidebarDragAuthorized());UserConfig.user=42;
  t.locked=true;check(!t.isSidebarDragAuthorized());t.locked=false;
  t.resumed=false;check(!t.isSidebarDragAuthorized());t.resumed=true;
  t.sidebarOpen=false;check(!t.isSidebarDragAuthorized());t.sidebarOpen=true;
  t.destroyed=true;check(!t.isSidebarDragAuthorized());t.destroyed=false;
  SystemClock.time=120101;check(!t.isSidebarDragAuthorized());
 }
}'''.replace('RECORD',method(SOURCE,'private static final class SidebarDrag')).replace('METHOD',method(SOURCE,'private boolean isSidebarDragAuthorized('))
  with tempfile.TemporaryDirectory() as d:
   p=Path(d)/'SidebarDragTest.java';p.write_text(code)
   subprocess.run(['javac',str(p)],check=True);subprocess.run(['java','-cp',d,'SidebarDragTest'],check=True)
 def test_drag_is_local_and_cancel_keeps_picker(self):
  start=method(SOURCE,'private boolean startSidebarDrag(')
  self.assertIn('sidebarDrag, 0)',start)
  self.assertNotIn('DRAG_FLAG_GLOBAL',SOURCE)
  handler=method(SOURCE,'private boolean handleSidebarDrag(')
  self.assertIn('event.getLocalState() != sidebarDrag',handler)
  self.assertIn('sidebarDrag.token.contentEquals',handler)
  self.assertIn('sidebarOverlay.setVisibility(View.VISIBLE)',handler)
  self.assertIn('openSidebarConversation(',handler)
  self.assertIn('WindowInsetsCompat.Type.systemGestures()',SOURCE)
  self.assertIn('leftGestureInset + AndroidUtilities.dp(4)',SOURCE)
  self.assertIn('if (SharedConfig.animationsEnabled())',SOURCE)

 def test_single_header_and_accessible_native_picker(self):
  self.assertNotIn('ScrollSlidingTextTabStrip',SOURCE)
  self.assertNotIn('content.addView(toolbar',SOURCE)
  self.assertIn('DialogsActivity picker = createPicker(currentAccount)',SOURCE)
  self.assertIn('setSelected(i == activePane)',SOURCE)
  self.assertIn('IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS',SOURCE)
  self.assertIn('Math.min(AndroidUtilities.dp(360)',SOURCE)
  chat=(ROOT/'TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java').read_text()
  self.assertIn('menu.addItem(foldegram_open_second_chat',chat)
  self.assertIn('actionBar.setupGlass(',chat)
if __name__=='__main__':unittest.main()
