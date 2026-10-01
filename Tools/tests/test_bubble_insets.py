#!/usr/bin/env python3
"""Production metric synchronization with Java doubles, not device/window-manager QA."""
from pathlib import Path
import subprocess,tempfile,unittest
from test_chat_drop_policy import method
ROOT=Path(__file__).resolve().parents[2]
SOURCE=(ROOT/'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/DrawerLayoutContainer.java').read_text()
class BubbleInsets(unittest.TestCase):
 def test_repeated_window_transitions(self):
  code='''public class InsetsTest {
 static class Insets { int top,bottom; Insets(int t,int b){top=t;bottom=b;} }
 static class Layout { boolean bubble; Layout(boolean b){bubble=b;} boolean isInBubbleMode(){return bubble;} }
 static class AndroidUtilities { static int statusBarHeight,navigationBarHeight; }
 boolean publishSystemBarMetrics=true;
 METHOD
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  InsetsTest main=new InsetsTest(),bubble=new InsetsTest(),unattached=new InsetsTest();
  bubble.publishSystemBarMetrics=false;unattached.publishSystemBarMetrics=false;
  for(int i=0;i<100;i++){
   Insets full=new Insets(i%2==0?72:96,48);
   main.updateSharedSystemBarMetrics(full);
   check(!bubble.updateSharedSystemBarMetrics(new Insets(0,0)));
   check(AndroidUtilities.statusBarHeight==full.top && AndroidUtilities.navigationBarHeight==48);
   check(!main.updateSharedSystemBarMetrics(full));
   // Reproduce a stale global value despite unchanged local main-window insets.
   AndroidUtilities.statusBarHeight=0;AndroidUtilities.navigationBarHeight=0;
   check(main.updateSharedSystemBarMetrics(full));
   check(AndroidUtilities.statusBarHeight==full.top && AndroidUtilities.navigationBarHeight==48);
   check(!unattached.updateSharedSystemBarMetrics(new Insets(0,0)));
  }
  // Real full-window zero insets are allowed; never hardcode a minimum top padding.
  check(main.updateSharedSystemBarMetrics(new Insets(0,0)));
  check(AndroidUtilities.statusBarHeight==0 && AndroidUtilities.navigationBarHeight==0);
 }
}'''.replace('METHOD',method(SOURCE,'private boolean updateSharedSystemBarMetrics('))
  with tempfile.TemporaryDirectory() as d:
   p=Path(d)/'InsetsTest.java';p.write_text(code)
   subprocess.run(['javac',str(p)],check=True);subprocess.run(['java','-cp',d,'InsetsTest'],check=True)
 def test_dispatch_and_focus_wiring(self):
  dispatch=method(SOURCE,'private WindowInsetsCompat onApplyWindowInsets(')
  self.assertLess(dispatch.index('updateSharedSystemBarMetrics(systemInsets)'),dispatch.index('if (sharedMetricsChanged'))
  self.assertIn('requestApplyInsets(this)',method(SOURCE,'public void onWindowFocusChanged('))
  bubble=(ROOT/'TMessagesProj/src/main/java/org/telegram/ui/BubbleActivity.java').read_text()
  self.assertLess(bubble.index('setParentActionBarLayout(actionBarLayout)'),bubble.index('setContentView(drawerLayoutContainer'))
if __name__=='__main__':unittest.main()
