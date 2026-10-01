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
  self.assertIn('canTransferComposition(previous)',picker)
  self.assertLess(picker.index('savePaneDraft(targetPane)'),picker.index('paneLayouts[targetPane].presentFragment(chat)'))
  self.assertIn('!resumed || shouldLock()',picker)
  self.assertIn('sidebarOpen',method(SOURCE,'public boolean activateForDrop('))
  for signature in ['protected void onPause(', 'protected void onDestroy(', 'private void showPasscode(']:
   self.assertIn('closeSidebar()',method(SOURCE,signature))
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
