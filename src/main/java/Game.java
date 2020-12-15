import io.reactivex.disposables.Disposable;
import org.apache.log4j.Logger;
import org.telegram.telegrambots.meta.api.objects.User;
import org.web3j.abi.TypeDecoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.contracts.eip20.generated.ERC20;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.core.methods.response.Web3ClientVersion;
import org.web3j.protocol.websocket.WebSocketClient;
import org.web3j.protocol.websocket.WebSocketService;
import org.web3j.tx.gas.ContractGasProvider;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * Testnet webSocket urls are :-
 * "wss://ropsten.infura.io/ws/v3/04009a10020d420bbab54951e72e23fd"
 * "wss://ropsten.infura.io/ws/v3/94fead43844d49de833adffdf9ff3993"
 * "wss://ropsten.infura.io/ws/v3/b8440ab5890a4d539293994119b36893"
 * "wss://ropsten.infura.io/ws/v3/b05a1fe6f7b64750a10372b74dec074f"
 * "wss://ropsten.infura.io/ws/v3/2e98f2588f85423aa7bced2687b8c2af"
 *
 * MainNet webSocket urls are :-
 * "wss://mainnet.infura.io/ws/v3/04009a10020d420bbab54951e72e23fd"
 * "wss://mainnet.infura.io/ws/v3/94fead43844d49de833adffdf9ff3993"
 * "wss://mainnet.infura.io/ws/v3/b8440ab5890a4d539293994119b36893"
 * "wss://mainnet.infura.io/ws/v3/b05a1fe6f7b64750a10372b74dec074f"
 * "wss://mainnet.infura.io/ws/v3/2e98f2588f85423aa7bced2687b8c2af"
 *
 * Ropsten TestNet RTKContractAddress = "0xa59b4d4c371a4b7957d7a3bf06c19ec3ac5885f1"
 * Ethereum Mainnet RTKContractAddress = "0x1F6DEADcb526c4710Cf941872b86dcdfBbBD9211"
 * */

public class Game {

    final long chat_id;
    final int gameInitiator;
    final Hell_Gates_Bot hell_gates_bot;
    Instant gameStartTime, gameCurrentTime, gameDestroyTime;
    final int minimumNumberOfPlayers;
    boolean shouldTryToEstablishConnection = true;
    Logger logger = Logger.getLogger(Game.class);
    Queue<CallbackResponse> callbackResponses = new LinkedList<>();
    private boolean isLookingForPlayerResponses = false;


    ///// WEB3 Related Stuff :-
    final String RTKContractAddress;
    final String EthNetworkType;
    final String ourWallet;
    final String shotWallet;
    final BigInteger joinCost;
    final BigInteger shotCost;
    private final ArrayList<String> webSocketUrls = new ArrayList<>();
    private WebSocketService webSocketService;
    private Web3j web3j;
    private Disposable disposable = null;
    private ArrayList<Transaction> allRTKTransactions = new ArrayList<>();
    private ArrayList<Log> flowableTransactionLog = new ArrayList<>();
    private AtomicInteger numberOfTransactionsFetched = new AtomicInteger(0);
    private int numberOfTransactionsExamined = 0;
    BigInteger minGasFees;
    BigInteger gasPrice;
    /////


    // Constructor
    public Game(Hell_Gates_Bot hell_gates_bot, long chat_id, int playerInitiator, String EthNetworkType, String ourWallet, String shotWallet,
                String RTKContractAddress, BigInteger joinCost, BigInteger shotCost, int minimumNumberOfPlayers) {
        this.chat_id = chat_id;
        this.hell_gates_bot = hell_gates_bot;
        gameInitiator = playerInitiator;
        gameStartTime = Instant.now();
        gameDestroyTime = gameStartTime.plus(10, ChronoUnit.MINUTES);
        this.EthNetworkType = EthNetworkType;
        this.ourWallet = ourWallet;
        this.shotWallet = shotWallet;
        this.RTKContractAddress = RTKContractAddress;
        this.joinCost = joinCost;
        this.shotCost = shotCost;
        this.minimumNumberOfPlayers = minimumNumberOfPlayers;

        ///// Setting web3 data
        // Connecting to web3 client
        webSocketUrls.add("wss://" + EthNetworkType + ".infura.io/ws/v3/04009a10020d420bbab54951e72e23fd");
        webSocketUrls.add("wss://" + EthNetworkType + ".infura.io/ws/v3/94fead43844d49de833adffdf9ff3993");
        webSocketUrls.add("wss://" + EthNetworkType + ".infura.io/ws/v3/b8440ab5890a4d539293994119b36893");
        webSocketUrls.add("wss://" + EthNetworkType + ".infura.io/ws/v3/b05a1fe6f7b64750a10372b74dec074f");
        webSocketUrls.add("wss://" + EthNetworkType + ".infura.io/ws/v3/2e98f2588f85423aa7bced2687b8c2af");
        /////

        gameThread.start();
    }


    public boolean hasGameStarted;
    public boolean isAcceptingEntryPayment = false;
    private final ArrayList<Integer> player_Ids = new ArrayList<>();
    private final TwoKeyIdAddyValHashMap fullPlayerCollection = new TwoKeyIdAddyValHashMap();
    public int numberOfPlayers = 0;
    private int doorSelection = -1;
    private boolean wasPrevMatchTwoPTie = false;
    private boolean shouldPlayMiniGame = false;


