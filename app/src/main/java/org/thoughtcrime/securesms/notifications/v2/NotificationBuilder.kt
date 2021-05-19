package org.thoughtcrime.securesms.notifications.v2

import android.annotation.TargetApi
import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.DefaultMessageNotifier
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.ReplyMethod
import org.thoughtcrime.securesms.preferences.widgets.NotificationPrivacyPreference
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientUtil
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.AvatarUtil
import org.thoughtcrime.securesms.util.BubbleUtil
import org.thoughtcrime.securesms.util.ConversationUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import androidx.core.app.Person as PersonCompat

private const val BIG_PICTURE_DIMEN = 500

/**
 * Wraps the compat and OS versions of the Notification builders so we can more easily access native
 * features in newer versions. Also provides some domain specific helpers.
 *
 * Note: All business logic should exist in the base builder or the models that drive the notifications
 * like NotificationConversation and NotificationItemV2.
 */
sealed class NotificationBuilder(protected val context: Context) {

  private val privacy: NotificationPrivacyPreference = SignalStore.settings().messageNotificationsPrivacy
  private val isNotLocked: Boolean = !KeyCachingService.isLocked(context)

  abstract fun setSmallIcon(@DrawableRes drawable: Int)
  abstract fun setColor(@ColorInt color: Int)
  abstract fun setCategory(category: String)
  abstract fun setGroup(group: String)
  abstract fun setGroupAlertBehavior(behavior: Int)
  abstract fun setChannelId(channelId: String)
  abstract fun setContentTitle(contentTitle: CharSequence)
  abstract fun setLargeIcon(largeIcon: Bitmap?)
  abstract fun setContentInfo(contentInfo: String)
  abstract fun setNumber(number: Int)
  abstract fun setContentText(contentText: CharSequence?)
  abstract fun setContentIntent(pendingIntent: PendingIntent?)
  abstract fun setDeleteIntent(deleteIntent: PendingIntent?)
  abstract fun setSortKey(sortKey: String)
  abstract fun setOnlyAlertOnce(onlyAlertOnce: Boolean)
  abstract fun setGroupSummary(isGroupSummary: Boolean)
  abstract fun setSubText(subText: String)
  abstract fun addMarkAsReadActionActual(state: NotificationStateV2)
  abstract fun setPriority(priority: Int)
  abstract fun setAlarms(recipient: Recipient?)
  abstract fun setTicker(ticker: CharSequence?)
  abstract fun addTurnOffJoinedNotificationsAction(pendingIntent: PendingIntent)
  abstract fun setAutoCancel(autoCancel: Boolean)
  abstract fun build(): Notification

  protected abstract fun addPersonActual(recipient: Recipient)
  protected abstract fun setShortcutIdActual(shortcutId: String)
  protected abstract fun setWhen(timestamp: Long)
  protected abstract fun addActions(replyMethod: ReplyMethod, conversation: NotificationConversation)
  protected abstract fun addMessagesActual(conversation: NotificationConversation, includeShortcut: Boolean)
  protected abstract fun addMessagesActual(state: NotificationStateV2)
  protected abstract fun setBubbleMetadataActual(conversation: NotificationConversation, bubbleState: BubbleUtil.BubbleState)
  protected abstract fun setLights(@ColorInt color: Int, onTime: Int, offTime: Int)

  fun addPerson(recipient: Recipient) {
    if (privacy.isDisplayContact) {
      addPersonActual(recipient)
    }
  }

  fun setShortcutId(shortcutId: String) {
    if (privacy.isDisplayContact && isNotLocked) {
      setShortcutIdActual(shortcutId)
    }
  }

  fun setWhen(conversation: NotificationConversation) {
    if (conversation.getWhen() != 0L) {
      setWhen(conversation.getWhen())
    }
  }

  fun setWhen(notificationItem: NotificationItemV2?) {
    if (notificationItem != null && notificationItem.timestamp != 0L) {
      setWhen(notificationItem.timestamp)
    }
  }

