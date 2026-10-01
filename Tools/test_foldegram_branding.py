#!/usr/bin/env python3
"""Exercise client branding allowlist; do not rename service, protocol, or legal terms."""
from pathlib import Path
import re,subprocess,tempfile,unittest
ROOT=Path(__file__).resolve().parents[1]
SOURCE=(ROOT/'TMessagesProj/src/main/java/org/telegram/messenger/FoldegramBranding.java').read_text()
class Branding(unittest.TestCase):
 def test_client_labels_and_service_boundaries(self):
  names=set(re.findall(r'R.string.(\w+)',SOURCE)); names.add('PaymentWarningText')
  java='package org.telegram.messenger; class R {static class string {'+''.join('static final int '+n+'='+str(i)+';' for i,n in enumerate(sorted(names)))+'}}\n'
  java+='''class ApplicationLoader { static ApplicationLoader applicationContext=new ApplicationLoader(); String getString(int id){return "Unofficial Telegram client";} }
class BrandingTest { public static void main(String[] args){
 if(!"Foldegram".equals(FoldegramBranding.clientLabel(null,R.string.AppName,"Telegram")))throw new AssertionError();
 if(!"Foldegram needs contacts; Telegram servers".equals(FoldegramBranding.clientLabel(null,R.string.ContactsPermissionAlert,"Telegram needs contacts; Telegram servers")))throw new AssertionError();
 if(!"Telegram Premium".equals(FoldegramBranding.clientLabel(null,R.string.PaymentWarningText,"Telegram Premium")))throw new AssertionError();
 if(!"Telegram".equals(FoldegramBranding.clientLabel("unlisted",-1,"Telegram")))throw new AssertionError();
 if(!"Foldegram".equals(FoldegramBranding.clientLabel("AppName",-1,"Telegram")))throw new AssertionError();
 if(!FoldegramBranding.clientLabel(null,R.string.TelegramVersion,"Telegram for Android %1$s").contains("Based on Telegram"))throw new AssertionError();
 }}'''
  with tempfile.TemporaryDirectory() as d:
   p=Path(d);(p/'FoldegramBranding.java').write_text(SOURCE);(p/'BrandingTest.java').write_text(java)
   subprocess.run(['javac','-d',d,str(p/'FoldegramBranding.java'),str(p/'BrandingTest.java')],check=True)
   subprocess.run(['java','-cp',d,'org.telegram.messenger.BrandingTest'],check=True)
 def test_notification_and_navigation_mark(self):
  res=ROOT/'TMessagesProj_AppFoldegram/src/common/res'
  self.assertEqual((res/'drawable-anydpi/notification.xml').read_text(),(ROOT/'TMessagesProj/src/main/res/drawable/foldegram_crane_mark.xml').read_text())
  main=(ROOT/'TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java').read_text()
  self.assertNotIn('R.drawable.telegram_logo_2',main)
  intro=(ROOT/'TMessagesProj/src/main/java/org/telegram/ui/IntroActivity.java').read_text()
  self.assertIn('textureView.setVisibility(View.GONE)',intro)
if __name__=='__main__':unittest.main()
