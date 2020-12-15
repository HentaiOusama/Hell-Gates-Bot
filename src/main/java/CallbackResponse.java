import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

public class CallbackResponse {
    public String responseMsg, callbackQueryId;
    public int responseMsgId, replier;
    private final Hell_Gates_Bot hell_gates_bot;

    public CallbackResponse(Hell_Gates_Bot hell_gates_bot, Update update) {
        this.hell_gates_bot = hell_gates_bot;
        callbackQueryId = update.getCallbackQuery().getId();
        responseMsgId = update.getCallbackQuery().getMessage().getMessageId();
        responseMsg = update.getCallbackQuery().getData();
        replier = update.getCallbackQuery().getFrom().getId();
    }

    public void sendWrongReplierMessage() {
        AnswerCallbackQuery answerCallbackQuery = new AnswerCallbackQuery();
        answerCallbackQuery.setCallbackQueryId(callbackQueryId);
        answerCallbackQuery.setText("You are not allowed to pick during someone else's turn.");
        try {
            hell_gates_bot.executeAnswerCallbackQuery(answerCallbackQuery);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void responseNotNeeded() {
        AnswerCallbackQuery answerCallbackQuery = new AnswerCallbackQuery();
        answerCallbackQuery.setCallbackQueryId(callbackQueryId);
        answerCallbackQuery.setText("Response time limit expired. Not accepting any inputs.");
        try {
            hell_gates_bot.executeAnswerCallbackQuery(answerCallbackQuery);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