  fun addReplyActions(conversation: NotificationConversation) {
    if (privacy.isDisplayMessage && isNotLocked && RecipientUtil.isMessageRequestAccepted(context, conversation.recipient)) {
      addActions(ReplyMethod.forRecipient(context, conversation.recipient), conversation)
    }
  }

  fun addMarkAsReadAction(state: NotificationStateV2) {
    if (privacy.isDisplayMessage && isNotLocked) {
      addMarkAsReadActionActual(state)
    }
  }

  fun addMessages(conversation: NotificationConversation) {
    addMessagesActual(conversation, privacy.isDisplayContact)
  }

  fun addMessages(state: NotificationStateV2) {
    if (privacy.isDisplayNothing) {
      return
    }

    addMessagesActual(state)
  }

  fun setBubbleMetadata(conversation: NotificationConversation, bubbleState: BubbleUtil.BubbleState) {
    if (privacy.isDisplayContact && isNotLocked) {
      setBubbleMetadataActual(conversation, bubbleState)
    }
  }

  fun setSummaryContentText(recipient: Recipient?) {
    if (privacy.isDisplayContact && recipient != null) {
      setContentText(context.getString(R.string.MessageNotifier_most_recent_from_s, recipient.getDisplayName(context)))
    }

    recipient?.notificationChannel?.let { channel -> setChannelId(channel) }
  }

  fun setLights() {
    val ledColor: String = SignalStore.settings().messageLedColor

    if (ledColor != "none") {
      var blinkPattern = SignalStore.settings().messageLedBlinkPattern
      if (blinkPattern == "custom") {
        blinkPattern = TextSecurePreferences.getNotificationLedPatternCustom(context)
      }
      val (onTime: Int, offTime: Int) = blinkPattern.parseBlinkPattern()
      setLights(Color.parseColor(ledColor), onTime, offTime)
    }
  }

  private fun String.parseBlinkPattern(): Pair<Int, Int> {
    return split(",").let { parts -> parts[0].toInt() to parts[1].toInt() }
  }

  companion object {
    fun create(context: Context): NotificationBuilder {
      return if (Build.VERSION.SDK_INT >= 28) {
        NotificationBuilderOS(context)
      } else {
        NotificationBuilderCompat(context)
      }
    }
  }

  /**
   * Notification builder using solely androidx/compat libraries.
   */
  class NotificationBuilderCompat(context: Context) : NotificationBuilder(context) {
    val builder: NotificationCompat.Builder = NotificationCompat.Builder(context, NotificationChannels.getMessagesChannel(context))

