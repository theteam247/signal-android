package org.thoughtcrime.securesms.conversation;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.BubbleUtil;
import org.thoughtcrime.securesms.util.ConversationUtil;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.List;
import java.util.concurrent.Executor;

class ConversationRepository {

  private final Context  context;
  private final Executor executor;

  ConversationRepository() {
    this.context  = ApplicationDependencies.getApplication();
    this.executor = SignalExecutors.BOUNDED;
  }

  LiveData<ConversationData> getConversationData(long threadId, @NonNull Recipient recipient, int jumpToPosition) {
    MutableLiveData<ConversationData> liveData = new MutableLiveData<>();

    executor.execute(() -> {
      liveData.postValue(getConversationDataInternal(threadId, recipient, jumpToPosition));
    });

    return liveData;
  }

  @WorkerThread
  boolean canShowAsBubble(long threadId) {
    if (Build.VERSION.SDK_INT >= ConversationUtil.CONVERSATION_SUPPORT_VERSION) {
      Recipient recipient = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(threadId);

      return recipient != null && BubbleUtil.canBubble(context, recipient.getId(), threadId);
    } else {
      return false;
    }
  }

  private @NonNull ConversationData getConversationDataInternal(long threadId, @NonNull Recipient conversationRecipient, int jumpToPosition) {
    ThreadDatabase.ConversationMetadata metadata                       = DatabaseFactory.getThreadDatabase(context).getConversationMetadata(threadId);
    int                                 threadSize                     = DatabaseFactory.getMmsSmsDatabase(context).getConversationCount(threadId);
    long                                lastSeen                       = metadata.getLastSeen();
    boolean                             hasSent                        = metadata.hasSent();
    int                                 lastSeenPosition               = 0;
    long                                lastScrolled                   = metadata.getLastScrolled();
    int                                 lastScrolledPosition           = 0;
    boolean                             isMessageRequestAccepted       = RecipientUtil.isMessageRequestAccepted(context, threadId);
    ConversationData.MessageRequestData messageRequestData             = new ConversationData.MessageRequestData(isMessageRequestAccepted);
    boolean                             showUniversalExpireTimerUpdate = false;

    if (lastSeen > 0) {
      lastSeenPosition = DatabaseFactory.getMmsSmsDatabase(context).getMessagePositionOnOrAfterTimestamp(threadId, lastSeen);
    }

    if (lastSeenPosition <= 0) {
      lastSeen = 0;
    }

    if (lastSeen == 0 && lastScrolled > 0) {
      lastScrolledPosition = DatabaseFactory.getMmsSmsDatabase(context).getMessagePositionOnOrAfterTimestamp(threadId, lastScrolled);
    }

    if (!isMessageRequestAccepted) {
      boolean isGroup                             = false;
      boolean recipientIsKnownOrHasGroupsInCommon = false;
      if (conversationRecipient.isGroup()) {
        Optional<GroupDatabase.GroupRecord> group = DatabaseFactory.getGroupDatabase(context).getGroup(conversationRecipient.getId());
        if (group.isPresent()) {
          List<Recipient> recipients = Recipient.resolvedList(group.get().getMembers());
          for (Recipient recipient : recipients) {
            if ((recipient.isProfileSharing() || recipient.hasGroupsInCommon()) && !recipient.isSelf()) {
              recipientIsKnownOrHasGroupsInCommon = true;
              break;
            }
          }
        }
        isGroup = true;
      } else if (conversationRecipient.hasGroupsInCommon()) {
        recipientIsKnownOrHasGroupsInCommon = true;
      }
      messageRequestData = new ConversationData.MessageRequestData(isMessageRequestAccepted, recipientIsKnownOrHasGroupsInCommon, isGroup);
    }

    if (SignalStore.settings().getUniversalExpireTimer() != 0 &&
        conversationRecipient.getExpireMessages() == 0 &&
        !conversationRecipient.isGroup() &&
        (threadId == -1 || !DatabaseFactory.getMmsSmsDatabase(context).hasMeaningfulMessage(threadId)))
    {
      showUniversalExpireTimerUpdate = true;
    }

    return new ConversationData(threadId, lastSeen, lastSeenPosition, lastScrolledPosition, hasSent, jumpToPosition, threadSize, messageRequestData, showUniversalExpireTimerUpdate);
  }
}
