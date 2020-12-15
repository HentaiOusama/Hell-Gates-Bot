import io.reactivex.disposables.Disposable;
import org.apache.log4j.Logger;
import org.web3j.abi.TypeDecoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.Web3ClientVersion;
import org.web3j.protocol.websocket.WebSocketClient;
import org.web3j.protocol.websocket.WebSocketService;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class TicketBuyer {
    Hell_Gates_Bot hell_gates_bot;
    long chat_id;
    int numberOfTicketsToBuy;
    String addyOfPlayer;
    final String EthNetworkType;
    Logger logger = Logger.getLogger(TicketBuyer.class);
    boolean shouldTryToEstablishConnection = true;

    final String RTKContractAddress;
    final String ourWallet;
    final BigInteger TicketCost;
    final BigInteger TotalAmountToSpend;
    private final ArrayList<String> webSocketUrls = new ArrayList<>();
    private WebSocketService webSocketService;
    private Web3j web3j;
    private Disposable disposable = null;
    private ArrayList<Transaction> allRTKTransactions = new ArrayList<>();
    private ArrayList<Log> flowableTransactionLog = new ArrayList<>();
    private AtomicInteger numberOfTransactionsFetched = new AtomicInteger(0);
    private int numberOfTransactionsExamined = 0;

    TicketBuyer(Hell_Gates_Bot hell_gates_bot, long chat_id, int numberOfTicketsToBuy, String addyOfPlayer, String EthNetworkType, String ourWallet,
                String RTKContractAddress, BigInteger TicketCost) {

        webSocketUrls.add("wss://" + EthNetworkType + ".infura.io/ws/v3/04009a10020d420bbab54951e72e23fd");
        webSocketUrls.add("wss://" + EthNetworkType + ".infura.io/ws/v3/94fead43844d49de833adffdf9ff3993");
        webSocketUrls.add("wss://" + EthNetworkType + ".infura.io/ws/v3/b8440ab5890a4d539293994119b36893");
        webSocketUrls.add("wss://" + EthNetworkType + ".infura.io/ws/v3/b05a1fe6f7b64750a10372b74dec074f");
        webSocketUrls.add("wss://" + EthNetworkType + ".infura.io/ws/v3/2e98f2588f85423aa7bced2687b8c2af");

        this.chat_id = chat_id;
        this.EthNetworkType = EthNetworkType;
        this.hell_gates_bot = hell_gates_bot;
        this.numberOfTicketsToBuy = numberOfTicketsToBuy;
        this.addyOfPlayer = addyOfPlayer;
        this.ourWallet = ourWallet;
        this.RTKContractAddress = RTKContractAddress;
        this.TicketCost = TicketCost;
        TotalAmountToSpend = TicketCost.multiply(new BigInteger(Integer.toString(numberOfTicketsToBuy)));
        thread.start();
    }

    Thread thread = new Thread() {
        @Override
        public void run() {
            super.run();
            boolean val = buildNewConnectionToEthereumNetwork(true);

            if(!val) {
                hell_gates_bot.sendMessage(chat_id, "There was an error while purchasing the tickets. Please try again later.");
                hell_gates_bot.playerTicketPurchaseEnded((int) chat_id, numberOfTicketsToBuy, false, EthNetworkType + "Tickets");
                try {
                    join();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            System.out.println("\n\n\n\nIn run = " + val + "\n\n\n\n");
            hell_gates_bot.sendMessage(chat_id, "You must send " + TotalAmountToSpend.divide(new BigInteger("1000000000000000000")) +
                    " RTK within 7 minutes from your REGISTERED wallet to the following address : \n\n");
            hell_gates_bot.sendMessage(chat_id, ourWallet);
            Instant paymentEndInstant = Instant.now().plus(7, ChronoUnit.MINUTES);

            boolean didPay = false;

            OUTER :
            while (Instant.now().compareTo(paymentEndInstant) < 0) {
                while (numberOfTransactionsExamined < numberOfTransactionsFetched.get() && numberOfTransactionsFetched.get() != 0) {
                    try {
                        TransactionData transactionData = splitInputData(flowableTransactionLog.get(numberOfTransactionsExamined),
                                allRTKTransactions.get(numberOfTransactionsExamined));
                        boolean condition = !transactionData.methodName.equalsIgnoreCase("Useless") &&
                                transactionData.toAddress.equalsIgnoreCase(ourWallet) &&
                                transactionData.value.compareTo(TotalAmountToSpend) >= 0 &&
                                addyOfPlayer.equalsIgnoreCase(transactionData.fromAddress);
                        if(condition) {
                            didPay = true;
                            break OUTER;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    numberOfTransactionsExamined++;
                }
                wait500ms();
                wait500ms();
            }
            disposable.dispose();

            if(didPay) {
                hell_gates_bot.sendMessage(chat_id, "Payment confirmed. Successfully added tickets to your account");
                hell_gates_bot.playerTicketPurchaseEnded((int) chat_id, numberOfTicketsToBuy, true, EthNetworkType + "Tickets");
            } else {
                hell_gates_bot.sendMessage(chat_id, "We were unable to confirm you payment. This purchase has been canceled");
                hell_gates_bot.playerTicketPurchaseEnded((int) chat_id, numberOfTicketsToBuy, false, EthNetworkType + "Tickets");
            }

        }
    };

    public boolean buildNewConnectionToEthereumNetwork(boolean shouldSendMessage) {
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
            wait500ms();
            wait500ms();
            wait500ms();
            wait500ms();
        }

        try {
            web3j =  Web3j.build(webSocketService);
            Web3ClientVersion web3ClientVersion = web3j.web3ClientVersion().send();
            System.out.println("Game's Chat ID : " + chat_id + "\nWeb3ClientVersion : " + web3ClientVersion.getWeb3ClientVersion());
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

    public TransactionData splitInputData(Log log, Transaction transaction) throws Exception {
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

    public void wait500ms() {
        try {
            Thread.sleep(500);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setShouldTryToEstablishConnection() {
        shouldTryToEstablishConnection = true;
    }
}