    override fun addActions(replyMethod: ReplyMethod, conversation: NotificationConversation) {
      val markAsRead: PendingIntent = conversation.getMarkAsReadIntent(context)
      val markAsReadAction: NotificationCompat.Action = NotificationCompat.Action.Builder(R.drawable.check, context.getString(R.string.MessageNotifier_mark_read), markAsRead)
        .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
        .build()

      val extender: NotificationCompat.WearableExtender = NotificationCompat.WearableExtender()

      builder.addAction(markAsReadAction)
      extender.addAction(markAsReadAction)

      if (conversation.mostRecentNotification.canReply(context)) {
        val quickReply: PendingIntent = conversation.getQuickReplyIntent(context)
        val remoteReply: PendingIntent = conversation.getRemoteReplyIntent(context, replyMethod)

        val actionName: String = context.getString(R.string.MessageNotifier_reply)
        val label: String = context.getString(replyMethod.toLongDescription())
        val replyAction: NotificationCompat.Action = if (Build.VERSION.SDK_INT >= 24) {
          NotificationCompat.Action.Builder(R.drawable.ic_reply_white_36dp, actionName, remoteReply)
            .addRemoteInput(RemoteInput.Builder(DefaultMessageNotifier.EXTRA_REMOTE_REPLY).setLabel(label).build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()
        } else {
          NotificationCompat.Action(R.drawable.ic_reply_white_36dp, actionName, quickReply)
        }

        val wearableReplyAction = NotificationCompat.Action.Builder(R.drawable.ic_reply, actionName, remoteReply)
          .addRemoteInput(RemoteInput.Builder(DefaultMessageNotifier.EXTRA_REMOTE_REPLY).setLabel(label).build())
          .build()

        builder.addAction(replyAction)
        extender.addAction(wearableReplyAction)
      }

      builder.extend(extender)
    }

    override fun addMarkAsReadActionActual(state: NotificationStateV2) {
      val markAllAsReadAction = NotificationCompat.Action(R.drawable.check, context.getString(R.string.MessageNotifier_mark_all_as_read), state.getMarkAsReadIntent(context))
      builder.addAction(markAllAsReadAction)
      builder.extend(NotificationCompat.WearableExtender().addAction(markAllAsReadAction))
    }

    override fun addTurnOffJoinedNotificationsAction(pendingIntent: PendingIntent) {
      val turnOffTheseNotifications = NotificationCompat.Action(
        R.drawable.check,
        context.getString(R.string.MessageNotifier_turn_off_these_notifications),
        pendingIntent
      )

      builder.addAction(turnOffTheseNotifications)
    }

    override fun addMessagesActual(conversation: NotificationConversation, includeShortcut: Boolean) {
      val bigPictureUri: Uri? = conversation.getSlideBigPictureUri(context)
      if (bigPictureUri != null) {
        builder.setStyle(
          NotificationCompat.BigPictureStyle()
            .bigPicture(bigPictureUri.toBitmap(context, BIG_PICTURE_DIMEN))
            .setSummaryText(conversation.getContentText(context))
            .bigLargeIcon(null)
        )
        return
      }

      val self: PersonCompat = PersonCompat.Builder()
        .setBot(false)
        .setName(if (includeShortcut) Recipient.self().getDisplayName(context) else context.getString(R.string.SingleRecipientNotificationBuilder_you))
        .setIcon(if (includeShortcut) Recipient.self().getContactDrawable(context).toLargeBitmap(context).toIconCompat() else null)
        .build()

      val messagingStyle: NotificationCompat.MessagingStyle = NotificationCompat.MessagingStyle(self)
      messagingStyle.conversationTitle = conversation.getConversationTitle(context)
      messagingStyle.isGroupConversation = conversation.isGroup

      conversation.notificationItems.forEach { notificationItem ->
        val personBuilder: PersonCompat.Builder = PersonCompat.Builder()
          .setBot(false)
          .setName(notificationItem.getPersonName(context))
          .setUri(notificationItem.getPersonUri(context))
          .setIcon(notificationItem.getPersonIcon(context).toIconCompat())

        if (includeShortcut) {
          personBuilder.setKey(ConversationUtil.getShortcutId(notificationItem.individualRecipient))
        }

        val (dataUri: Uri?, mimeType: String?) = notificationItem.getThumbnailInfo(context)

        messagingStyle.addMessage(NotificationCompat.MessagingStyle.Message(notificationItem.getPrimaryText(context), notificationItem.timestamp, personBuilder.build()).setData(mimeType, dataUri))
      }

      builder.setStyle(messagingStyle)
    }

    override fun addMessagesActual(state: NotificationStateV2) {
      if (Build.VERSION.SDK_INT >= 24) {
        return
      }

      val style: NotificationCompat.InboxStyle = NotificationCompat.InboxStyle()

      for (notificationItem: NotificationItemV2 in state.notificationItems) {
        val line: CharSequence? = notificationItem.getInboxLine(context)
        if (line != null) {
          style.addLine(line)
        }
        addPerson(notificationItem.individualRecipient)
      }

      builder.setStyle(style)
    }

    override fun setAlarms(recipient: Recipient?) {
      if (NotificationChannels.supported()) {
        return
      }

      val ringtone: Uri? = recipient?.messageRingtone
      val vibrate = recipient?.messageVibrate

      val defaultRingtone: Uri = SignalStore.settings().messageNotificationSound
      val defaultVibrate: Boolean = SignalStore.settings().isMessageVibrateEnabled

      if (ringtone == null && !TextUtils.isEmpty(defaultRingtone.toString())) {
        builder.setSound(defaultRingtone)
      } else if (ringtone != null && ringtone.toString().isNotEmpty()) {
        builder.setSound(ringtone)
      }

      if (vibrate == RecipientDatabase.VibrateState.ENABLED || vibrate == RecipientDatabase.VibrateState.DEFAULT && defaultVibrate) {
        builder.setDefaults(Notification.DEFAULT_VIBRATE)
      }
    }

    override fun setBubbleMetadataActual(conversation: NotificationConversation, bubbleState: BubbleUtil.BubbleState) {
      // Intentionally left blank
    }

    override fun setLights(@ColorInt color: Int, onTime: Int, offTime: Int) {
      builder.setLights(color, onTime, offTime)
    }

    override fun setSmallIcon(drawable: Int) {
      builder.setSmallIcon(drawable)
    }

    override fun setColor(@ColorInt color: Int) {
      builder.color = color
    }

    override fun setCategory(category: String) {
      builder.setCategory(category)
    }

    override fun setGroup(group: String) {
      if (Build.VERSION.SDK_INT < 24) {
        return
      }

      builder.setGroup(group)
    }

    override fun setGroupAlertBehavior(behavior: Int) {
      if (Build.VERSION.SDK_INT < 24) {
        return
      }

      builder.setGroupAlertBehavior(behavior)
    }

    override fun setChannelId(channelId: String) {
      builder.setChannelId(channelId)
    }

    override fun setContentTitle(contentTitle: CharSequence) {
      builder.setContentTitle(contentTitle)
    }

    override fun setLargeIcon(largeIcon: Bitmap?) {
      builder.setLargeIcon(largeIcon)
    }

    override fun setShortcutIdActual(shortcutId: String) {
      builder.setShortcutId(shortcutId)
    }

    override fun setContentInfo(contentInfo: String) {
      builder.setContentInfo(contentInfo)
    }

    override fun setNumber(number: Int) {
      builder.setNumber(number)
    }

    override fun setContentText(contentText: CharSequence?) {
      builder.setContentText(contentText)
    }

    override fun setTicker(ticker: CharSequence?) {
      builder.setTicker(ticker)
    }

    override fun setContentIntent(pendingIntent: PendingIntent?) {
      builder.setContentIntent(pendingIntent)
    }

    override fun setDeleteIntent(deleteIntent: PendingIntent?) {
      builder.setDeleteIntent(deleteIntent)
    }

    override fun setSortKey(sortKey: String) {
      builder.setSortKey(sortKey)
    }

    override fun setOnlyAlertOnce(onlyAlertOnce: Boolean) {
      builder.setOnlyAlertOnce(onlyAlertOnce)
    }

    override fun setPriority(priority: Int) {
      if (!NotificationChannels.supported()) {
        builder.priority = priority
      }
    }

    override fun setAutoCancel(autoCancel: Boolean) {
      builder.setAutoCancel(autoCancel)
    }

    override fun build(): Notification {
      return builder.build()
    }

    override fun addPersonActual(recipient: Recipient) {
      builder.addPerson(recipient.contactUri.toString())
    }

    override fun setWhen(timestamp: Long) {
      builder.setWhen(timestamp)
      builder.setShowWhen(true)
    }

    override fun setGroupSummary(isGroupSummary: Boolean) {
      builder.setGroupSummary(isGroupSummary)
    }

    override fun setSubText(subText: String) {
      builder.setSubText(subText)
    }
  }