    Thread gameThread = new Thread() {
        @Override
        public void run() {
            super.run();
            if(EthNetworkType.equalsIgnoreCase("ropsten")) {
                hell_gates_bot.sendMessage(chat_id, "Ropsten");
                performProperWait(2);
            }
            gameCurrentTime = gameStartTime;
            boolean shouldClose = false;
            boolean hasEnoughBalance = hasEnoughBalance();
            if(!hasEnoughBalance) {
                hasGameStarted = true;
                hell_gates_bot.sendMessage(chat_id, "Rewards Wallet " + ourWallet + " doesn't have enough eth for transactions. " +
                        "Please contact admins. Closing Game\n\nMinimum eth required : " + minGasFees.divide(new BigInteger(
                                "1000000000000000000")));
                removeAllPlayersAndDeleteTheGame();
                try {
                    join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            while (!hasGameStarted) {
                gameCurrentTime = Instant.now();

                if(numberOfPlayers >= 10) {
                    hell_gates_bot.sendMessage(chat_id, "Maximum number of players have joined the game and we can proceed with payments");
                    hasGameStarted = true;
                }

                if(gameCurrentTime.compareTo(gameDestroyTime) > 0) {
                    if(numberOfPlayers >= minimumNumberOfPlayers) {
                        hell_gates_bot.sendMessage(chat_id, "Join time over. Starting Game with " + numberOfPlayers + " players");
                        hasGameStarted = true;
                        performProperWait(1);
                    } else {
                        hell_gates_bot.sendMessage(chat_id, "Not enough players. Cancelling the game.");
                        hasGameStarted = true;
                        shouldClose = true;
                    }
                }
            }

            if (shouldClose) {
                removeAllPlayersAndDeleteTheGame();
                try {
                    join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            hell_gates_bot.sendMessage(chat_id, "Final Player list has been decided to\n\n" +
                    fullPlayerCollection.getAllPlayerPing(), "https://media.giphy.com/media/j2pWZpr5RlpCodOB0d/giphy.gif");
            performProperWait(1);

            Instant paymentEndInstant = Instant.now().plus(10, ChronoUnit.MINUTES);

            boolean val = buildNewConnectionToEthereumNetwork(true);

            if(!val) {
                hell_gates_bot.sendMessage(chat_id, "The bot encountered an error Unable to connect to ethereum Network. " +
                        "Please try again later. Closing the current game...");
                removeAllPlayersAndDeleteTheGame();
                try {
                    join();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }


            isAcceptingEntryPayment = true;
            hell_gates_bot.sendMessage(chat_id, "All players must EITHER send " + joinCost.divide(new BigInteger(
                    "1000000000000000000")) + " RTK within 10 minutes from their REGISTERED wallet to the following mentioned " +
                    "below this message,\n\nOR\n\n\nPlayers who ALREADY have tickets, CAN INSTEAD pay 1 ticket by using the " +
                    "/paywithticket command. \n\nAfter everyone completes any one these steps, everyone will be notified 1 minute " +
                    "before the game starts.\n\n");
            hell_gates_bot.sendMessage(chat_id, ourWallet);
            while (Instant.now().compareTo(paymentEndInstant) < 0) {
                while (numberOfTransactionsExamined < numberOfTransactionsFetched.get() && numberOfTransactionsFetched.get() != 0) {
                    try {
                        TransactionData transactionData = splitInputData(flowableTransactionLog.get(numberOfTransactionsExamined),
                                allRTKTransactions.get(numberOfTransactionsExamined));
                        boolean condition = !transactionData.methodName.equalsIgnoreCase("Useless") &&
                                transactionData.toAddress.equalsIgnoreCase(ourWallet) &&
                                transactionData.value.compareTo(joinCost) >= 0 &&
                                fullPlayerCollection.containsKey(transactionData.fromAddress);
                        numberOfTransactionsExamined++;

                        if(condition) {
                            fullPlayerCollection.get(transactionData.fromAddress).didPlayerPay = true;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if(fullPlayerCollection.didAllPlayerPay()) {
                    break;
                }
               performProperWait(1.5f);
            }

            int num = numberOfPlayers;
            for(int i = 0; i < num; i++) {
                if(!fullPlayerCollection.get(player_Ids.get(0)).didPlayerPay) {
                    fullPlayerCollection.remove(player_Ids.get(0));
                }
            }

            if(numberOfPlayers < minimumNumberOfPlayers) {
                hell_gates_bot.sendMessage(chat_id, "Less than " + minimumNumberOfPlayers + " people paid the entry fees. " +
                        "Cannot start the game. Closing the game.\n\n\nEveryone who paid the entry fees via RTK or entry Ticket will " +
                        "be refunded by adding 1 ticket to their account. You can use /mytickets command in private chat with me to " +
                        "check your number of tickets.\n\n\n*Note : It can take up to 5 minutes before you receive the refund. " +
                        "Please be patient");
                num = numberOfPlayers;
                for(int i = 0; i < num; i++) {
                    hell_gates_bot.refund1Ticket(player_Ids.get(0));
                    removePlayer(player_Ids.get(0));
                }
                hell_gates_bot.deleteGame(chat_id);
                try {
                    join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            isAcceptingEntryPayment = false;
            disposable.dispose();
            BigInteger totalPool = joinCost.multiply(new BigInteger(Integer.toString(numberOfPlayers)));
            hell_gates_bot.sendMessage(chat_id, fullPlayerCollection.getAllPlayerPing() + "\n\nPayment for above players was " +
                    "confirmed. The game will start exactly after 1 minutes.\n\nAll players will be kept notified until the game " +
                    "begins", "https://media.giphy.com/media/xOix1S8lhWAZa/giphy.gif");

            Instant minEnd = Instant.now().plus(60, ChronoUnit.SECONDS);
            Instant min5Warn = Instant.now().plus(30, ChronoUnit.SECONDS);
            boolean min5 = true;

            while (Instant.now().compareTo(minEnd) <= 0) {
                if(min5) {
                    if(Instant.now().compareTo(min5Warn) > 0) {
                        hell_gates_bot.sendMessage(chat_id, fullPlayerCollection.getAllPlayerPing() + "\n\n30 Seconds remaining " +
                                "before game start");
                        min5 = false;
                    }
                }
                performProperWait(1);
            }

            hell_gates_bot.sendMessage(chat_id, fullPlayerCollection.getAllPlayerPing() + "\nGame has Begun...");
            performProperWait(1);

            while (numberOfPlayers > 1) {
                for(int i = 0; i < numberOfPlayers; i++) {
                    List<String> nos = new ArrayList<>();
                    nos.add("0");
                    nos.add("1");
                    nos.add("1");
                    nos.add("3");
                    nos.add("3");
                    nos.add("3");
                    Collections.shuffle(nos);
                    StringBuilder str = new StringBuilder();
                    for (String no : nos) {
                        str.append(no);
                    }
                    fullPlayerCollection.get(player_Ids.get(i)).uniqueDoorArrangement = str.toString();
                    fullPlayerCollection.get(player_Ids.get(i)).shotsToFire = 0;
                    fullPlayerCollection.get(player_Ids.get(i)).didGotShot = false;
                }
                int currentTurnOfPlayer = 0;

                while (currentTurnOfPlayer < numberOfPlayers) {
                    performProperWait(1);
                    startTurnOfPlayer(currentTurnOfPlayer);
                    currentTurnOfPlayer++;
                }

                boolean[] result = fireAllPlayerShots();

                if(!disposable.isDisposed()) {
                    disposable.dispose();
                }

                boolean miniGameResult = false;
                if(shouldPlayMiniGame) {
                    miniGameResult = playMiniGame();
                    if(!miniGameResult) {
                        break;
                    }
                }

                if(result[0] || miniGameResult) {
                    hell_gates_bot.sendMessage(chat_id, "The winner of the game is : @" +
                                    fullPlayerCollection.get(player_Ids.get(0)).username +
                                    "\n\n\nYou have won " + totalPool.divide(new BigInteger("1000000000000000000")) + 
                                    " RTK. Sending out your rewards now.","https://media.giphy.com/media/fW4lLDow8bwXxPKzNV/giphy.gif", 
                            "https://media.giphy.com/media/11uArCoB4fkRcQ/giphy.gif", "https://media.giphy.com/media/3o6wrETjrlt0RegHNS/giphy.gif", 
                            "https://media.giphy.com/media/THlcEnowukS9BRaVcw/giphy.gif");
                    sendRewardToWinner(totalPool, fullPlayerCollection.get(player_Ids.get(0)).addy);
                    hell_gates_bot.sendMessage(chat_id, "Your rewards have been sent. Please check the new balance in your Registered wallet.");
                    removePlayer(player_Ids.get(0));
                    hell_gates_bot.deleteGame(chat_id);
                    numberOfPlayers = -1;
                    try {
                        join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                if(result[1] || result[2]) {
                    removeAllPlayersAndDeleteTheGame();
                    numberOfPlayers = -1;
                    try {
                        join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }

            removeAllPlayersAndDeleteTheGame();
            try {
                join();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    };


    // By the end of this function, it would have been determined how many shots each player will fire
    private void startTurnOfPlayer(int playerIndex) {
        Instant endTime, halfTime, quarterTime;
        quarterTime = Instant.now();
        halfTime = Instant.now();
        endTime = Instant.now();
        int turnStage = 1;
        int halfValue = 0;
        int quarterValue = 0;
        boolean isWaiting = false;
        int doorWith3 = 0, doorWith1 = 0;
        boolean switched;

        OUTER:
        while (true) {
            if (!isWaiting) {
                switch (turnStage) {
                    case 1: {
                        halfValue = 45;
                        quarterValue = 23;
                        performProperWait(2);
                        hell_gates_bot.sendMessage(chat_id, "Current turn of @" +
                                        fullPlayerCollection.get(player_Ids.get(playerIndex)).username +
                                "\n\n\uD83D\uDEAA You are standing in front of 6 doors \uD83D\uDEAA\n\n3 random doors contain 3️⃣ shots " +
                                "each.\n2 other doors contain 1️⃣ shot each.\nRemaining 1 door has 0️⃣ shots.\n\nPick one door within ⏳ 90 " +
                                "seconds or else you fire 5️⃣ shots. \uD83D\uDCA5\uD83D\uDD2B",
                                new String[] {"\uD83D\uDEAA 1", "\uD83D\uDEAA 2", "\uD83D\uDEAA 3", "\uD83D\uDEAA 4", "\uD83D\uDEAA 5", "\uD83D\uDEAA 6"},
                                new String[] {"1", "2", "3", "4", "5", "6"}, "https://media.giphy.com/media/L2f1x2NFlAFScJ0Fm6/giphy.gif");
                        isLookingForPlayerResponses = true;
                        performProperWait(2);
                        endTime = Instant.now().plus(90, ChronoUnit.SECONDS);
                        halfTime = endTime.minus(45, ChronoUnit.SECONDS);
                        quarterTime = endTime.minus(22, ChronoUnit.SECONDS);
                        isWaiting  = true;
                        break;
                    }

                    case 2: {
                        halfValue = 30;
                        quarterValue = 15;
                        boolean d1 = false, d3 = false;
                        for(int i = 0; i < 6; i++) {
                            if(!d1) {
                                if(i+1 != doorSelection) {
                                    if(fullPlayerCollection.get(player_Ids.get(playerIndex)).uniqueDoorArrangement.charAt(i) == '1') {
                                        doorWith1 = i+1;
                                        d1 = true;
                                    }
                                }
                            }
                            if(!d3) {
                                if(i+1 != doorSelection) {
                                    if(fullPlayerCollection.get(player_Ids.get(playerIndex)).uniqueDoorArrangement.charAt(i) == '3') {
                                        doorWith3 = i+1;
                                        d3 = true;
                                    }
                                }
                            }
                            if(d1 && d3) {
                                break;
                            }
                        }
                        StringBuilder doorPattern = new StringBuilder();
                        for(int i = 0; i < 6; i++) {
                            if(i == doorWith1-1) {
                                doorPattern.append("1️⃣");
                            } else if (i == doorWith3-1) {
                                doorPattern.append("3️⃣");
                            } else {
                                doorPattern.append("❎");
                            }
                            if(i != 5) {
                                doorPattern.append(" -- ");
                            }
                        }
                        performProperWait(1);
                        hell_gates_bot.sendMessage(chat_id, "The gatekeeper of hell will now open two doors",
                                "https://media.giphy.com/media/J7fawBXeSAu3e/giphy.gif");
                        performProperWait(3);
                        hell_gates_bot.sendMessage(chat_id, "@" + fullPlayerCollection.get(player_Ids.get(playerIndex)).username +
                                        "\n\n" + doorPattern.toString() + "\n\n\uD83D\uDEAA Door " + doorWith1 + " has 1️⃣ shot" +
                                        " \uD83D\uDCA5\uD83D\uDD2B behind it.\n\uD83D\uDEAA Door " + doorWith3 + " has 3️⃣ shots " +
                                        "\uD83D\uDCA5\uD83D\uDD2B behind it.\n\nYou now have an option to switch to one of the remaining " +
                                        "three doors.\nYou have ⏳ 1 minute to switch. Would you like to switch?", new String[] {
                                                "Switch", "Don't Switch"}, new String[] {"Switch", "Don't Switch"});
                        isLookingForPlayerResponses = true;
                        performProperWait(1);
                        endTime = Instant.now().plus(60, ChronoUnit.SECONDS);
                        halfTime = endTime.minus(30, ChronoUnit.SECONDS);
                        quarterTime = endTime.minus(15, ChronoUnit.SECONDS);
                        isWaiting  = true;
                        break;
                    }

                    case 3: {
                        String[] text = new String[3];
                        String[] data = new String[3];
                        int k = 0;
                        for(int i = 0; i < 6; i++) {
                            if(i != doorSelection-1 && i != doorWith1-1 && i != doorWith3-1) {
                                text[k] = "\uD83D\uDEAA" + (i + 1);
                                data[k] = Integer.toString(i+1);
                                k++;
                            }
                        }
                        halfValue = 30;
                        quarterValue = 15;
                        performProperWait(2);
                        hell_gates_bot.sendMessage(chat_id, "@" + fullPlayerCollection.get(player_Ids.get(playerIndex)).username
                                + "\n\nPlease switch to one of the remaining doors. You have ⏳ 60 seconds to decide or else you fire 5️⃣ " +
                                "shots \uD83D\uDCA5\uD83D\uDD2B", text, data);
                        isLookingForPlayerResponses = true;
                        performProperWait(1);
                        endTime = Instant.now().plus(60, ChronoUnit.SECONDS);
                        halfTime = endTime.minus(30, ChronoUnit.SECONDS);
                        quarterTime = endTime.minus(15, ChronoUnit.SECONDS);
                        isWaiting  = true;
                        break;
                    }

                    case 4: {
                        String sendVal;
                        int result = Integer.parseInt(Character.toString(
                                fullPlayerCollection.get(player_Ids.get(playerIndex)).uniqueDoorArrangement.charAt(doorSelection-1)
                        ));
                        if(result == 0) {
                            sendVal = "0️⃣";
                        } else if(result == 1) {
                            sendVal = "1️⃣";
                        } else if(result == 3) {
                            sendVal = "3️⃣";
                        } else {
                            sendVal = "5️⃣";
                        }
                        hell_gates_bot.sendMessage(chat_id, "@" + fullPlayerCollection.get(player_Ids.get(playerIndex)).username
                                + "\nOpening \uD83D\uDEAA Door No. " + doorSelection,
                                "https://media.giphy.com/media/xTiTnt0GGd5p3HpDBC/giphy.gif");
                        performProperWait(2);
                        hell_gates_bot.sendMessage(chat_id, "It had " + sendVal + " shots \uD83D\uDCA5\uD83D\uDD2B behind it.");
                        fullPlayerCollection.get(player_Ids.get(playerIndex)).shotsToFire = result;
                        performProperWait(2);
                        turnStage++;
                        break;
                    }

                    default: {
                        break OUTER;
                    }
                }
            }
            else {
                boolean halfWarn = true, quarterWarn = true, didNotAnswer = true, shouldQuit = false;
                while (Instant.now().compareTo(endTime) < 0) {
                    while (!callbackResponses.isEmpty()) {
                        CallbackResponse currentResponse = callbackResponses.remove();
                        if(currentResponse.replier != player_Ids.get(playerIndex) && !shouldQuit) {
                            currentResponse.sendWrongReplierMessage();
                            continue;
                        } else if (shouldQuit) {
                            currentResponse.responseNotNeeded();
                            continue;
                        }
                        didNotAnswer = false;
                        isLookingForPlayerResponses = false;
                        String msg = "Invalid";
                        if (turnStage == 1) {
                            turnStage++;
                            doorSelection = Integer.parseInt(currentResponse.responseMsg);
                            msg = "You chose \uD83D\uDEAA Door : " + doorSelection;
                        } else if (turnStage == 2) {
                            switched = currentResponse.responseMsg.equals("Switch");
                            if(switched) {
                                turnStage++;
                            } else {
                                turnStage += 2;
                            }
                            msg ="You decided " + ((switched) ? "to Switch the \uD83D\uDEAA Doors" : "not to Switch the \uD83D\uDEAA Doors");
                        } else if (turnStage == 3) {
                            turnStage++;
                            doorSelection = Integer.parseInt(currentResponse.responseMsg);
                            msg = "You chose \uD83D\uDEAA Door : " + doorSelection;
                        }
                        hell_gates_bot.sendMessage(chat_id, msg, currentResponse.responseMsgId, currentResponse.callbackQueryId);
                        isWaiting = false;
                        shouldQuit = true;
                    }
                    if(halfWarn) {
                        if (Instant.now().compareTo(halfTime) > 0) {
                            hell_gates_bot.sendMessage(chat_id, "⏳ " + halfValue + " seconds remaining @" +
                                    fullPlayerCollection.get(player_Ids.get(playerIndex)).username);
                            halfWarn = false;
                        }
                    } else if (quarterWarn) {
                        if (Instant.now().compareTo(quarterTime) > 0) {
                            hell_gates_bot.sendMessage(chat_id, "⏳ " + quarterValue + " seconds remaining @" +
                                    fullPlayerCollection.get(player_Ids.get(playerIndex)).username);
                            quarterWarn = false;
                        }
                    }
                    performProperWait(2);
                    if(shouldQuit) {
                        break;
                    }
                }
                if (didNotAnswer) {
                    if(turnStage != 2) {
                        hell_gates_bot.sendMessage(chat_id, "@" + fullPlayerCollection.get(player_Ids.get(playerIndex)).username
                                + " you didn't respond in time. You'll have to fire 5️⃣ shots \uD83D\uDCA5\uD83D\uDD2B");
                        fullPlayerCollection.get(player_Ids.get(playerIndex)).shotsToFire = 5;
                        break;
                    } else {
                        hell_gates_bot.sendMessage(chat_id, "You haven't chosen any option. Therefore you won't switch.");
                        turnStage += 2;
                    }
                    performProperWait(2);
                }
                isLookingForPlayerResponses = false;
            }
        }
    }

    // By the end of this function, all players would have fired their shots of the current round
    private boolean[] fireAllPlayerShots() {
        performProperWait(1);
        /*
         * retBool[0] = onlyOnePlayerLeft
         * retBool[1] = shouldEndGameDueToNoPlayersLeft
         * */
        boolean[] retBool = new boolean[] {false, false, false};
        numberOfTransactionsExamined = numberOfTransactionsFetched.get();

        boolean valOF = buildNewConnectionToEthereumNetwork(true);

        if(!valOF) {
            hell_gates_bot.sendMessage(chat_id, "The bot encountered an error while connecting to ethereum network. " +
                    "All players remaining in the game will be refunded back with 1 ticket");
            int num = numberOfPlayers;
            for(int i = 0; i < num; i++) {
                hell_gates_bot.refund1Ticket(player_Ids.get(0));
                hell_gates_bot.playerRemovedFromGame(player_Ids.get(0));
                removePlayer(player_Ids.get(0));
            }
            hell_gates_bot.deleteGame(chat_id);
            retBool[2] = true;
            return retBool;
        }

        hell_gates_bot.sendMessage(chat_id, "All players will now fire their shots \uD83D\uDCA5\uD83D\uDD2B\n\n\n" +
                "1 Shot \uD83D\uDCA5\uD83D\uDD2B means 1 transaction of RTK tokens. Each shots has to be of "
                + shotCost.divide(new BigInteger("1000000000000000000")) + " RTK. Shots must be fired ONE by ONE from your " +
                "REGISTERED wallet to the bellow mentioned wallet address :", "https://tenor.com/wykJ.gif");
        performProperWait(0.5f);
        hell_gates_bot.sendMessage(chat_id, shotWallet);
        performProperWait(0.5f);
        hell_gates_bot.sendMessage(chat_id, "Everyone has ⏳ 10 minutes to fire the shots \uD83D\uDCA5\uD83D\uDD2B. Failing to " +
                "fire all the shots within time will result in loss.\n\nIf one or more shot of any of the player gets burned, he/she " +
                "will not play the next round. Others shall be promoted to the next round.\n\nIn rare cases in which one or more shots " +
                "of all remaining players result in a burn, all players will be promoted to the next round.\n\nThis is the list of players " +
                "and the amount of shots they have to fire.\n\n" + fullPlayerCollection.getAllPlayerPingWithShots() + "\nGood Luck!!");
        performProperWait(3);

        Instant trxEndTime = Instant.now().plus(10, ChronoUnit.MINUTES);
        Instant halfTime = trxEndTime.minus(5, ChronoUnit.MINUTES),
                quarterTime = trxEndTime.minus(2, ChronoUnit.MINUTES);
        int halfValue = 5, quarterValue = 2;
        boolean halfWarn = true, quarterWarn = true;

        int totalNumberOfShotsToBeFired = 0;
        for(int i = 0; i < numberOfPlayers; i++) {
            totalNumberOfShotsToBeFired += fullPlayerCollection.get(player_Ids.get(i)).shotsToFire;
        }

        while(Instant.now().compareTo(trxEndTime) < 0) {
            if(halfWarn) {
                if (Instant.now().compareTo(halfTime) > 0) {
                    hell_gates_bot.sendMessage(chat_id, fullPlayerCollection.getAllPlayerPing() + "\n" + "⏳ " + halfValue +
                            " Minutes left.", "https://media.giphy.com/media/o2KLYPem407CM/giphy.gif");
                    halfWarn = false;
                }
            }
            if(quarterWarn) {
                if (Instant.now().compareTo(quarterTime) > 0) {
                    hell_gates_bot.sendMessage(chat_id, fullPlayerCollection.getAllPlayerPing() + "\n" + "⏳ " + quarterValue +
                            " Minutes left.");
                    quarterWarn = false;
                }
            }

            while (numberOfTransactionsExamined < numberOfTransactionsFetched.get()) {
                try {
                    TransactionData transactionData = splitInputData(flowableTransactionLog.get(numberOfTransactionsExamined),
                            allRTKTransactions.get(numberOfTransactionsExamined));
                    boolean condition = !transactionData.methodName.equalsIgnoreCase("Useless") &&
                            transactionData.toAddress.equalsIgnoreCase(shotWallet) &&
                            transactionData.value.compareTo(shotCost) >= 0 &&
                            fullPlayerCollection.containsKey(transactionData.fromAddress);
                    if(condition) {
                        if(fullPlayerCollection.get(transactionData.fromAddress).shotsToFire > 0) {
                            totalNumberOfShotsToBeFired--;
                            fullPlayerCollection.get(transactionData.fromAddress).shotsToFire--;
                            if(transactionData.didBurn) {
                                fullPlayerCollection.get(transactionData.fromAddress).didGotShot = true;
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                numberOfTransactionsExamined++;
            }

            if(totalNumberOfShotsToBeFired <= 0) {
                hell_gates_bot.sendMessage(chat_id, fullPlayerCollection.getAllPlayerPing() + "\n\nShots \uD83D\uDCA5\uD83D\uDD2B " +
                        "of all players has been confirmed. Annnnnnnnnnnd the results are........",
                        "https://media.giphy.com/media/YmZOBDYBcmWK4/giphy.gif");
                performProperWait(3);
                break;
            }
        }

        int newNumOfPlayer = numberOfPlayers;
        boolean atLeastOneGotBurned = false;

        for(int i = 0; i < numberOfPlayers; i++) {
            if(fullPlayerCollection.get(player_Ids.get(i)).didGotShot) {
                atLeastOneGotBurned = true;
                newNumOfPlayer--;
            }
            if(fullPlayerCollection.get(player_Ids.get(i)).shotsToFire > 0) {
                newNumOfPlayer--;
            }
        }
        if(newNumOfPlayer != 0) {
            if(atLeastOneGotBurned) {
                hell_gates_bot.sendMessage(chat_id, "", "https://tenor.com/1e2v.gif",
                        "https://media.giphy.com/media/3ohjUVTmHNP3MKVLos/giphy.gif", "https://media.giphy.com/media/3o6fIUErcuwI011K5a/giphy.gif",
                        "https://tenor.com/3GWE.gif", "https://media.giphy.com/media/KuRbQrfkw9yco/giphy.gif");
            }
            StringBuilder msg = new StringBuilder();
            for(int i = 0; i < numberOfPlayers; i++) {
                boolean shouldRemove = false;
                if(fullPlayerCollection.get(player_Ids.get(i)).didGotShot) {
                    shouldRemove = true;
                    msg.append("@").append(fullPlayerCollection.get(player_Ids.get(i)).username)
                            .append("\nOne of your shots got burned. You won't join ")
                            .append("the next round. Your journey ends here..... \uD83D\uDE2D⚰️");
                }

                if(fullPlayerCollection.get(player_Ids.get(i)).shotsToFire > 0) {
                    shouldRemove = true;
                    msg.append("@").append(fullPlayerCollection.get(player_Ids.get(i)).username)
                            .append("\nYou didn't fire all the shots within time as ")
                            .append("instructed. You won't join the next round. Your journey ends here..... \uD83D\uDE2D⚰️");
                }

                if(shouldRemove) {
                    removePlayer(player_Ids.get(i));
                    i--;
                }
            }
            if(!msg.toString().equals("")) {
                hell_gates_bot.sendMessage(chat_id, msg.toString());
            }
            performProperWait(2);
            if(numberOfPlayers != 1) {
                hell_gates_bot.sendMessage(chat_id, fullPlayerCollection.getAllPlayerPing() + "\nYou all are the remaining " +
                                "players and shall play the next round","https://media.giphy.com/media/bb0Xwo6UoHTPy/giphy.gif",
                        "https://media.giphy.com/media/fxsqOYnIMEefC/giphy.gif", "https://media.giphy.com/media/cXRfaOcr2ZFE5BR5zv/giphy.gif",
                        "https://media.giphy.com/media/klQrJUcrfMsTK/giphy.gif", "https://media.giphy.com/media/3Cm8cxtSHqu6Q/giphy.gif",
                        "https://media.giphy.com/media/CNUb51EbTxuRG/giphy.gif", "https://media.giphy.com/media/KKB54xpucNE4M/giphy.gif",
                        "https://media.giphy.com/media/JWGgsu82QDoEE/giphy.gif", "https://media.giphy.com/media/MdLFOyVZtoUPm/giphy.gif");
                performProperWait(2);
                if(wasPrevMatchTwoPTie) {
                    shouldPlayMiniGame = true;
                } else if(numberOfPlayers == 2) {
                    wasPrevMatchTwoPTie = true;
                }
            } else {
                retBool[0] = true;
            }
        } else {
            boolean didAllGotBurned = true;
            boolean didNoneShootAll = false;
            boolean didAllShootAll = true;
            for(int i = 0; i < numberOfPlayers; i++) {
                didNoneShootAll = didNoneShootAll || fullPlayerCollection.get(player_Ids.get(i)).shotsToFire <= 0;
                didAllShootAll = didAllShootAll && fullPlayerCollection.get(player_Ids.get(i)).shotsToFire <= 0;
                if(fullPlayerCollection.get(player_Ids.get(i)).shotsToFire == 0) {
                    didAllGotBurned = didAllGotBurned && fullPlayerCollection.get(player_Ids.get(i)).didGotShot;
                }
            }
            didNoneShootAll = !didNoneShootAll;

            if(didNoneShootAll) {
                hell_gates_bot.sendMessage(chat_id, "None of you shoot all the shots. Therefore no one proceeds to the next round. " +
                        "There will be no winner for this round as everyone is disqualified.",
                        "https://media.giphy.com/media/kxfA8S9vf27zG/giphy.gif");
                performProperWait(2);
                retBool[1] = true;
            } else if(didAllShootAll) {
                if(didAllGotBurned) {
                    hell_gates_bot.sendMessage(chat_id, fullPlayerCollection.getAllPlayerPing() + "\n\nAt least one shot of each of " +
                            "you resulted into a burn. Therefore everyone is promoted to the next round.", "https://tenor.com/bcciU.gif");
                    if(wasPrevMatchTwoPTie) {
                        shouldPlayMiniGame = true;
                    }
                    wasPrevMatchTwoPTie = true;
                } else {
                    hell_gates_bot.sendMessage(chat_id, "Bot Encountered an Error");
                    retBool[1] = true;
                }
                performProperWait(2);
            } else if(didAllGotBurned){
                hell_gates_bot.sendMessage(chat_id, "", "https://media.giphy.com/media/nPvD0gcvSvMIg/giphy.gif");
                StringBuilder msg = new StringBuilder();
                for(int i = 0; i < numberOfPlayers; i++) {
                    if(fullPlayerCollection.get(player_Ids.get(i)).shotsToFire > 0) {
                        msg.append("@").append(fullPlayerCollection.get(player_Ids.get(i)).username)
                                .append("\nYou didn't fire all the shots within time as ")
                                .append("instructed. You won't join the next round. Your journey ends here..... T_T");
                        removePlayer(player_Ids.get(i));
                        i--;
                    }
                }
                if(!msg.toString().equals("")) {
                    hell_gates_bot.sendMessage(chat_id, msg.toString());
                }
                performProperWait(2);
                hell_gates_bot.sendMessage(chat_id, fullPlayerCollection.getAllPlayerPing() + "\nYou all are remaining players " +
                                "and at least one shot of each of you got burned. Therefore all of you, the remaining players will be " +
                                "promoted to the next round.","https://media.giphy.com/media/f4PP2LvvI3AVuw7mnt/giphy.gif");
                performProperWait(2);
            } else {
                hell_gates_bot.sendMessage(chat_id, "Bot Encountered an Error");
                retBool[1] = true;
            }
        }
        return retBool;
    }

    // By the end of this function, 2 player ties would have been resolved and one single winner would be decided
    private boolean playMiniGame() {
        numberOfTransactionsExamined = numberOfTransactionsFetched.get();
        hell_gates_bot.sendMessage(chat_id, "As we have only 2 players left who have tied twice, we will resolve the winner with" +
                " a mini game.\n\nIn each round, both of the players have to fire 2️⃣ shots \uD83D\uDCA5\uD83D\uDD2B each. After both of you" +
                " have fired your shots, if exactly one of you gets shots, he/she will lose and other person will be declared as winner," +
                " otherwise both players play another round.");
        performProperWait(3);
        hell_gates_bot.sendMessage(chat_id, "All players should GET READY to fire their shots \uD83D\uDCA5\uD83D\uDD2B.\n\n" +
                "1 Shot \uD83D\uDCA5\uD83D\uDD2B means 1 transaction of RTK tokens. Each shots has to be of "
                + shotCost.divide(new BigInteger("1000000000000000000")) + " RTK. Shots must be fired from your REGISTERED " +
                "wallet to the following wallet one by one :", "https://tenor.com/wykJ.gif");

        hell_gates_bot.sendMessage(chat_id, shotWallet);
        performProperWait(1);
        hell_gates_bot.sendMessage(chat_id, "To save time and transaction cost, it is recommended to make all transactions " +
                "in quick successions from a wallet that covers transaction fees. Both players will get ⏳ 5 minutes per round to " +
                "fire the shots \uD83D\uDCA5\uD83D\uDD2B. Failing to fire all the shots within time will result in loss. Good Luck!!");
        performProperWait(1);

        int roundNumber = 1;

        while (true) {
            performProperWait(2);
            hell_gates_bot.sendMessage(chat_id, "Mini Game Round : " + roundNumber);
            fullPlayerCollection.get(player_Ids.get(0)).shotsToFire = 2;
            fullPlayerCollection.get(player_Ids.get(1)).shotsToFire = 2;
            buildNewConnectionToEthereumNetwork(true);
            hell_gates_bot.sendMessage(chat_id, "Both players, please fire 2️⃣ shots \uD83D\uDCA5\uD83D\uDD2B each within ⏳ 5 minutes");
            Instant trxEndTime = Instant.now().plus(5, ChronoUnit.MINUTES);
            Instant halfTime = trxEndTime.minus(2, ChronoUnit.MINUTES), 
                    quarterTime = trxEndTime.minus(30, ChronoUnit.SECONDS);
            int halfValue = 2;
            int quarterValue = 30;
            boolean halfWarn = true, quarterWarn = true;

            int totalNumberOfShotsToBeFired = 0;
            for(int i = 0; i < numberOfPlayers; i++) {
                fullPlayerCollection.get(player_Ids.get(i)).didGotShot = false;
                totalNumberOfShotsToBeFired += fullPlayerCollection.get(player_Ids.get(i)).shotsToFire;
            }
            
            while(Instant.now().compareTo(trxEndTime) < 0) {
                if(halfWarn) {
                    if (Instant.now().compareTo(halfTime) > 0) {
                        hell_gates_bot.sendMessage(chat_id, fullPlayerCollection.getAllPlayerPing() + "\n" + "⏳ " + halfValue 
                                + " Minutes left.", "https://media.giphy.com/media/o2KLYPem407CM/giphy.gif");
                        halfWarn = false;
                    }
                }
                if(quarterWarn) {
                    if (Instant.now().compareTo(quarterTime) > 0) {
                        hell_gates_bot.sendMessage(chat_id, fullPlayerCollection.getAllPlayerPing() + "\n" + "⏳ " + quarterValue 
                                + " seconds left.");
                        quarterWarn = false;
                    }
                }

                while (numberOfTransactionsExamined < numberOfTransactionsFetched.get()) {
                    try {
                        TransactionData transactionData = splitInputData(flowableTransactionLog.get(numberOfTransactionsExamined),
                                allRTKTransactions.get(numberOfTransactionsExamined));
                        boolean condition = !transactionData.methodName.equalsIgnoreCase("Useless") &&
                                transactionData.toAddress.equalsIgnoreCase(shotWallet) &&
                                transactionData.value.compareTo(shotCost) >= 0 &&
                                fullPlayerCollection.containsKey(transactionData.fromAddress);
                        if(condition) {
                            if(fullPlayerCollection.get(transactionData.fromAddress).shotsToFire > 0) {
                                totalNumberOfShotsToBeFired--;
                                fullPlayerCollection.get(transactionData.fromAddress).shotsToFire--;
                                if(transactionData.didBurn) {
                                    fullPlayerCollection.get(transactionData.fromAddress).didGotShot = true;
                                }
                            }
                        }
                        numberOfTransactionsExamined++;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if(totalNumberOfShotsToBeFired <= 0) {
                    hell_gates_bot.sendMessage(chat_id, fullPlayerCollection.getAllPlayerPing() + "\n\nShots \uD83D\uDCA5\uD83D\uDD2B" +
                            " of all players has been confirmed. Annnnnnnnnnnd the results are..........",
                            "https://media.giphy.com/media/YmZOBDYBcmWK4/giphy.gif");
                    performProperWait(2);
                    break;
                }
            }
            disposable.dispose();

            int playersWhoShotAll = 0;
            int playersWhoGotBurned = 0;
            int idxOfBurned = -1;
            int idxOfPlayerWhoShotAll = -1;
            for(int i = 0; i < numberOfPlayers; i++) {
                if(fullPlayerCollection.get(player_Ids.get(i)).shotsToFire == 0) {
                    idxOfPlayerWhoShotAll = i;
                    playersWhoShotAll++;
                }
                if(fullPlayerCollection.get(player_Ids.get(i)).didGotShot) {
                    playersWhoGotBurned++;
                    idxOfBurned = i;
                }
            }

            if(playersWhoShotAll == 0) {
                hell_gates_bot.sendMessage(chat_id, "None of you fired all the shots. Therefore both of you are disqualified. " +
                        "No winner in this game");
                return false;
            } else if(playersWhoShotAll == 1) {
                hell_gates_bot.sendMessage(chat_id, "Only @" + fullPlayerCollection.get(player_Ids.get(idxOfPlayerWhoShotAll))
                        .username + " has shot all the shots.");
                int idxOfPlayerWhoDidNotShootAll = Math.abs((1-idxOfPlayerWhoShotAll));
                removePlayer(idxOfPlayerWhoDidNotShootAll);
                return true;
            } else {
                if(playersWhoGotBurned == 0) {
                    hell_gates_bot.sendMessage(chat_id, "No shot got burned. Both of you will play next round of mini game.");
                } else if(playersWhoGotBurned == 1) {
                    hell_gates_bot.sendMessage(chat_id, "@" + fullPlayerCollection.get(player_Ids.get(playersWhoGotBurned))
                            .username + " one of your shots got burned. You have lost.");
                    removePlayer(idxOfBurned);
                    return true;
                } else {
                    hell_gates_bot.sendMessage(chat_id, "At least 1 shot of each of you got burned. Therefore both of you will " +
                            "play next round of mini Game.");
                }
            }
            roundNumber++;
        }
    }




    // Related to working of game mechanics
    public int getGameInitiator() {
        return gameInitiator;
    }

    public boolean addPlayer(User player, String addy) {
        if(!fullPlayerCollection.containsKey(player.getId())) {
            PlayerData newPlayerData = new PlayerData();
            newPlayerData.playerId = player.getId();
            newPlayerData.addy = addy.toLowerCase();
            newPlayerData.didPlayerPay = false;
            newPlayerData.username = player.getUserName();
            newPlayerData.shotsToFire = 0;
            fullPlayerCollection.put(player.getId(), newPlayerData);
            numberOfPlayers++;
            return true;
        } else {
            return false;
        }
    }

    public void removePlayer(int playerId) {
        if(player_Ids.contains(playerId)) {
            hell_gates_bot.playerRemovedFromGame(player_Ids.remove(fullPlayerCollection.remove(playerId).playerId));
            numberOfPlayers--;
        }
    }
    
    public void removeAllPlayersAndDeleteTheGame() {
        hell_gates_bot.deleteGame(chat_id);
        int num = numberOfPlayers;
        for(int i = 0; i < num; i++) {
            removePlayer(player_Ids.get(0));
        }
    }

    public boolean beginGame() {
        if(numberOfPlayers >= minimumNumberOfPlayers) {
            hasGameStarted = true;
            return true;
        } else {
            return false;
        }
    }

    public boolean payWithTicketForThisUser(int playerId) {
        if(isAcceptingEntryPayment) {
            if(player_Ids.contains(playerId)) {
                if(fullPlayerCollection.get(playerId).didPlayerPay) {
                    hell_gates_bot.sendMessage(chat_id, "@" + fullPlayerCollection.get(playerId).username +
                            " Cannot pay with tickets. Your entry payment has already been confirmed.");
                    return false;
                } else {
                    fullPlayerCollection.get(playerId).didPlayerPay = true;
                    hell_gates_bot.sendMessage(chat_id, "@" + fullPlayerCollection.get(playerId).username +
                            " Pay with ticket successful");
                    return true;
                }
            } else {
                hell_gates_bot.sendMessage(chat_id, "@" + fullPlayerCollection.get(playerId).username +
                        " You are not part of the current game. Cannot pay with tickets for anything.");
                return false;
            }
        } else {
            hell_gates_bot.sendMessage(chat_id, "@" + fullPlayerCollection.get(playerId).username +
                    " The game is not accepting any entry payment at the moment");
            return false;
        }
    }

    public void acceptNewResponse(CallbackResponse callbackResponse) {
        if(isLookingForPlayerResponses) {
            callbackResponses.add(callbackResponse);
        } else {
            callbackResponse.responseNotNeeded();
        }
    }

    public void performProperWait(float seconds) {
        try {
            Thread.sleep((long)(seconds * 1000f));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    // Related to Blockchain Communication
    private TransactionData splitInputData(Log log, Transaction transaction) throws Exception {
        String inputData = transaction.getInput();
        TransactionData currentTransactionData = new TransactionData();
        String method = inputData.substring(0, 10);
        currentTransactionData.methodName = method;

        // If method is transfer method
        if(method.equalsIgnoreCase("0xa9059cbb")) {
            currentTransactionData.fromAddress = transaction.getFrom().toLowerCase();
            String topic = log.getTopics().get(0);
            if(topic.equalsIgnoreCase("0x897c6a07c341708f5a14324ccd833bbf13afacab63b30bbd827f7f1d29cfdff4")) {
                currentTransactionData.didBurn = true;
            } else if(topic.equalsIgnoreCase("0xe7d849ade8c22f08229d6eec29ca84695b8f946b0970558272215552d79076e6")) {
                currentTransactionData.didBurn = false;
            }
            Method refMethod = TypeDecoder.class.getDeclaredMethod("decode",String.class,int.class,Class.class);
            refMethod.setAccessible(true);
            Address toAddress = (Address) refMethod.invoke(null,inputData.substring(10, 74),0,Address.class);
            Uint256 amount = (Uint256) refMethod.invoke(null,inputData.substring(74),0,Uint256.class);
            currentTransactionData.toAddress = toAddress.toString().toLowerCase();
            currentTransactionData.value = amount.getValue();
        } else {
            currentTransactionData.methodName = "Useless";
        }
        return currentTransactionData;
    }

    private boolean buildNewConnectionToEthereumNetwork(boolean shouldSendMessage) {

        int count = 0;
        if(shouldSendMessage) {
            hell_gates_bot.sendMessage(chat_id, "Connecting to Ethereum Network to read transactions. Please be patient. This can take from few" +
                    " seconds to few minutes");
        }
        allRTKTransactions = new ArrayList<>();
        flowableTransactionLog = new ArrayList<>();
        numberOfTransactionsFetched = new AtomicInteger(0);
        numberOfTransactionsExamined = 0;
        if(disposable != null) {
            disposable.dispose();
            webSocketService.close();
            System.out.println("Reconnecting to Web3");
        } else {
            System.out.println("Connecting to Web3");
        }
        shouldTryToEstablishConnection = true;
        while (shouldTryToEstablishConnection && count < 6) {
            count++;
            try {
                Collections.shuffle(webSocketUrls);
                shouldTryToEstablishConnection = false;
                WebSocketClient webSocketClient = new WebSocketClient(new URI(webSocketUrls.get(0))) {
                    @Override
                    public void onClose(int code, String reason, boolean remote) {
                        super.onClose(code, reason, remote);
                        logger.info(chat_id + " : WebSocket connection to " + uri + " closed successfully " + reason);
                    }

                    @Override
                    public void onError(Exception e) {
                        super.onError(e);
                        e.printStackTrace();
                        setShouldTryToEstablishConnection();
                        logger.error(chat_id + " : WebSocket connection to " + uri + " failed with error");
                        System.out.println("Trying again");
                    }
                };
                webSocketService = new WebSocketService(webSocketClient, true);
                webSocketService.connect();
            } catch (Exception e) {
                e.printStackTrace();
            }
            performProperWait(2);
        }

        try {
            web3j =  Web3j.build(webSocketService);
            Web3ClientVersion web3ClientVersion = web3j.web3ClientVersion().send();
            System.out.println("Game's Chat ID : " + chat_id + "\nWeb3ClientVersion : " + web3ClientVersion.getWeb3ClientVersion());
//            ClientTransactionManager transactionManager = new ClientTransactionManager(web3j, RTKContractAddress);
//            ERC20 RTKToken = ERC20.load(RTKContractAddress, web3j, transactionManager, new ContractGasProvider() {
//                @Override
//                public BigInteger getGasPrice(String s) {
//                    return BigInteger.valueOf(45L);
//                }
//
//                @Override
//                public BigInteger getGasPrice() {
//                    return BigInteger.valueOf(45L);
//                }
//
//                @Override
//                public BigInteger getGasLimit(String s) {
//                    return BigInteger.valueOf(65000L);
//                }
//
//                @Override
//                public BigInteger getGasLimit() {
//                    return BigInteger.valueOf(65000L);
//                }
//            });
            EthFilter RTKContractFilter = new EthFilter(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST, RTKContractAddress);
            // This statement will receive all transactions that have been made and as they are made on our provided contract address in the filter
            disposable = web3j.ethLogFlowable(RTKContractFilter).subscribe(log -> {
                if(numberOfTransactionsFetched.get() != 0) {
                    String hash = log.getTransactionHash(); // Here we obtain transaction hash of transaction from the log that we get from subscribe
                    String prevHash = flowableTransactionLog.get(numberOfTransactionsFetched.get() - 1).getTransactionHash();

                    /*The reason as to why we are comparing to prev hash is that a lot of transactions (which includes all transfer transactions),
                     * there will be two logs for each under same transaction. 1st log (for transfer transaction) will be for shot or survive and 2nd
                     * log will be for transfer. It is very important to understand this for further use of these logs.*/
                    if(!hash.equals(prevHash)) {
                        flowableTransactionLog.add(log);
                        System.out.println("Chat ID : " + chat_id + " - Trx :  " + log.getTransactionHash());
                        Optional<Transaction> trx = web3j.ethGetTransactionByHash(hash).send().getTransaction();
                        trx.ifPresent(transaction -> allRTKTransactions.add(transaction));
                        numberOfTransactionsFetched.getAndIncrement();
                    }
                } else {
                    String hash = log.getTransactionHash();
                    flowableTransactionLog.add(log);
                    System.out.println("Chat ID : " + chat_id + " - Trx :  " + log.getTransactionHash());
                    Optional<Transaction> trx = web3j.ethGetTransactionByHash(hash).send().getTransaction();
                    trx.ifPresent(transaction -> allRTKTransactions.add(transaction));
                    numberOfTransactionsFetched.getAndIncrement();
                }
            }, throwable -> {
                throwable.printStackTrace();
                webSocketService.close();
                webSocketService.connect();
            });
            System.out.println("\n\n\n\n\n\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return !shouldTryToEstablishConnection;
    }



    private void sendRewardToWinner(BigInteger amount, String toAddress) {
        while (shouldTryToEstablishConnection) {
            try {
                Collections.shuffle(webSocketUrls);
                shouldTryToEstablishConnection = false;
                WebSocketClient webSocketClient = new WebSocketClient(new URI(webSocketUrls.get(0))) {
                    @Override
                    public void onClose(int code, String reason, boolean remote) {
                        super.onClose(code, reason, remote);
                        logger.info("WebSocket connection to " + uri + " closed successfully " + reason);
                    }

                    @Override
                    public void onError(Exception e) {
                        super.onError(e);
                        logger.error("WebSocket connection to " + uri + " failed with error");
                        e.printStackTrace();
                        System.out.println("Trying again");
                        setShouldTryToEstablishConnection();
                    }
                };
                webSocketService = new WebSocketService(webSocketClient, true);

                webSocketService.connect();
            } catch (Exception e) {
                e.printStackTrace();
            }
            performProperWait(2);
        }

        try {
            web3j = Web3j.build(webSocketService);
            Web3ClientVersion web3ClientVersion = web3j.web3ClientVersion().send();
            System.out.println("Game's Chat ID : " + chat_id + "\nWeb3ClientVersion : " + web3ClientVersion.getWeb3ClientVersion());
            TransactionReceipt trxReceipt = ERC20.load(RTKContractAddress, web3j, Credentials.create(System.getenv("PrivateKey")), new ContractGasProvider() {
                @Override
                public BigInteger getGasPrice(String s) {
                    return gasPrice.multiply(new BigInteger("1000000000"));
                }

                @Override
                public BigInteger getGasPrice() {
                    return gasPrice.multiply(new BigInteger("1000000000"));
                }

                @Override
                public BigInteger getGasLimit(String s) {
                    return BigInteger.valueOf(65000L);
                }

                @Override
                public BigInteger getGasLimit() {
                    return BigInteger.valueOf(65000L);
                }
            }).transfer(toAddress, amount.divide(new BigInteger("1000000000000000000"))).send();
            System.out.println(trxReceipt.getTransactionHash());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean hasEnoughBalance() {
        boolean retVal = false;
        while (shouldTryToEstablishConnection) {
            try {
                Collections.shuffle(webSocketUrls);
                shouldTryToEstablishConnection = false;
                WebSocketClient webSocketClient = new WebSocketClient(new URI(webSocketUrls.get(0))) {
                    @Override
                    public void onClose(int code, String reason, boolean remote) {
                        super.onClose(code, reason, remote);
                        logger.info("WebSocket connection to " + uri + " closed successfully " + reason);
                    }

                    @Override
                    public void onError(Exception e) {
                        super.onError(e);
                        logger.error("WebSocket connection to " + uri + " failed with error");
                        e.printStackTrace();
                        System.out.println("Trying again");
                        setShouldTryToEstablishConnection();
                    }
                };
                webSocketService = new WebSocketService(webSocketClient, true);

                webSocketService.connect();
            } catch (Exception e) {
                e.printStackTrace();
            }
            performProperWait(1);
        }

        try {
            web3j = Web3j.build(webSocketService);
            Web3ClientVersion web3ClientVersion = web3j.web3ClientVersion().send();
            System.out.println("Game's Chat ID : " + chat_id + "\nWeb3ClientVersion : " + web3ClientVersion.getWeb3ClientVersion());
            gasPrice = web3j.ethGasPrice().send().getGasPrice();
            BigInteger balance = web3j.ethGetBalance(ourWallet, DefaultBlockParameterName.LATEST).send().getBalance();
            minGasFees = new BigInteger((gasPrice.multiply(new BigInteger("195000")).toString()));
            System.out.println("Network type = " + EthNetworkType + ", Wallet Balance = " + balance + ", Required Balance = " + minGasFees);
            if(balance.compareTo(minGasFees) > 0) {
                retVal = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return retVal;
    }

    private void setShouldTryToEstablishConnection() {
        shouldTryToEstablishConnection = true;
    }
}