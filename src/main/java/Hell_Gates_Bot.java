import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.web3j.crypto.WalletUtils;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Hell_Gates_Bot extends org.telegram.telegrambots.bots.TelegramLongPollingBot {

    public HashMap<Long, Game> currentlyActiveGames = new HashMap<>();
    int minimumNumberOfPlayers;
    private String EthNetworkType;
    final String ourWallet, shotWallet;
    String RTKContractAddress;
    final BigInteger joinCost, shotCost;

    // Mongo Stuff
    final String mongoDBUri;
    final String databaseName = "Hell-Gates-Bot-Database";
    final String botControlDatabaseName = "All-Bots-Command-Centre";
    final String botName = "Hell Gates Bot";
    final String idKey = "UserID";
    final String addyKey = "EthAddy";
    String ticketKey;
    MongoClient mongoClient;
    MongoDatabase mongoDatabase, botControlDatabase;
    MongoCollection userDataCollection, botControlCollection;
    Document botNameDoc, foundBotNameDoc;

    // Game User Status
    final ArrayList<Integer> playersPlayingAGame = new ArrayList<>();
    final ArrayList<Integer> playersBuyingTickets = new ArrayList<>();
    final HashMap<Long, TicketBuyer> ticketBuyers = new HashMap<>();
    boolean shouldRunGame;
    long testingChatId = -1001477389485L;
    boolean waitingToSwitchServers = false;

    // Constructor
    public Hell_Gates_Bot(String EthNetworkType, String ourWallet, String shotWallet, String RTKContractAddress, BigInteger joinCost,
                          BigInteger shotCost, int minimumNumberOfPlayers) {
        this.EthNetworkType = EthNetworkType;
        ticketKey = EthNetworkType + "Tickets";
        this.ourWallet = ourWallet;
        this.shotWallet = shotWallet;
        this.RTKContractAddress = RTKContractAddress;
        this.joinCost = joinCost;
        this.shotCost = shotCost;
        this.minimumNumberOfPlayers = minimumNumberOfPlayers;
        mongoDBUri = "mongodb+srv://" + System.getenv("hellGatesMonoID") + ":" +
                System.getenv("hellGatesMonoPass") + "@hellgatesbotcluster.zm0r5.mongodb.net/test";
        MongoClientURI mongoClientURI = new MongoClientURI(mongoDBUri);
        mongoClient = new MongoClient(mongoClientURI);
        mongoDatabase = mongoClient.getDatabase(databaseName);
        botControlDatabase = mongoClient.getDatabase(botControlDatabaseName);
        userDataCollection = mongoDatabase.getCollection("UserAddyAndTickets");
        botControlCollection = botControlDatabase.getCollection("MemberValues");
        botNameDoc = new Document("botName", botName);
        foundBotNameDoc = (Document) botControlCollection.find(botNameDoc).first();
        assert foundBotNameDoc != null;
        shouldRunGame = (boolean) foundBotNameDoc.get("shouldRunGame");
    }


    @Override
    public void onUpdateReceived(Update update) {

        if(update.hasMessage()) {
            if(update.getMessage().getChatId() == getAdminChatId()) {
                if(update.getMessage().hasText()) {
                    String text = update.getMessage().getText();
                    if(!shouldRunGame && text.equalsIgnoreCase("run")) {
                        shouldRunGame = true;
                        botNameDoc = new Document("botName", botName);
                        foundBotNameDoc = (Document) botControlCollection.find(botNameDoc).first();
                        Bson updatedAddyDoc = new Document("shouldRunGame", shouldRunGame);
                        Bson updateAddyDocOperation = new Document("$set", updatedAddyDoc);
                        botControlCollection.updateOne(foundBotNameDoc, updateAddyDocOperation);
                    } else if(text.equalsIgnoreCase("Switch to mainnet")) {
                        EthNetworkType = "mainnet";
                        RTKContractAddress = "0x1F6DEADcb526c4710Cf941872b86dcdfBbBD9211";
                        ticketKey = EthNetworkType + "Tickets";
                    }
                    else if (text.equalsIgnoreCase("Switch to ropsten")) {
                        EthNetworkType = "ropsten";
                        RTKContractAddress = "0xa59b4d4c371a4b7957d7a3bf06c19ec3ac5885f1";
                        ticketKey = EthNetworkType + "Tickets";
                    } else if(text.startsWith("MinPlayers = ")) {
                        try {
                            minimumNumberOfPlayers = Integer.parseInt(text.trim().split(" ")[2]);
                        } catch (Exception e) {
                            sendMessage(update.getMessage().getChatId(), "Invalid number of players");
                        }
                    }
                    else if(shouldRunGame && text.equalsIgnoreCase("stopBot")) {
                        shouldRunGame = false;
                        botNameDoc = new Document("botName", botName);
                        foundBotNameDoc = (Document) botControlCollection.find(botNameDoc).first();
                        Bson updatedAddyDoc = new Document("shouldRunGame", shouldRunGame);
                        Bson updateAddyDocOperation = new Document("$set", updatedAddyDoc);
                        botControlCollection.updateOne(foundBotNameDoc, updateAddyDocOperation);
                    } else if(text.equalsIgnoreCase("StartServerSwitchProcess")) {
                        waitingToSwitchServers = true;
                        sendMessage(getAdminChatId(), "From now, the bot won't accept new games or Ticket buy requests. Please use \"ActiveProcesses\" command " +
                                "to see how many games are active and then switch when there are no games active games and ticket buyers.");
                    }
                    else if(text.equalsIgnoreCase("ActiveProcesses")) {
                        sendMessage(getAdminChatId(), "Active Games : " + currentlyActiveGames.size() + "\nPeople buying tickets : " + playersBuyingTickets.size());
                    } else if(text.equalsIgnoreCase("Commands")) {
                        sendMessage(update.getMessage().getChatId(), "Run\nSwitch to mainnet\nSwitch to ropsten\nMinPlayers = __\nStopBot\n" +
                                "StartServerSwitchProcess\nActiveProcesses\nCommands");
                    }
                    sendMessage(update.getMessage().getChatId(), "shouldRunGame = " + shouldRunGame + "\nEthNetworkType = " + EthNetworkType +
                            "\nRTKContractAddress = " + RTKContractAddress + "\nMinPlayers = " + minimumNumberOfPlayers + "\nticketKey = " + ticketKey + "\n" +
                            "WaitingToSwitchServers = " + waitingToSwitchServers);
                }
                // Can add special operation for admin here
            }
        }
        if(!shouldRunGame) {
            sendMessage(update.getMessage().getChatId(), "Bot under maintenance.. Please try again later.");
            return;
        }

        if(update.hasCallbackQuery()) {
            currentlyActiveGames.get(update.getCallbackQuery().getMessage().getChatId()).acceptNewResponse(new CallbackResponse(this, update));
        } else if(update.hasMessage()) {
            long chat_id = update.getMessage().getChatId();
            int player_id = update.getMessage().getFrom().getId();
            String[] inputMsg = update.getMessage().getText().trim().split(" ");
            if(waitingToSwitchServers && !inputMsg[0].startsWith("/paywithticket")) {
                sendMessage(chat_id, "The bot is not accepting any commands at the moment. The bot will be changing the servers soon. So a buffer time has been " +
                        "provided to complete all active games and Ticket purchases. This won't take much long. Please expect a 15-30 minute delay. This process has to be" +
                        "done after every 15 days.");
                return;
            }
            switch (inputMsg[0]) {

                case "/startgame":
                case "/startgame@Hell_Gates_Bot": {
                    SendMessage sendMessage = new SendMessage();
                    boolean shouldSend = true;
                    if (update.getMessage().getChat().isGroupChat() || update.getMessage().getChat().isSuperGroupChat()) {
                        if (currentlyActiveGames.containsKey(chat_id)) {
                            Game game = currentlyActiveGames.get(chat_id);
                            if(!game.hasGameStarted) {
                                sendMessage.setText("A game is already running. Please wait for current game to end to start a new one or you can join the current game");
                            } else {
                                shouldSend = false;
                            }
                        } else if(playersBuyingTickets.contains(player_id)) {
                            sendMessage.setText("You cannot start or join a game when buying tickets. Please complete your purchase first");
                        } else {
                            if(!(chat_id == -1001474429395L || chat_id == testingChatId)) {
                                sendMessage(chat_id, "This bot cannot be used in this group!! It is built for exclusive groups only.");
                                return;
                            }
                            Document getAddyDoc = new Document(idKey, player_id);
                            Document foundAddyDoc = (Document) userDataCollection.find(getAddyDoc).first();
                            if(foundAddyDoc != null) {
                                Game newGame;
                                try {
                                    sendMessage(chat_id, "Initiating a new Game!!!");
                                    String addy = (String) foundAddyDoc.get(addyKey);
                                    newGame = new Game(this, chat_id, player_id, EthNetworkType, ourWallet,
                                            shotWallet, RTKContractAddress, joinCost, shotCost, minimumNumberOfPlayers);
                                    newGame.addPlayer(update.getMessage().getFrom(), addy);
                                    currentlyActiveGames.put(chat_id, newGame);
                                    SendAnimation sendAnimation = new SendAnimation();
                                    sendAnimation.setAnimation("https://media.giphy.com/media/3ov9jYd9chmwCNQl8c/giphy.gif");
                                    sendAnimation.setCaption("New game has been created. Please gather at least " + minimumNumberOfPlayers + " players within " +
                                            "10 minutes for game to begin. Players can use /join command to join the game. Those who are new to the game can use " +
                                            "/rules@Hell_Gates_Bot in private chat @" + getBotUsername() + " to learn the rules.");
                                    sendAnimation.setChatId(chat_id);
                                    execute(sendAnimation);
                                    shouldSend = false;
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    shouldSend = false;
                                }
                            } else {
                                try {
                                    SendAnimation sendAnimation = new SendAnimation();
                                    sendAnimation.setAnimation("https://media.giphy.com/media/fDvqZGc2D0oXC/giphy.gif");
                                    sendAnimation.setCaption("You haven't registered your ethereum address yet. Please link your ethereum wallet to start or join games.");
                                    sendAnimation.setChatId(chat_id);
                                    execute(sendAnimation);
                                    shouldSend = false;
                                } catch (Exception e) {
                                    sendMessage.setText("You haven't registered your ethereum address yet. Please link your ethereum wallet to start or join games.");
                                }
                            }
                        }
                    } else {
                        sendMessage.setText("This command can only be run in a group!!!");
                    }
                    sendMessage.setChatId(chat_id);
                    if(shouldSend) {
                        try {
                            execute(sendMessage);
                        } catch (TelegramApiException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                }

                case "/join":
                case "/join@Hell_Gates_Bot": {
                    boolean shouldSend = true;
                    SendMessage sendMessage = new SendMessage();
                    if (update.getMessage().getChat().isGroupChat() || update.getMessage().getChat().isSuperGroupChat()) {
                        if (currentlyActiveGames.containsKey(chat_id)) {
                            Game game = currentlyActiveGames.get(chat_id);
                            if(game.hasGameStarted){
                                shouldSend = false;
                            } else {
                                Document getAddyDoc = new Document(idKey, player_id);
                                Document foundAddyDoc = (Document) userDataCollection.find(getAddyDoc).first();
                                if(foundAddyDoc != null) {
                                    if(playersBuyingTickets.contains(player_id)) {
                                        sendMessage.setText("You cannot start or join a game when buying tickets. Please complete your ticket purchase first");
                                    } else if (currentlyActiveGames.containsKey(chat_id)) {
                                        String addy = (String) foundAddyDoc.get(addyKey);
                                        if (game.addPlayer(update.getMessage().getFrom(), addy)) {
                                            sendMessage.setText("You have successfully joined the current game @" + update.getMessage().getFrom().getUserName());
                                            playersPlayingAGame.add(player_id);
                                        } else {
                                            sendMessage.setText("You are already in the game @" + update.getMessage().getFrom().getUserName());
                                        }
                                    } else {
                                        sendMessage.setText("No Games Active. Start a new one to join");
                                    }
                                } else {
                                    try {
                                        SendAnimation sendAnimation = new SendAnimation();
                                        sendAnimation.setAnimation("https://media.giphy.com/media/fDvqZGc2D0oXC/giphy.gif");
                                        sendAnimation.setCaption("You haven't registered your ethereum address yet. Please link your ethereum wallet to start or join games.");
                                        sendAnimation.setChatId(chat_id);
                                        execute(sendAnimation);
                                        shouldSend = false;
                                    } catch (Exception e) {
                                        sendMessage.setText("You haven't registered your ethereum address yet. Please link your ethereum wallet to start or join games.");
                                    }
                                }
                            }
                        } else {
                            sendMessage.setText("No Games Active. Start a new one to join");
                        }
                    } else {
                        sendMessage.setText("This command can only be run in a group!!!");
                    }
                    if(shouldSend) {
                        sendMessage.setChatId(chat_id);
                        try {
                            execute(sendMessage);
                        } catch (TelegramApiException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                }

                case "/begin":
                case "/begin@Hell_Gates_Bot": {
                    boolean shouldSend = true;
                    SendMessage sendMessage = new SendMessage();
                    if (update.getMessage().getChat().isGroupChat() || update.getMessage().getChat().isSuperGroupChat()) {
                        if (currentlyActiveGames.containsKey(chat_id)) {
                            Game game = currentlyActiveGames.get(chat_id);
                            if(game.hasGameStarted){
                                shouldSend = false;
                            } else {
                                if (game.getGameInitiator() == player_id) {
                                    if(game.beginGame()) {
                                        shouldSend = false;
                                    } else {
                                        sendMessage.setText("Cannot begin the game. Not Enough Players!\nCurrent Number of Players : " + game.numberOfPlayers);
                                    }
                                } else {
                                    try {
                                        SendAnimation sendAnimation = new SendAnimation();
                                        sendAnimation.setAnimation("https://media.giphy.com/media/Lr9Y5rWFIpcsTSodLj/giphy.gif");
                                        sendAnimation.setCaption("/begin command can only be used by the person who initiated the game or the game automatically " +
                                                "start after the join time ends and minimum number of players are found");
                                        sendAnimation.setChatId(chat_id);
                                        execute(sendAnimation);
                                        shouldSend = false;
                                    } catch (Exception e) {
                                        sendMessage.setText("/begin command can only be used by the person who initiated the game or the game automatically " +
                                                "start after the join time ends and minimum number of players are found");
                                    }
                                }
                            }
                        } else {
                            sendMessage.setText("No Games Active. Although you CAN use /Start command to start a new game XD");
                        }
                    } else {
                        sendMessage.setText("This command can only be run in a group!!!");
                    }
                    sendMessage.setChatId(chat_id);
                    if (shouldSend) {
                        try {
                            execute(sendMessage);
                        } catch (TelegramApiException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                }

                case "/register":
                case "/register@Hell_Gates_Bot": {
                    if(currentlyActiveGames.containsKey(chat_id)) {
                        Game game = currentlyActiveGames.get(chat_id);
                        if(game.hasGameStarted) {
                            return;
                        }
                    }
                    SendMessage sendMessage = new SendMessage();
                    if(!update.getMessage().getChat().isUserChat()) {
                        sendMessage.setText("Please use /register command in private chat @" + getBotUsername());
                    } else {
                        if(inputMsg.length != 2) {
                            sendMessage.setText("Proper format to use the register command is :\n/register ethereum_address");
                        } else {
                            if(!playersBuyingTickets.contains(player_id)) {
                                // Check if input wallet address is valid
                                boolean isValid = WalletUtils.isValidAddress(inputMsg[1]);
                                if(isValid) {
                                    Document addyDoc = new Document(addyKey, inputMsg[1]);
                                    Document foundAddyDoc = (Document) userDataCollection.find(addyDoc).first();
                                    if(foundAddyDoc != null) {
                                        sendMessage.setText("Cannot register with this wallet. This address has already been registered by you or by " +
                                                "someone else. Please use /wallet command to check your current wallet address or use another wallet.");
                                    } else {
                                        addyDoc = new Document(idKey, player_id);
                                        foundAddyDoc = (Document) userDataCollection.find(addyDoc).first();
                                        if(foundAddyDoc == null) {
                                            addyDoc = new Document(idKey, player_id);
                                            addyDoc.append(addyKey, inputMsg[1]);
                                            addyDoc.append("ropstenTickets", 0);
                                            addyDoc.append("mainnetTickets", 0);
                                            userDataCollection.insertOne(addyDoc);
                                        } else {
                                            Bson updatedAddyDoc = new Document(addyKey, inputMsg[1]);
                                            Bson updateAddyDocOperation = new Document("$set", updatedAddyDoc);
                                            userDataCollection.updateOne(foundAddyDoc, updateAddyDocOperation);
                                        }
                                        sendMessage.setText("Wallet successfully registered");
                                    }
                                } else {
                                    sendMessage.setText("Invalid wallet address. Please check your address and retry.");
                                }
                            } else {
                                sendMessage.setText("Cannot change wallet address when buying tickets");
                            }
                        }
                    }
                    sendMessage.setChatId(chat_id);
                    try {
                        execute(sendMessage);
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }
                    break;
                }

                case "/mywallet":
                case "/mywallet@Hell_Gates_Bot": {
                    if(currentlyActiveGames.containsKey(chat_id)) {
                        Game game = currentlyActiveGames.get(chat_id);
                        if(game.hasGameStarted) {
                            return;
                        }
                    }
                    SendMessage sendMessage = new SendMessage();
                    if(!update.getMessage().getChat().isUserChat()) {
                        sendMessage.setText("Please use /mywallet command in private chat");
                    } else {
                        Document getAddyDoc = new Document(idKey, player_id);
                        Document foundAddyDoc = (Document) userDataCollection.find(getAddyDoc).first();
                        if(foundAddyDoc != null) {
                            String addy = (String) foundAddyDoc.get(addyKey);
                            sendMessage.setText("Your registered address is : \n\n" + addy);
                        } else {
                            sendMessage.setText("You haven't registered with any wallet yet. Please use /register command to register now.");
                        }
                    }
                    sendMessage.setChatId(chat_id);
                    try {
                        execute(sendMessage);
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }
                    break;
                }

                case "/mytickets":
                case "/mytickets@Hell_Gates_Bot": {
                    if(currentlyActiveGames.containsKey(chat_id)) {
                        Game game = currentlyActiveGames.get(chat_id);
                        if(game.hasGameStarted) {
                            return;
                        }
                    }
                    SendMessage sendMessage = new SendMessage();
                    if(!update.getMessage().getChat().isUserChat()) {
                        sendMessage.setText("Please use /mytickets command in private chat");
                    } else {
                        Document getAddyDoc = new Document(idKey, player_id);
                        Document foundAddyDoc = (Document) userDataCollection.find(getAddyDoc).first();
                        if(foundAddyDoc != null) {
                            int tickets = (int) foundAddyDoc.get(ticketKey);
                            sendMessage.setText("You currently have " + tickets + " tickets");
                        } else {
                            sendMessage.setText("You haven't registered with any wallet yet. Only registered uses can have tickets.");
                        }
                    }
                    sendMessage.setChatId(chat_id);
                    try {
                        execute(sendMessage);
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }
                    break;
                }

                case "/paywithticket":
                case "/paywithticket@Hell_Gates_Bot": {
                    boolean shouldSend = true;
                    SendMessage sendMessage = new SendMessage();
                    if (update.getMessage().getChat().isGroupChat() || update.getMessage().getChat().isSuperGroupChat()) {
                        if (currentlyActiveGames.containsKey(chat_id)) {
                            Game game = currentlyActiveGames.get(chat_id);
                            if(game.isAcceptingEntryPayment) {
                                Document getAddyDoc = new Document(idKey, player_id);
                                Document foundAddyDoc = (Document) userDataCollection.find(getAddyDoc).first();
                                if(foundAddyDoc != null) {
                                    int tickets = (int) foundAddyDoc.get((game.EthNetworkType + "Tickets"));
                                    if(tickets > 0) {
                                        if(game.payWithTicketForThisUser(player_id)){
                                            tickets--;
                                            Bson updatedAddyDoc = new Document((game.EthNetworkType + "Tickets"), tickets);
                                            Bson updateAddyDocOperation = new Document("$set", updatedAddyDoc);
                                            userDataCollection.updateOne(foundAddyDoc, updateAddyDocOperation);
                                            shouldSend = false;
                                        }
                                    } else {
                                        sendMessage.setText("@" + update.getMessage().getFrom().getUserName() + " You have 0 tickets. Cannot " +
                                                "pay with tickets.");
                                    }
                                } else {
                                    sendMessage.setText("@" + update.getMessage().getFrom().getUserName() + " You have not registered yourself yet. " +
                                            "Please use /register command in private chat to register. @" + getBotUsername());
                                }
                            } else if(game.hasGameStarted) {
                                shouldSend = false;
                            } else {
                                sendMessage.setText("Not accepting any Entry payment at the moment");
                            }
                        } else {
                            sendMessage.setText("@" + update.getMessage().getFrom().getUserName() + " No active game. Not accepting any " +
                                    "entry payment at the moment");
                        }
                    } else {
                        sendMessage.setText("This command can only be run in a group!!!");
                    }
                    sendMessage.setChatId(chat_id);
                    if (shouldSend) {
                        try {
                            execute(sendMessage);
                        } catch (TelegramApiException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                }

                case "/buytickets":
                case "/buytickets@Hell_Gates_Bot": {
                    if(currentlyActiveGames.containsKey(chat_id)) {
                        Game game = currentlyActiveGames.get(chat_id);
                        if(game.hasGameStarted) {
                            return;
                        }
                    }
                    SendMessage sendMessage = new SendMessage();
                    boolean shouldSend = true;
                    if(!update.getMessage().getChat().isUserChat()) {
                        sendMessage.setText("Please use /buytickets command in private chat @" + getBotUsername());
                    } else {
                        if(EthNetworkType.equalsIgnoreCase("mainnet")) {
                            if (inputMsg.length != 2) {
                                sendMessage.setText("Proper format to use this command is :\n/buytickets amount");
                            } else {
                                if (playersBuyingTickets.contains(player_id)) {
                                    sendMessage.setText("Please complete your current purchase before starting a new purchase");
                                } else if (playersPlayingAGame.contains(player_id)) {
                                    sendMessage.setText("Cannot buy tickets when playing a game. You can only buy tickets when you are not part of an " +
                                            "active game");
                                } else {
                                    Document searchDoc = new Document(idKey, player_id);
                                    Document foundDoc = (Document) userDataCollection.find(searchDoc).first();
                                    if (foundDoc != null) {
                                        int amountToBuy;
                                        try {
                                            amountToBuy = Integer.parseInt(inputMsg[1]);
                                            TicketBuyer ticketBuyer = new TicketBuyer(this, chat_id, amountToBuy,
                                                    (String) foundDoc.get(addyKey), EthNetworkType, ourWallet, RTKContractAddress, joinCost);
                                            playersBuyingTickets.add(player_id);
                                            ticketBuyers.put(chat_id, ticketBuyer);
                                            shouldSend = false;
                                        } catch (Exception e) {
                                            sendMessage.setText("Proper format to use this command is :\n/buytickets amount\n\nWhere amount has to be a number");
                                        }
                                    } else {
                                        sendMessage.setText("You have not registered yet. Please register first to buy tickets");
                                    }
                                }
                            }
                        } else {
                            sendMessage.setText("Cannot buy tickets at the moment");
                        }
                    }
                    sendMessage.setChatId(chat_id);
                    if(shouldSend) {
                        try {
                            execute(sendMessage);
                        } catch (TelegramApiException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                }

                case "/rules" :
                case "/rules@Hell_Gates_Bot": {
                    if(currentlyActiveGames.containsKey(chat_id)) {
                        Game game = currentlyActiveGames.get(chat_id);
                        if(game.hasGameStarted) {
                            return;
                        }
                    }
                    SendMessage sendMessage = new SendMessage();
                    if(!update.getMessage().getChat().isUserChat()) {
                        sendMessage.setText("Please use this command in private chat @" + getBotUsername());
                    } else {
                        sendMessage.setText("Hell Gates - GAME RULES:\n\n" +
                                "- ANY & ONLY registered player can start a new game by using the /startgame command in a group chat. " +
                                "- A minimum of " + minimumNumberOfPlayers + " players will join the game using /join command within " +
                                "a given amount of time after game creation (Maximum 10 players)\n\n" +
                                "After joining the game, the bot will ask players to pay " + joinCost.divide(new BigInteger("1000000000000000000")) +
                                " RTK (OR) those who have bought tickets can instantly pay using the command /paywithticket. " +
                                "To buy tickets, please DM @Hell_Gates_Bot. Price :- " + joinCost.divide(new BigInteger("1000000000000000000")) +
                                " RTK (Tickets cannot be bought when playing a game)\n\n" +
                                "- Each round every players gets to pick a door;\n" +
                                "- The number behind the chosen door represents the number of SHOTS TO BE FIRED i.e the number of transactions " +
                                "the player needs to send to the specified wallet;\n" +
                                "- Send the number of transactions the bot tells you to! Each transaction must contain EXACTLY " +
                                shotCost.divide(new BigInteger("1000000000000000000")) + " RTK (no more no less)!" +
                                " Do not try to send - multiple shots in one transaction. Different amounts will be considered invalid and " +
                                "they will not count. NO REFUNDS!\n" +
                                "- A shot transaction will mean that the shot player gets eliminated from the game.\n" +
                                "- Those that don’t get shot make it to the next round where they must again pick a door.\n" +
                                "- Last man standing in the game is considered the winner and will collect the prize.\n" +
                                "- Prize is the sum of all player’s join cost (ex: For 6 players, prize =  6*" +
                                joinCost.divide(new BigInteger("1000000000000000000")) + " RTK)\n\n" +
                                "Developed by: @oregazembutoushiisuru\n\n" +
                                "Please note: “Hell Gates” is still in Public Beta so feel free to address any bug / issue so that we may correct them.\n\n" +
                                "This is an abridged version of rules. IT IS HIGHLY RECOMMENDED TO READ THE FULL RULES AT LEAST ONCE BY TAPPING ON THE FOLLOWING " +
                                "COMMAND --> /gamebook@Hell_Gates_Bot");
                    }
                    sendMessage.setChatId(chat_id);
                    try {
                        execute(sendMessage);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                }

                case "/gamebook" :
                case "/gamebook@Hell_Gates_Bot": {
                    if(currentlyActiveGames.containsKey(chat_id)) {
                        Game game = currentlyActiveGames.get(chat_id);
                        if(game.hasGameStarted) {
                            return;
                        }
                    }
                    SendMessage sendMessage = new SendMessage();
                    if(!update.getMessage().getChat().isUserChat()) {
                        sendMessage.setText("Please use this command in private chat @" + getBotUsername());
                    } else {
                        sendMessage.setText("Hell Gates Bot\n" +
                                "\n" +
                                "Are you feeling LUCKY & DEMONIC today?\n" +
                                "Do you feels as though you can win big today?\n" +
                                "Then Boi oh Boi! It’s time for you to test your luck by playing a game with Hell Gates Bot.\n" +
                                "\n\n" +
                                "How to Play?\n\n" +
                                "First of all, make sure you have a registered with the bot. If you haven’t already, then use the /register " +
                                "command in private chat with the bot. (Format: - /register RTK_Wallet_Addy <--- Separated by single space).\n\n" +
                                "Now that you are registered, anyone can start a new game by using the /startgame command in a group chat. If no games " +
                                "are already running, a new game will be created.\n\n" +
                                "For any game to begin, a minimum of 3 players should join the game using /join command within a given amount of time after " +
                                "game creation (Maximum 10 players).\n\n" +
                                "The person who initiated the game can then use /begin command to start the game or else, if minimum number of players are found, " +
                                "then the game starts automatically after join time ends.\n\n" +
                                "Next when the game begins, players have to pay " + joinCost.divide(new BigInteger("1000000000000000000")) + " RTK " +
                                "by sending the RTK to the ADDRESS THAT THE BOT WILL TELL." +
                                "\nCurrent Entry payment wallet : " + ourWallet + "\n\nALTERNATIVELY, if " +
                                "you have tickets, you can join the game by paying 1 entry ticket by using the command /paywithticket. If you don’t have " +
                                "tickets, you can buy them from the bot for a price of " + joinCost.divide(new BigInteger("1000000000000000000")) + " RTK per ticket.\n\n" +
                                "After everyone have made their payments, final player list is displayed. \n\n" +
                                "The Prize Pool is decided at this point of time for winning the game. Say n players join the game. Then prize pool = n * 250 RTK.\n" +
                                "\uF0F0\tIf 3 players are in the game, then the winner will get 3*" + joinCost.divide(new BigInteger("1000000000000000000")) + " RTK. \n" +
                                "\n\n" +
                                "Now the Round 1 starts.\n" +
                                "First everyone is confronted by 6 doors. \n" +
                                "•\t1 random door has 0 Shots\n" +
                                "•\t2 random doors have 1 Shot each\n" +
                                "•\tRemaining 3 doors have 3 Shots each\n" +
                                "(Arrangement of Shots is different for all players)\n" +
                                "\n\n" +
                                "Your aim is to get minimum shots. You first pick one door. Then the bot will open two doors. One of the doors will definitely have " +
                                "3 shots and another door will definitely have 1 shot. You can then decide to switch to one of the remaining 3 doors or continue with " +
                                "your current choice.\n" +
                                "After continuing with a new choice or your original choice, the bot will open the door which was your final selection. The shots behind it will be displayed on score board under your user name.\n" +
                                "\n\n" +
                                "When everyone has received their shots for the current round, the score board will be displayed and shot firing will begin.\n" +
                                "Here, everyone has to fire the same number of shots as displayed on the score board. Shots refer to transactions of RTK Token. " +
                                "Each shot has to be a transaction of " + shotCost.divide(new BigInteger("1000000000000000000")) + " RTK and " +
                                "it must be sent FROM YOUR REGISTERED WALLET to the wallet that the bot " +
                                "will tell you during the game.\nCurrent Shot wallet : " + shotWallet + "\n\n" +
                                "After everyone has fired their shots, results will be displayed. Everyone whose at least 1 shot gets burned, will be kicked out the game and will not play the next round.\n" +
                                "(In case all players get shot, then everyone is promoted to the next round.)\n" +
                                "If only two players are remaining and they tie, then will play mini rounds. In mini rounds, both players have to fire 2 shots each. They will be kept promoted to next round unless EXACTLY 1 player gets shot.\n" +
                                "The only person remaining will be declared as the winner and the prize will be sent to his registered wallet address.\n" +
                                "\n" +
                                "I hope you all will win big from this bot and enjoy to your fullest. Make sure you don’t get your head and butt shot.\n" +
                                "\n" +
                                "The code for the bot was written by: @OreGaZembuToushiiSuru\n");
                    }
                    sendMessage.setChatId(chat_id);
                    try {
                        execute(sendMessage);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                }

                case "/addtickets":
                case "/addtickets@Hell_Gates_Bot": {
                    if(player_id != getAdminChatId()) {
                        return;
                    } else {
                        if(update.getMessage().isReply()) {
                            int Id = update.getMessage().getReplyToMessage().getFrom().getId();
                            Document document = new Document(idKey, Id);
                            Document foundDoc = (Document) userDataCollection.find(document).first();
                            if(inputMsg.length == 3) {
                                if(foundDoc != null) {
                                    try {
                                        int ticks = Integer.parseInt(update.getMessage().getText().trim().split(" ")[1]);
                                        String networkType = inputMsg[2];
                                        if(networkType.equalsIgnoreCase("mainnet") || networkType.equalsIgnoreCase("ropsten")) {
                                            int tickets = (int) foundDoc.get(ticketKey) + ticks;
                                            Bson updatedAddyDoc = new Document((networkType + "Tickets"), tickets);
                                            Bson updateAddyDocOperation = new Document("$set", updatedAddyDoc);
                                            userDataCollection.updateOne(foundDoc, updateAddyDocOperation);
                                            sendMessage(chat_id, "Successfully Added " + ticks + " Ticket");
                                        } else {
                                            sendMessage(chat_id, "Invalid Format");
                                        }
                                    } catch (Exception e) {
                                        sendMessage(chat_id, "Invalid Format");
                                    }
                                } else {
                                    sendMessage(chat_id, "User has not registered");
                                }
                            } else {
                                sendMessage(chat_id, "Proper format : /addtickets amount network_Type");
                            }
                        } else {
                            sendMessage(chat_id, "This message has to be a reply type message");
                        }
                    }
                    break;
                }

                case "/transfertickets":
                case "/transfertickets@Lucky_Gates_Bot": {
                    if(currentlyActiveGames.containsKey(chat_id)) {
                        Game game = currentlyActiveGames.get(chat_id);
                        if(game.hasGameStarted) {
                            return;
                        }
                    }
                    SendMessage sendMessage = new SendMessage();
                    if(update.getMessage().getChat().isGroupChat() || update.getMessage().getChat().isSuperGroupChat()) {
                        if(EthNetworkType.equalsIgnoreCase("ropsten")) {
                            sendMessage(chat_id, "Cannot use this command. The bot is in ropsten mode");
                            return;
                        }
                        if(update.getMessage().isReply()) {
                            if(inputMsg.length != 2) {
                                sendMessage.setText("Proper format to use this command is : /transfertickets@Hell_Gates_Bot amountToTransfer");
                            } else {
                                try {
                                    int amountToTransfer = Integer.parseInt(inputMsg[1]);
                                    int ToId = update.getMessage().getReplyToMessage().getFrom().getId();
                                    Document FromDocument = new Document(idKey, player_id);
                                    Document ToDocument = new Document(idKey, ToId);
                                    Document foundFromDoc = (Document) userDataCollection.find(FromDocument).first();
                                    Document foundToDoc = (Document) userDataCollection.find(ToDocument).first();
                                    if(foundFromDoc != null) {
                                        int tickets = (int) foundFromDoc.get(ticketKey);
                                        if(tickets >= amountToTransfer) {
                                            if(foundToDoc != null) {
                                                Bson updatedAddyDoc = new Document(ticketKey, tickets - amountToTransfer);
                                                Bson updateAddyDocOperation = new Document("$set", updatedAddyDoc);
                                                userDataCollection.updateOne(foundFromDoc, updateAddyDocOperation);
                                                tickets = (int) foundToDoc.get(ticketKey);
                                                updatedAddyDoc = new Document(ticketKey, tickets + amountToTransfer);
                                                updateAddyDocOperation = new Document("$set", updatedAddyDoc);
                                                userDataCollection.updateOne(foundToDoc, updateAddyDocOperation);
                                                sendMessage.setText("Ticket Transfer Successful");
                                            } else {
                                                sendMessage.setText("The person to whom you are trying to send tickets has not registered with the bot yet." +
                                                        " Cannot transfer tickets.");
                                            }
                                        } else {
                                            sendMessage.setText("You don't have enough tickets");
                                        }
                                    } else {
                                        sendMessage.setText("Please register with the bot before using this command");
                                    }
                                } catch (Exception e) {
                                    sendMessage.setText("Proper format to use this command is : /transfertickets@Hell_Gates_Bot amountToTransfer\n\n" +
                                            "Amount has to be a number");
                                }
                            }
                        } else {
                            sendMessage.setText("This message has to be a reply type message quoting any message of the person to whom you want to " +
                                    "transfer the tickets");
                        }
                    } else {
                        sendMessage.setText("This command can only be run in a group!!!");
                    }
                    sendMessage.setChatId(chat_id);
                    try {
                        execute(sendMessage);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }
    }

    @Override
    public String getBotUsername() {
        return "Hell_Gates_Bot";
    }

    @Override
    public String getBotToken() {
        return (System.getenv("hellgatesBotTokenA") + ":" + System.getenv("hellgatesBotTokenB"));
    }



    public void sendMessage(long chat_id, String msg, String... url) {
        if(url.length == 0) {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setText(msg);
            sendMessage.setChatId(chat_id);
            try {
                execute(sendMessage);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            SendAnimation sendAnimation = new SendAnimation();
            sendAnimation.setAnimation(url[(int)(Math.random()*(url.length))]);
            sendAnimation.setCaption(msg);
            sendAnimation.setChatId(chat_id);
            try {
                execute(sendAnimation);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    } // Normal Message Sender

    public void sendMessage(long chat_id, String msg, int editMessageId, String callbackQueryId) {
        EditMessageText editMessageText = new EditMessageText().setMessageId(editMessageId);
        editMessageText.setChatId(chat_id);
        editMessageText.setText(msg);
        AnswerCallbackQuery answerCallbackQuery = new AnswerCallbackQuery();
        answerCallbackQuery.setCallbackQueryId(callbackQueryId);
        try {
            execute(editMessageText);
            execute(answerCallbackQuery);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

    } // Edit Message Sender

    // Buttoned Message Sender...
    public void sendMessage(long chat_id, String msg, String[] buttonText, String[] buttonValues, String... url) {
        if(url.length == 0) {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setText(msg);
            sendMessage.setChatId(chat_id);
            List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
            List<InlineKeyboardButton> rowInLine = new ArrayList<>();
            InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
            for(int i = 0; i < buttonText.length; i++) {
                rowInLine.add(new InlineKeyboardButton().setText(buttonText[i]).setCallbackData(buttonValues[i]));
            }
            rowsInLine.add(rowInLine);
            inlineKeyboardMarkup.setKeyboard(rowsInLine);
            sendMessage.setReplyMarkup(inlineKeyboardMarkup);
            try {
                execute(sendMessage);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            SendAnimation sendAnimation = new SendAnimation();
            sendAnimation.setAnimation(url[(int)(Math.random()*(url.length))]);
            sendAnimation.setCaption(msg);
            sendAnimation.setChatId(chat_id);
            List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
            List<InlineKeyboardButton> rowInLine = new ArrayList<>();
            InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
            for(int i = 0; i < buttonText.length; i++) {
                rowInLine.add(new InlineKeyboardButton().setText(buttonText[i]).setCallbackData(buttonValues[i]));
            }
            rowsInLine.add(rowInLine);
            inlineKeyboardMarkup.setKeyboard(rowsInLine);
            sendAnimation.setReplyMarkup(inlineKeyboardMarkup);
            try {
                execute(sendAnimation);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void executeAnswerCallbackQuery(AnswerCallbackQuery answerCallbackQuery) throws TelegramApiException {
        execute(answerCallbackQuery);
    }

    public void deleteGame(long chat_id) {
        currentlyActiveGames.remove(chat_id);
    }

    public long getAdminChatId() {
        return 607901021;
    }

    public void playerRemovedFromGame(int playerID) {
        playersPlayingAGame.remove((Integer) playerID);
    }

    public void refund1Ticket(int playerId) {
        Document fetchDocument = new Document(idKey, playerId);
        Document gotDocument = (Document) userDataCollection.find(fetchDocument).first();
        if(gotDocument != null) {
            int tickets = (int) gotDocument.get(ticketKey);
            tickets++;
            Bson replaceDoc = new Document(ticketKey, tickets);
            Bson replaceDocOOperation = new Document("$set", replaceDoc);
            userDataCollection.updateOne(fetchDocument, replaceDocOOperation);
        }
    }

    public void playerTicketPurchaseEnded(int playerId, int numberOfTicketsToBuy, boolean didPay, String ticketKey) {
        if(didPay) {
            Document document = new Document(idKey, playerId);
            Document foundDocument = (Document) userDataCollection.find(document).first();
            if(foundDocument != null) {
                int tickets = (int) foundDocument.get(ticketKey);
                Bson updateDoc = new Document(ticketKey, (numberOfTicketsToBuy + tickets));
                Bson updateDocOperation = new Document("$set", updateDoc);
                userDataCollection.updateOne(foundDocument, updateDocOperation);
            }
        }
        playersBuyingTickets.remove((Integer) playerId);
        ticketBuyers.remove((long) playerId);
    }
}