  /**
   * Notification builder using solely on device OS libraries.
   */
  @TargetApi(28)
  class NotificationBuilderOS(context: Context) : NotificationBuilder(context) {
    val builder: Notification.Builder = Notification.Builder(context, NotificationChannels.getMessagesChannel(context))

    override fun addActions(replyMethod: ReplyMethod, conversation: NotificationConversation) {
      val markAsRead: PendingIntent = conversation.getMarkAsReadIntent(context)
      val markAsReadAction: Notification.Action = Notification.Action.Builder(context.getIcon(R.drawable.check), context.getString(R.string.MessageNotifier_mark_read), markAsRead)
        .setSemanticAction(Notification.Action.SEMANTIC_ACTION_MARK_AS_READ)
        .build()
      val extender: Notification.WearableExtender = Notification.WearableExtender()

      builder.addAction(markAsReadAction)
      extender.addAction(markAsReadAction)

      if (conversation.mostRecentNotification.canReply(context)) {
        val remoteReply: PendingIntent = conversation.getRemoteReplyIntent(context, replyMethod)

        val actionName: String = context.getString(R.string.MessageNotifier_reply)
        val label: String = context.getString(replyMethod.toLongDescription())
        val replyAction: Notification.Action = Notification.Action.Builder(context.getIcon(R.drawable.ic_reply_white_36dp), actionName, remoteReply)
          .addRemoteInput(android.app.RemoteInput.Builder(DefaultMessageNotifier.EXTRA_REMOTE_REPLY).setLabel(label).build())
          .setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
          .build()

        val wearableReplyAction = Notification.Action.Builder(context.getIcon(R.drawable.ic_reply), actionName, remoteReply)
          .addRemoteInput(android.app.RemoteInput.Builder(DefaultMessageNotifier.EXTRA_REMOTE_REPLY).setLabel(label).build())
          .build()

        builder.addAction(replyAction)
        extender.addAction(wearableReplyAction)
      }

      builder.extend(extender)
    }

