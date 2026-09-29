const {onSchedule} = require("firebase-functions/v2/scheduler");
const {onDocumentCreated} = require("firebase-functions/v2/firestore");
const {initializeApp} = require("firebase-admin/app");
const {getFirestore, Timestamp} = require("firebase-admin/firestore");

initializeApp();

exports.cleanupExpiredStatuses = onSchedule("every 60 minutes", async () => {
  const db = getFirestore();
  const now = Timestamp.now();
  const snap = await db.collection("statuses").where("expiresAt", "<=", now).limit(500).get();
  if (snap.empty) return null;
  const batch = db.batch();
  snap.docs.forEach(doc => batch.delete(doc.ref));
  await batch.commit();
  return null;
});


exports.pushNewMessage = onDocumentCreated("conversations/{conversationId}/messages/{messageId}", async (event) => {
  const message = event.data?.data();
  if (!message?.senderId) return null;
  const db = getFirestore();
  const conversationId = event.params.conversationId;
  const convSnap = await db.doc(`conversations/${conversationId}`).get();
  if (!convSnap.exists) return null;
  const members = convSnap.data()?.members || [];
  const recipientIds = members.filter(uid => uid && uid !== message.senderId);
  if (!recipientIds.length) return null;
  const users = await Promise.all(recipientIds.map(uid => db.doc(`users/${uid}`).get()));
  const tokens = users.map(s => s.data()?.nativeFcmToken).filter(Boolean);
  if (!tokens.length) return null;
  const sender = await db.doc(`users/${message.senderId}`).get();
  const senderName = sender.data()?.displayName || "Mercer Messenger";
  let body = message.type === "image" ? "📷 Photo" : message.type === "video" ? "🎥 Video" : message.type === "audio" ? "🎤 Voice message" : message.type === "file" ? "📎 File" : (message.text || "New message");
  if (body.length > 160) body = body.slice(0,157) + "…";
  await require("firebase-admin/messaging").getMessaging().sendEachForMulticast({
    tokens, notification: {title: senderName, body}, data: {conversationId, messageId: event.params.messageId, senderId: message.senderId}, android: {notification: {channelId: "mercer_messages"}}
  });
  return null;
});
