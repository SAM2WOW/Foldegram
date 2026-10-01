#!/usr/bin/env python3
"""Execute extracted lifecycle gates with Java doubles; NOT Android/device bubble QA."""
from pathlib import Path
import subprocess,tempfile,unittest
from test_chat_drop_policy import method
ROOT=Path(__file__).resolve().parents[2]
SOURCE=(ROOT/'TMessagesProj/src/main/java/org/telegram/ui/BubbleActivity.java').read_text()
NOTIFICATIONS=(ROOT/'TMessagesProj/src/main/java/org/telegram/messenger/NotificationsController.java').read_text()
class BubbleLifecycle(unittest.TestCase):
 def test_balanced_resume_pause_and_passcode(self):
  methods='\n'.join(method(SOURCE,s) for s in ['private void pauseLayout(', 'private void resumeLayout('])
  java='''import java.util.ArrayList;
public class BubbleLifecycleTest {
 static class View { static final int VISIBLE=0; int visibility=8; int getVisibility(){return visibility;} }
 static class SharedConfig { static boolean appLocked,isWaitingForPasscodeEnter; }
 static class Layout { int resumes,pauses; void onResume(){resumes++;} void onPause(){pauses++;} }
 boolean finished,activityResumed,layoutResumed;
 Layout actionBarLayout=new Layout(); View passcodeView=new View(); ArrayList<Object> mainFragmentsStack=new ArrayList<>();
 METHODS
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  BubbleLifecycleTest t=new BubbleLifecycleTest();
  t.resumeLayout(); check(t.actionBarLayout.resumes==0);
  t.mainFragmentsStack.add(new Object()); t.activityResumed=true;
  for(int i=0;i<100;i++){t.resumeLayout();t.resumeLayout();t.pauseLayout();t.pauseLayout();}
  check(t.actionBarLayout.resumes==100 && t.actionBarLayout.pauses==100);
  SharedConfig.appLocked=true;t.resumeLayout();check(!t.layoutResumed);SharedConfig.appLocked=false;
  SharedConfig.isWaitingForPasscodeEnter=true;t.resumeLayout();check(!t.layoutResumed);SharedConfig.isWaitingForPasscodeEnter=false;
  t.passcodeView.visibility=0;t.resumeLayout();check(!t.layoutResumed);
  t.passcodeView.visibility=8;t.resumeLayout();check(t.layoutResumed);
  t.activityResumed=false;t.pauseLayout();t.resumeLayout();check(!t.layoutResumed);
  t.activityResumed=true;t.finished=true;t.resumeLayout();check(!t.layoutResumed);
 }
}'''.replace('METHODS',methods)
  with tempfile.TemporaryDirectory() as d:
   p=Path(d)/'BubbleLifecycleTest.java';p.write_text(java)
   subprocess.run(['javac',str(p)],check=True)
   subprocess.run(['java','-cp',d,'BubbleLifecycleTest'],check=True)
 def test_window_and_notification_wiring(self):
  self.assertNotIn('closeOtherAppActivities',SOURCE)
  resume=method(SOURCE,'protected void onResume(')
  self.assertNotIn('actionBarLayout.onResume()',resume)
  config=method(SOURCE,'public void onConfigurationChanged(')
  self.assertIn('invalidate()',config)
  self.assertIn('requestLayout()',config)
  destroy=method(SOURCE,'protected void onDestroy(')
  self.assertIn('removeAllFragments()',destroy)
  self.assertIn('clearOpenedBubble()',destroy)
  intent=method(SOURCE,'private boolean handleIntent(')
  self.assertLess(intent.index('clearOpenedBubble()'),intent.index('currentAccount = nextAccount'))
  self.assertIn('setIntent(intent)',method(SOURCE,'protected void onNewIntent('))
  self.assertIn('com.tmessages.openchat.bubble." + currentAccount + "." + did',NOTIFICATIONS)
  self.assertIn('shortcutIntent.putExtra("currentAccount", currentAccount)',NOTIFICATIONS)
  self.assertIn('bubbleBuilder.setDesiredHeight(640)',NOTIFICATIONS)
if __name__=='__main__':unittest.main()