    override fun addMarkAsReadActionActual(state: NotificationStateV2) {
      val markAllAsReadAction: Notification.Action = Notification.Action.Builder(
        context.getIcon(R.drawable.check),
        context.getString(R.string.MessageNotifier_mark_all_as_read),
        state.getMarkAsReadIntent(context)
      ).build()

      builder.addAction(markAllAsReadAction)
      builder.extend(Notification.WearableExtender().addAction(markAllAsReadAction))
    }

    override fun addTurnOffJoinedNotificationsAction(pendingIntent: PendingIntent) {
      val turnOffTheseNotifications: Notification.Action = Notification.Action.Builder(
        context.getIcon(R.drawable.check),
        context.getString(R.string.MessageNotifier_turn_off_these_notifications),
        pendingIntent
      ).build()

      builder.addAction(turnOffTheseNotifications)
    }

    override fun addMessagesActual(conversation: NotificationConversation, includeShortcut: Boolean) {
      val self: Person = Person.Builder()
        .setBot(false)
        .setName(if (includeShortcut) Recipient.self().getDisplayName(context) else context.getString(R.string.SingleRecipientNotificationBuilder_you))
        .setIcon(if (includeShortcut) Recipient.self().getContactDrawable(context).toLargeBitmap(context).toIcon() else null)
        .build()

      val messagingStyle: Notification.MessagingStyle = Notification.MessagingStyle(self)
      messagingStyle.conversationTitle = conversation.getConversationTitle(context)
      messagingStyle.isGroupConversation = conversation.isGroup

      conversation.notificationItems.forEach { notificationItem ->
        val personBuilder: Person.Builder = Person.Builder()
          .setBot(false)
          .setName(notificationItem.getPersonName(context))
          .setUri(notificationItem.getPersonUri(context))
          .setIcon(notificationItem.getPersonIcon(context).toIcon())

        if (includeShortcut) {
          personBuilder.setKey(ConversationUtil.getShortcutId(notificationItem.individualRecipient))
        }

        val (dataUri: Uri?, mimeType: String?) = notificationItem.getThumbnailInfo(context)

        messagingStyle.addMessage(Notification.MessagingStyle.Message(notificationItem.getPrimaryText(context), notificationItem.timestamp, personBuilder.build()).setData(mimeType, dataUri))
      }

      builder.style = messagingStyle
    }

    override fun setBubbleMetadataActual(conversation: NotificationConversation, bubbleState: BubbleUtil.BubbleState) {
      if (Build.VERSION.SDK_INT < ConversationUtil.CONVERSATION_SUPPORT_VERSION) {
        return
      }

      val intent = PendingIntent.getActivity(
        context,
        0,
        ConversationIntents.createBubbleIntent(context, conversation.recipient.id, conversation.threadId),
        0
      )

      val bubbleMetadata = Notification.BubbleMetadata.Builder(intent, AvatarUtil.getIconForShortcut(context, conversation.recipient))
        .setAutoExpandBubble(bubbleState === BubbleUtil.BubbleState.SHOWN)
        .setDesiredHeight(600)
        .setSuppressNotification(bubbleState === BubbleUtil.BubbleState.SHOWN)
        .build()

      builder.setBubbleMetadata(bubbleMetadata)
    }

