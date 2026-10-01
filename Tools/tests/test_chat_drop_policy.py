#!/usr/bin/env python3
"""Host checks using actual Foldegram drop/policy method bodies with small Java doubles.

These verify classification, protection gates, composition preservation, and cancellation;
Android's native drag delivery/URI grants/PhotoViewer still require device tests.
"""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
CHAT = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java").read_text()
DROP = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/FoldegramChatDragDrop.java").read_text()


def method(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


HARNESS = r'''
import java.util.ArrayList;

public class DropPolicyRegressionTest {
    static final int MAX_TEXT_LENGTH = 16384;
    static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024;
    static final long MAX_IMAGE_PIXELS = 32L * 1024 * 1024;
    __CLASSIFIER_METHODS__

    static class Build { static class VERSION { static int SDK_INT = 36; } }
    static class SharedConfig { static boolean appLocked, isWaitingForPasscodeEnter; }
    static class View {
        static final int VISIBLE = 0;
        boolean attached = true, shown = true;
        int visibility, windowVisibility;
        boolean isAttachedToWindow() { return attached; }
        boolean isShown() { return shown; }
        int getVisibility() { return visibility; }
        int getWindowVisibility() { return windowVisibility; }
    }
    static class Activity {
        boolean finishing, destroyed;
        boolean isFinishing() { return finishing; }
        boolean isDestroyed() { return destroyed; }
    }
    static class Layout {
        Object top;
        Object getLastFragment() { return top; }
    }
    static class Message {
        Object action;
        boolean noforwards;
    }
    static class MessageObject {
        static final int TYPE_TEXT = 0, TYPE_PHOTO = 1;
        Message messageOwner = new Message();
        int currentAccount = 1, id = 10, type;
        long dialog = 2, group;
        boolean sent = true, editing, canForward = true, sponsored, ephemeral, secretMedia, blurred, sensitive, paidProtected;
        int getId() { return id; }
        long getDialogId() { return dialog; }
        long getGroupId() { return group; }
        boolean isSent() { return sent; }
        boolean isEditing() { return editing; }
        boolean canForwardMessage() { return canForward; }
        boolean isSponsored() { return sponsored; }
        boolean isEphemeral() { return ephemeral; }
        boolean isSecretMedia() { return secretMedia; }
        boolean needDrawBluredPreview() { return blurred; }
        boolean isHiddenSensitive() { return sensitive; }
        boolean isPaidSuggestedPostProtected() { return paidProtected; }
    }
    static class Controller {
        boolean frozen, sourceProtected;
        boolean isFrozen() { return frozen; }
        boolean isPeerNoForwards(long dialog) { return sourceProtected; }
    }
    static class Chat { boolean left, writable = true, photo = true, plain = true, manageTopic; }
    static class Topic { boolean closed; }
    static class User { boolean deleted, replyUser; }
    static class UserObject {
        static final long VERIFY = 777000;
        static boolean isDeleted(User user) { return user.deleted; }
        static boolean isReplyUser(User user) { return user.replyUser; }
    }
    static class ChatObject {
        static boolean isNotInChat(Chat chat) { return chat.left; }
        static boolean canWriteToChat(Chat chat) { return chat.writable; }
        static boolean canSendPhoto(Chat chat) { return chat.photo; }
        static boolean canSendPlain(Chat chat) { return chat.plain; }
        static boolean canManageTopic(int account, Chat chat, Topic topic) { return chat != null && chat.manageTopic; }
    }
    static class Composer extends View {
        boolean editing, audio, recording;
        CharSequence text = "";
        boolean isEditingMessage() { return editing; }
        boolean hasAudioToSend() { return audio; }
        boolean isRecordingAudioVideo() { return recording; }
        CharSequence getFieldText() { return text; }
        void setFieldText(CharSequence value) { text = value; }
        void setFieldFocused() {}
    }
    static class Attachment { boolean showing; boolean isShowing() { return showing; } }
    static class SpannableStringBuilder implements CharSequence {
        final StringBuilder text = new StringBuilder();
        SpannableStringBuilder append(CharSequence value) { text.append(value); return this; }
        public int length() { return text.length(); }
        public char charAt(int index) { return text.charAt(index); }
        public CharSequence subSequence(int start, int end) { return text.subSequence(start, end); }
        public String toString() { return text.toString(); }
    }
    static class ChatActivity {
        static final int MODE_DEFAULT = 0;
        int chatMode, currentAccount = 1, fieldPanelShown, forwardPreviews;
        long dialog_id = 1;
        boolean isFinished, inPreviewMode, isInsideContainer, forceNoBottom, userBlocked, paused, secret, report, peerProtected;
        Object editingMessageObject, messageSuggestionParams;
        Activity activity = new Activity();
        Layout parentLayout = new Layout();
        View fragmentView = new View();
        Composer chatActivityEnterView = new Composer();
        Attachment chatAttachAlert;
        Chat currentChat;
        Topic forumTopic;
        User currentUser;
        Controller controller = new Controller();
        ChatActivity() { parentLayout.top = this; }
        Activity getParentActivity() { return activity; }
        boolean isSecretChat() { return secret; }
        boolean isReport() { return report; }
        boolean isPeerNoForwards() { return peerProtected; }
        Controller getMessagesController() { return controller; }
        void showFoldegramDropNotice(String text) {}
        void showFieldPanelForForward(boolean show, ArrayList<MessageObject> messages) { forwardPreviews++; }
        __CHAT_METHODS__
    }
    static class Import { boolean cancelled; void cancel() { cancelled = true; } }
    static class ImportOwner {
        Import imageImport;
        __CANCEL_IMPORT__
    }
    static void check(boolean value, String why) { if (!value) throw new AssertionError(why); }
    static void resetGlobals() { Build.VERSION.SDK_INT = 36; SharedConfig.appLocked = false; SharedConfig.isWaitingForPasscodeEnter = false; }
    static void classifier() {
        check(isSingleItem(1) && !isSingleItem(0) && !isSingleItem(2), "single item only");
        check(isSupportedTextItem(true, "hello", null), "plain text");
        check(isSupportedTextItem(true, "https://example.org", "https"), "text URL");
        check(!isSupportedTextItem(false, "hello", null), "HTML without plain text");
        check(!isSupportedTextItem(true, null, null) && !isSupportedTextItem(true, "", null), "empty text");
        check(!isSupportedTextItem(true, "secret", "file") && !isSupportedTextItem(true, "secret", "content"), "URI cannot coerce to text");
        check(!isSupportedTextItem(true, "run", "intent"), "intent scheme");
        check(isSupportedTextItem(true, new String(new char[MAX_TEXT_LENGTH]), null), "text boundary");
        check(!isSupportedTextItem(true, new String(new char[MAX_TEXT_LENGTH + 1]), null), "text limit");
        check(isSupportedImage("image/jpeg") && isSupportedImage("image/png") && isSupportedImage("image/webp"), "supported photos");
        check(!isSupportedImage("image/svg+xml") && !isSupportedImage("image/gif") && !isSupportedImage("image/*") && !isSupportedImage(null), "narrow MIME allowlist");
        check(isWithinImageCopyLimit(MAX_IMAGE_BYTES, 30000, false), "image byte/time boundaries");
        check(!isWithinImageCopyLimit(MAX_IMAGE_BYTES + 1, 0, false), "image byte cap");
        check(!isWithinImageCopyLimit(1, 30001, false), "image timeout");
        check(!isWithinImageCopyLimit(1, 0, true), "image cancellation");
        check(isSupportedImageDimensions(4096, 4096), "normal image dimensions");
        check(!isSupportedImageDimensions(0, 10) && !isSupportedImageDimensions(-1, 10), "invalid dimensions");
        check(!isSupportedImageDimensions(16385, 1), "single dimension cap");
        check(!isSupportedImageDimensions(16384, 16384), "pixel cap with long multiplication");
    }
    static void sourceProtection() {
        resetGlobals(); ChatActivity chat = new ChatActivity(); MessageObject message = new MessageObject();
        check(chat.canDragFoldegramMessage(message), "ordinary sent text");
        message.type = MessageObject.TYPE_PHOTO; check(chat.canDragFoldegramMessage(message), "ordinary photo");
        message.messageOwner.noforwards = true; check(!chat.canDragFoldegramMessage(message), "per-message protection"); message.messageOwner.noforwards = false;
        chat.peerProtected = true; check(!chat.canDragFoldegramMessage(message), "source chat protection"); chat.peerProtected = false;
        chat.controller.sourceProtected = true; check(!chat.canDragFoldegramMessage(message), "live source permission recheck"); chat.controller.sourceProtected = false;
        chat.secret = true; check(!chat.canDragFoldegramMessage(message), "secret source"); chat.secret = false;
        message.secretMedia = true; check(!chat.canDragFoldegramMessage(message), "secret media"); message.secretMedia = false;
        message.group = 9; check(!chat.canDragFoldegramMessage(message), "albums excluded"); message.group = 0;
        message.sensitive = true; check(!chat.canDragFoldegramMessage(message), "sensitive media"); message.sensitive = false;
        message.type = 3; check(!chat.canDragFoldegramMessage(message), "unsupported message type"); message.type = 0;
        message.id = -1; check(!chat.canDragFoldegramMessage(message), "unsent message"); message.id = 10;
        message.currentAccount = 2; check(!chat.canDragFoldegramMessage(message), "source account mismatch"); message.currentAccount = 1;
        SharedConfig.appLocked = true; check(!chat.canDragFoldegramMessage(message), "locked source"); resetGlobals();
        SharedConfig.isWaitingForPasscodeEnter = true; check(!chat.canDragFoldegramMessage(message), "passcode pending source"); resetGlobals();
        Build.VERSION.SDK_INT = 23; check(!chat.canDragFoldegramMessage(message), "pre-N source"); resetGlobals();
    }
    static void destinationProtection() {
        resetGlobals(); ChatActivity chat = new ChatActivity();
        check(chat.canAcceptFoldegramDrop(true), "visible writable chat");
        chat.paused = true; check(!chat.canAcceptFoldegramDrop(false), "paused target");
        check(chat.canReceiveFoldegramDrag(false), "inactive visible pane receives start without staging"); chat.paused = false;
        chat.parentLayout.top = new Object(); check(!chat.canAcceptFoldegramDrop(false), "covered target"); chat.parentLayout.top = chat;
        chat.fragmentView.shown = false; check(!chat.canAcceptFoldegramDrop(false), "hidden pane"); chat.fragmentView.shown = true;
        chat.fragmentView.windowVisibility = 8; check(!chat.canAcceptFoldegramDrop(false), "hidden window"); chat.fragmentView.windowVisibility = 0;
        chat.secret = true; check(!chat.canAcceptFoldegramDrop(false), "secret target"); chat.secret = false;
        SharedConfig.appLocked = true; check(!chat.canAcceptFoldegramDrop(true), "app locked target"); resetGlobals();
        SharedConfig.isWaitingForPasscodeEnter = true; check(!chat.canAcceptFoldegramDrop(true), "passcode pending target"); resetGlobals();
        chat.activity.destroyed = true; check(!chat.canAcceptFoldegramDrop(false), "destroyed Activity"); chat.activity.destroyed = false;
        chat.currentChat = new Chat(); chat.currentChat.photo = false;
        check(!chat.canAcceptFoldegramDrop(true) && chat.canAcceptFoldegramDrop(false), "media permission differs from text");
        chat.currentChat.writable = false; check(!chat.canAcceptFoldegramDrop(false), "readonly target"); chat.currentChat.writable = true;
        chat.forumTopic = new Topic(); chat.forumTopic.closed = true; check(!chat.canAcceptFoldegramDrop(false), "closed topic");
        chat.currentChat.manageTopic = true; check(chat.canAcceptFoldegramDrop(false), "topic manager");
    }
    static void draftAndPreview() {
        resetGlobals(); ChatActivity chat = new ChatActivity();
        chat.chatActivityEnterView.text = "original";
        check(chat.stageFoldegramText("dropped", 100), "text stage");
        check("original\ndropped".contentEquals(chat.chatActivityEnterView.text), "draft append preservation");
        check(!chat.stageFoldegramText("too long", 3), "combined text length rejected");
        check("original\ndropped".contentEquals(chat.chatActivityEnterView.text), "long text leaves draft unchanged");
        chat.chatActivityEnterView.editing = true; check(!chat.stageFoldegramText("bad", 100), "editing blocks insertion"); chat.chatActivityEnterView.editing = false;
        chat.chatActivityEnterView.recording = true; check(!chat.stageFoldegramText("bad", 100), "recording blocks insertion"); chat.chatActivityEnterView.recording = false;
        MessageObject message = new MessageObject();
        check(chat.stageFoldegramForward(message), "forward stages preview");
        check(chat.forwardPreviews == 1 && "original\ndropped".contentEquals(chat.chatActivityEnterView.text), "forward preserves draft");
        chat.fieldPanelShown = 3; check(!chat.stageFoldegramForward(message), "existing forward preview never replaced"); chat.fieldPanelShown = 0;
        message.currentAccount = 2; check(!chat.stageFoldegramForward(message), "cross-account forward blocked"); message.currentAccount = 1;
        message.dialog = chat.dialog_id; check(!chat.stageFoldegramForward(message), "same dialog forward blocked"); message.dialog = 2;
        chat.currentChat = new Chat(); chat.currentChat.plain = false; check(!chat.stageFoldegramText("bad", 100), "plain text banned");
        check(!chat.stageFoldegramForward(message), "text forward banned");
        ImportOwner imports = new ImportOwner(); Import importing = new Import(); imports.imageImport = importing;
        imports.cancelImport(); check(importing.cancelled && imports.imageImport == null, "pending URI import canceled and detached");
        imports.cancelImport();
    }
    public static void main(String[] args) {
        classifier(); sourceProtection(); destinationProtection(); draftAndPreview();
        System.out.println("PASS: classifier, source/destination guards, drafts/previews, import cancellation");
    }
}
'''


class DropPolicyTest(unittest.TestCase):
    def test_extracted_production_logic(self):
        classifier_names = ["isWithinImageCopyLimit", "isSingleItem", "isSupportedTextItem", "isSupportedImageDimensions", "isSupportedImage"]
        classifier = "\n".join(method(DROP, "private static boolean " + name + "(") for name in classifier_names)
        chat_signatures = [
            "boolean canDragFoldegramMessage(", "boolean canAcceptFoldegramDrop(", "boolean canReceiveFoldegramDrag(",
            "private boolean hasFoldegramCompositionConflict(", "boolean canStageFoldegramMedia(",
            "boolean stageFoldegramText(", "boolean stageFoldegramForward(",
        ]
        chat_methods = "\n".join(method(CHAT, signature) for signature in chat_signatures)
        java = HARNESS.replace("__CLASSIFIER_METHODS__", classifier).replace("__CHAT_METHODS__", chat_methods).replace("__CANCEL_IMPORT__", method(DROP, "void cancelImport("))
        javac = shutil.which("javac")
        if not javac:
            self.fail("JDK required: put javac on PATH")
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "DropPolicyRegressionTest.java"
            source.write_text(java)
            subprocess.run([javac, str(source)], check=True, capture_output=True, text=True)
            result = subprocess.run([str(Path(javac).with_name("java")), "-cp", directory, "DropPolicyRegressionTest"], check=True, capture_output=True, text=True)
            self.assertIn("PASS:", result.stdout)

    def test_no_automatic_send_and_lifecycle_hooks(self):
        self.assertNotIn("sendMedia(", DROP)
        self.assertNotIn("sendMessage(", DROP)
        forward = method(CHAT, "boolean stageFoldegramForward(")
        self.assertIn("showFieldPanelForForward(true, messages)", forward)
        self.assertNotIn("forwardMessages(", forward)
        image = method(CHAT, "void stageFoldegramImage(")
        callback = method(image, "public void sendButtonPressed(")
        self.assertEqual(image.count("sendMedia("), 1)
        self.assertIn("sendMedia(", callback)
        self.assertIn("!canAcceptFoldegramDrop(true)", callback)
        self.assertIn("cancelFoldegramPendingDrop();", method(CHAT, "public void onPause("))
        self.assertIn("cancelFoldegramPendingDrop();", method(CHAT, "protected void onVisibilityChanged("))
        self.assertIn("cancelFoldegramPendingDrop();", method(CHAT, "protected void onWindowVisibilityChanged("))
        self.assertIn("foldegramDragDrop.destroy();", method(CHAT, "public void onFragmentDestroy("))
        self.assertIn("permissions.release()", DROP)
        self.assertIn("finally {\n                    AndroidUtilities.cancelRunOnUIThread(timeout);\n                    release();", DROP)
        self.assertIn("provider.applicationInfo.uid == Process.myUid()", DROP)
        self.assertIn("item.getIntent() != null", DROP)
        self.assertIn("UUID.randomUUID()", DROP)
        self.assertNotIn("takePersistableUriPermission", DROP)


if __name__ == "__main__":
    unittest.main()
