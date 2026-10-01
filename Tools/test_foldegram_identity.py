#!/usr/bin/env python3
"""Static identity/isolation checks. Does not replace a merged APK or device test."""
from pathlib import Path
import re
import xml.etree.ElementTree as E

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / 'TMessagesProj_AppFoldegram'
ANDROID = '{http://schemas.android.com/apk/res/android}'
TOOLS = '{http://schemas.android.com/tools}'
ID = 'dev.foldegram.messenger'
for file in APP.rglob('*.xml'):
    if '/build/' not in str(file):
        E.parse(file)

# Resource directories in one source set must not define duplicate value keys.
seen = set()
for source in (APP / 'src/main/res', APP / 'src/common/res'):
    for values in source.glob('values*/*.xml'):
        for item in E.parse(values).getroot():
            identity = (values.parent.name, item.tag, item.get('name'))
            assert identity not in seen, f'duplicate resource in main source set: {identity}'
            seen.add(identity)

xml = APP / 'src/main/res/xml' 
assert E.parse(xml / 'auth.xml').getroot().get(ANDROID + 'accountType') == ID
assert E.parse(xml / 'sync_contacts.xml').getroot().get(ANDROID + 'accountType') == ID
assert E.parse(xml / 'auth_menu.xml').find('.//intent').get(ANDROID + 'targetPackage') == ID
for kind in E.parse(xml / 'contacts.xml').getroot():
    assert f'vnd.{ID}.android.' in kind.get(ANDROID + 'mimeType')

library = E.parse(ROOT / 'TMessagesProj/src/main/AndroidManifest.xml').getroot()
for provider in library.findall('application/provider'):
    assert '${applicationId}.' in provider.get(ANDROID + 'authorities')
for data in library.findall('.//data'):
    mime = data.get(ANDROID + 'mimeType', '')
    if 'vnd.android.cursor.item/vnd.' in mime:
        assert '${applicationId}' in mime

contacts = (ROOT / 'TMessagesProj/src/main/java/org/telegram/messenger/ContactsController.java').read_text()
# Ignore upstream's commented-out legacy migration when checking executable code.
contacts = re.sub(r'/\*.*?\*/', '', contacts, flags=re.S)
assert not re.search(r'(getAccountsByType|new Account)\([^;]*"org\.telegram\.', contacts)

app = E.parse(APP / 'src/main/AndroidManifest.xml').getroot().find('application')
assert app.get(ANDROID + 'name') == 'dev.foldegram.messenger.FoldegramApplication'
assert app.get(ANDROID + 'label') == '@string/foldegram_app_name'
assert app.get(ANDROID + 'icon') == '@mipmap/foldegram_launcher'
assert app.get(ANDROID + 'allowBackup') == 'false'
for alias in app.findall('activity-alias'):
    assert alias.get(ANDROID + 'icon') == '@mipmap/foldegram_launcher'
assert any(provider.get(ANDROID + 'name') == 'com.google.firebase.provider.FirebaseInitProvider'
           and provider.get(TOOLS + 'node') == 'remove' for provider in app.findall('provider'))

stub = E.parse(APP / 'src/stub/AndroidManifest.xml').getroot()
assert not stub.findall('uses-permission')
stub_app = stub.find('application')
assert len(stub_app.findall('activity')) == 1
assert stub_app.find('activity').get(ANDROID + 'name') == 'dev.foldegram.messenger.FoldegramSetupActivity'
assert not any(stub_app.findall(tag) for tag in ('service', 'provider', 'receiver', 'activity-alias'))
print('PASS: XML syntax, account/contact/provider isolation, launcher identity, Firebase removal, offline setup manifest')
print('NOTE: these are source-level checks; inspect final merged manifests and APKs separately')