    override fun addMessagesActual(state: NotificationStateV2) {
      // Intentionally left blank
    }

    override fun setLights(@ColorInt color: Int, onTime: Int, offTime: Int) {
      // Intentionally left blank
    }

    override fun setAlarms(recipient: Recipient?) {
      // Intentionally left blank
    }

    override fun setSmallIcon(drawable: Int) {
      builder.setSmallIcon(drawable)
    }

    override fun setColor(@ColorInt color: Int) {
      builder.setColor(color)
    }

    override fun setCategory(category: String) {
      builder.setCategory(category)
    }

    override fun setGroup(group: String) {
      builder.setGroup(group)
    }

    override fun setGroupAlertBehavior(behavior: Int) {
      builder.setGroupAlertBehavior(behavior)
    }

    override fun setChannelId(channelId: String) {
      builder.setChannelId(channelId)
    }

    override fun setContentTitle(contentTitle: CharSequence) {
      builder.setContentTitle(contentTitle)
    }

    override fun setLargeIcon(largeIcon: Bitmap?) {
      builder.setLargeIcon(largeIcon)
    }

    override fun setShortcutIdActual(shortcutId: String) {
      builder.setShortcutId(shortcutId)
    }

    @Suppress("DEPRECATION")
    override fun setContentInfo(contentInfo: String) {
      builder.setContentInfo(contentInfo)
    }

    override fun setNumber(number: Int) {
      builder.setNumber(number)
    }

    override fun setContentText(contentText: CharSequence?) {
      builder.setContentText(contentText)
    }

    override fun setTicker(ticker: CharSequence?) {
      builder.setTicker(ticker)
    }

    override fun setContentIntent(pendingIntent: PendingIntent?) {
      builder.setContentIntent(pendingIntent)
    }

    override fun setDeleteIntent(deleteIntent: PendingIntent?) {
      builder.setDeleteIntent(deleteIntent)
    }

    override fun setSortKey(sortKey: String) {
      builder.setSortKey(sortKey)
    }

    override fun setOnlyAlertOnce(onlyAlertOnce: Boolean) {
      builder.setOnlyAlertOnce(onlyAlertOnce)
    }

    override fun setPriority(priority: Int) {
      // Intentionally left blank
    }

    override fun setAutoCancel(autoCancel: Boolean) {
      builder.setAutoCancel(autoCancel)
    }

    override fun build(): Notification {
      return builder.build()
    }

    override fun addPersonActual(recipient: Recipient) {
      builder.addPerson(ConversationUtil.buildPerson(context, recipient))
    }

    override fun setWhen(timestamp: Long) {
      builder.setWhen(timestamp)
      builder.setShowWhen(true)
    }

    override fun setGroupSummary(isGroupSummary: Boolean) {
      builder.setGroupSummary(isGroupSummary)
    }

    override fun setSubText(subText: String) {
      builder.setSubText(subText)
    }
  }
}

private fun Bitmap?.toIconCompat(): IconCompat? {
  return if (this != null) {
    IconCompat.createWithBitmap(this)
  } else {
    null
  }
}

@RequiresApi(23)
private fun Bitmap?.toIcon(): Icon? {
  return if (this != null) {
    Icon.createWithBitmap(this)
  } else {
    null
  }
}

@RequiresApi(23)
private fun Context.getIcon(@DrawableRes drawableRes: Int): Icon {
  return Icon.createWithResource(this, drawableRes)
}

@StringRes
private fun ReplyMethod.toLongDescription(): Int {
  return when (this) {
    ReplyMethod.GroupMessage -> R.string.MessageNotifier_reply
    ReplyMethod.SecureMessage -> R.string.MessageNotifier_signal_message
    ReplyMethod.UnsecuredSmsMessage -> R.string.MessageNotifier_unsecured_sms
  }
}
