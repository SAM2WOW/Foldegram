#!/usr/bin/env python3
"""Production geometry/state method checks with host doubles, not Android/device UI tests."""
from pathlib import Path
import subprocess,tempfile,unittest
from test_chat_drop_policy import method
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/'TMessagesProj/src/main/java/org/telegram/ui'
HOME=(SRC/'FoldegramHomeWorkspace.java').read_text()
class HomeWorkspace(unittest.TestCase):
 def java(self, source, files=()):
  with tempfile.TemporaryDirectory() as d:
   p=Path(d); (p/'HomeTest.java').write_text(source)
   extra=[]
   for name,content in files:
    f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(content);extra.append(str(f))
   r=subprocess.run(['javac','-d',d,str(p/'HomeTest.java'),*extra],capture_output=True,text=True)
   self.assertEqual(r.returncode,0,r.stderr)
   r=subprocess.run(['java','-cp',d,'org.telegram.ui.HomeTest'],capture_output=True,text=True)
   self.assertEqual(r.returncode,0,r.stderr)
 def test_window_geometry_fold_keyboard_and_rail(self):
  self.java('''package org.telegram.ui;
public class HomeTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[]args){
  for(int cycle=0;cycle<100;cycle++)for(int w:new int[]{0,240,360,599,600,720,840,1200,1800})
   for(boolean two:new boolean[]{false,true})for(boolean expanded:new boolean[]{false,true})for(int active=0;active<2;active++){
    FoldegramHomeGeometry g=new FoldegramHomeGeometry(w,600,56,320,8,two,expanded,true,active);
    check(g.list>=0&&g.rail>=0&&g.firstWidth>=0&&g.secondWidth>=0);
    check(g.rail+g.list<=w&&g.firstX+g.firstWidth<=w&&g.secondX+g.secondWidth<=w);
    if(g.firstWidth>0&&g.secondWidth>0)check(g.firstX+g.firstWidth+8==g.secondX);
    if(two)check(g.rail==Math.min(w,56));
    if(w<600&&two&&!expanded)check((active==0?g.firstWidth:g.secondWidth)==Math.max(0,w-56));
    if(two&&!expanded&&w>=600)check(Math.abs(g.firstWidth-g.secondWidth)<=1);
   }
  FoldegramHomeGeometry home=new FoldegramHomeGeometry(840,600,56,320,8,false,false,true,0);
  FoldegramHomeGeometry split=new FoldegramHomeGeometry(840,600,56,320,8,true,false,true,1);
  check(home.list==320&&home.firstX==320&&split.firstX==56&&split.secondX>split.firstX);
 }
}''',[('FoldegramHomeGeometry.java',(SRC/'FoldegramHomeGeometry.java').read_text())])
 def test_open_close_back_preserves_home_and_other_stack(self):
  code='''package org.telegram.ui;
import java.util.*;
public class HomeTest {
 static class BaseFragment { }
 static class ChatActivity extends BaseFragment {long dialog;ChatActivity(long d){dialog=d;}int getCurrentAccount(){return 0;} }
 static class UserConfig {static int selectedAccount;}
 static class R {static class string {static int FoldegramChooseDifferentChat,FoldegramFinishComposition;}}
 static class Toast {static int LENGTH_SHORT,LENGTH_LONG;static Toast makeText(Object h,int r,int t){return new Toast();}void show(){}}
 static class INavigationLayout {
  ArrayList<BaseFragment> stack=new ArrayList<>();boolean safe=true;int scroll=83;String draft="unsent";int backs;
  static class NavigationParams {BaseFragment fragment;NavigationParams(BaseFragment f){fragment=f;}NavigationParams setCheckPresentFromDelegate(boolean b){return this;}}
  ArrayList<BaseFragment> getFragmentStack(){return stack;}
  boolean presentFragment(NavigationParams p){stack.add(p.fragment);return true;}
  void removeAllFragments(){stack.clear();} void onBackPressed(){backs++;}
 }
 Object host;boolean two,choosing,expanded,listFocused,permit=true;int active;
 INavigationLayout first=new INavigationLayout(),second=new INavigationLayout();Object drag;
 boolean allowed(){return permit;}boolean compositionSafe(INavigationLayout l){return l.safe;}
 boolean sameDialog(ChatActivity c,INavigationLayout l){return !l.stack.isEmpty()&&((ChatActivity)l.stack.get(l.stack.size()-1)).dialog==c.dialog;}
 INavigationLayout pane(int p){return p==0?first:second;}
 void saveDraft(INavigationLayout l){}void cancelDrag(){drag=null;}void animateLayout(){}void syncFocus(){}
 PRESENT
 CLOSE
 CLOSESECOND
 BACK
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[]a){HomeTest h=new HomeTest();ChatActivity original=new ChatActivity(1);h.first.stack.add(original);
  for(int n=0;n<100;n++){
   h.choosing=h.expanded=true; h.present(new INavigationLayout(),new INavigationLayout.NavigationParams(new ChatActivity(2)));
   check(h.two&&!h.expanded&&!h.choosing&&h.active==1&&h.second.stack.size()==1);
   check(h.first.stack.get(0)==original&&h.first.scroll==83&&h.first.draft.equals("unsent"));
   h.expanded=true;check(h.back(false)&&h.expanded);h.back(true);check(!h.expanded&&h.two);
   h.second.safe=false;h.close(h.second);check(h.two);h.second.safe=true;
   h.close(h.second);check(!h.two&&h.second.stack.isEmpty()&&h.first.stack.get(0)==original);
  }
  h.choosing=true;h.present(h.first,new INavigationLayout.NavigationParams(new ChatActivity(1)));check(!h.two);
  h.permit=false;h.present(h.first,new INavigationLayout.NavigationParams(new ChatActivity(3)));check(!h.two);
 }
}'''
  for key,signature in [('CLOSESECOND','private void closeSecond('),('PRESENT','Boolean present('),('CLOSE','Boolean close('),('BACK','boolean back(')]:code=code.replace(key,method(HOME,signature))
  self.java(code)
 def test_resize_never_reparents_or_recreates_fragments(self):
  for sig in ['void measure(', 'void layout(', 'private void animateLayout(']:
   body=method(HOME,sig)
   for forbidden in ['new ChatActivity','removeAllFragments','setParentLayout','rebuildFragments','startActivity']:self.assertNotIn(forbidden,body)
  launch=(SRC/'LaunchActivity.java').read_text()
  for sig in ['private void invalidateTabletMode(', 'private void checkLayout(']:
   body=method(launch,sig);self.assertLess(body.index('if (foldegramHome != null)'),body.index('return;'))
 def test_local_drag_cancellation_and_capture_boundaries(self):
  start=method(HOME,'private boolean startDrag(')
  self.assertIn('new View.DragShadowBuilder(row), drag, 0)',start)
  handler=method(HOME,'private boolean onDrag(')
  for required in ['event.getLocalState() != drag','drag.account != UserConfig.selectedAccount','getClientUserId()','120000','drag.token.contentEquals','compositionSafe','sameDialog']:
   self.assertIn(required,handler)
  cancel=method(HOME,'private void cancelDrag(')
  self.assertIn('!drag.used',cancel);self.assertIn('expanded = drag.expanded',cancel)
  self.assertNotIn('FLAG_SECURE',HOME)
  self.assertNotIn('setRemoveActionBarExtraHeight(true)',HOME)
  self.assertNotIn('setInBubbleMode(true)',HOME)
  self.assertIn('Type.systemGestures()',HOME)
 def test_platform_results_do_not_broadcast_media_to_both_chats(self):
  launch=(SRC/'LaunchActivity.java').read_text()
  capture=method(launch,'private static boolean isFoldegramMediaResult(')
  self.assertNotIn('520',capture)
  result=method(launch,'protected void onActivityResult(')
  self.assertIn('foldegramResultOwners.remove(requestCode)',result)
  self.assertIn('getLastFragment() == foldegramOwner',result)
  self.assertIn('!SharedConfig.appLocked',result)
  self.assertNotIn('foldegramHome.second.getLastFragment().onActivityResultFragment',result)
  permission=method(launch,'public void onRequestPermissionsResult(')
  self.assertIn('if (foldegramHome != null && foldegramHome.isTwo())',permission)
  self.assertIn('owner.onRequestPermissionsResultFragment',permission)

 def test_crane_geometry_unchanged(self):
  import subprocess
  path='TMessagesProj/src/main/res/drawable/foldegram_crane_mark.xml'
  self.assertEqual((ROOT/path).read_bytes(),subprocess.check_output(['git','show','00e546c1d:'+path],cwd=ROOT))
  stories=(SRC/'Stories/DialogStoriesCell.java').read_text()
  self.assertIn('LayoutHelper.createFrame(26, 26)',stories)
  self.assertNotIn('LayoutHelper.createFrame(90, 22)',stories)
if __name__=='__main__':unittest.main()
