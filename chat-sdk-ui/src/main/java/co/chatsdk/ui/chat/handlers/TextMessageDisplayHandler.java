package co.chatsdk.ui.chat.handlers;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import java.util.List;

import co.chatsdk.core.base.AbstractMessageViewHolder;
import co.chatsdk.core.dao.Message;
import co.chatsdk.core.message_action.MessageAction;
import co.chatsdk.core.types.MessageType;
import co.chatsdk.ui.chat.viewholder.TextMessageViewHolder;
import io.reactivex.subjects.PublishSubject;

public class TextMessageDisplayHandler extends AbstractMessageDisplayHandler {

    @Override
    public void updateMessageCellView(Message message, AbstractMessageViewHolder viewHolder, Context context) {

    }

    @Override
    public String displayName(Message message) {
        return message.getText();
    }

    @Override
    public AbstractMessageViewHolder newViewHolder(boolean isReply, Activity activity, PublishSubject<List<MessageAction>> actionPublishSubject) {
        View row = row(isReply, activity);
        return new TextMessageViewHolder(row, activity, actionPublishSubject);
    }
}
