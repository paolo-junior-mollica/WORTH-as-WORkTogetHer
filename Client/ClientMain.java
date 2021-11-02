import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.google.gson.Gson;

/**
 * Client class
 * @author Paolo Junior Mollica
 */
public class ClientMain {

    /** L'utente che fa il login */
    private static User user;

    /** Lock per l'oggetto user */
    private static final ReentrantReadWriteLock userLock = new ReentrantReadWriteLock();

    /** Porta per la connessione al registry del server */
    private static final int registryPort = 6789;

    /** Porta per la connessione TCP con il server */
    private static final int TCPport = 7890;

    /** 
     * Una lista di threads, uno per ogni progetto di user, che si occupano di
     * ricevere i messaggi della rispettiva chat 
     */
    private static final ArrayList<Thread> sniffers = new ArrayList<>();

    /** Il socket channel utilizzato per la comunicazione TCP con il server */
    private static SocketChannel socketChannel;

    /** L'oggetto esportato dal client per ricevere notifiche asincrone (callback) */
    private static final NotifyEventImpl callbackObj = new NotifyEventImpl();

    /** Lo stub dell'oggetto remoto */
    private static NotifyEventInterface stub;

    /** Riferimento all'oggetto remoto del server */
    private static WorthInterface serverStub;

    /**
     * Main method
     */
    public static void main(String[] args) {
        // Hook per effettuare il logout in caso di un'interruzione dell'utente, come un CTRL-C
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                userLock.writeLock().lock();
                    if (user != null && user.isOnline())
                        logout(user.getNickname());
                userLock.writeLock().unlock();
            }
        });
        // Inizializzazione di un BufferedReader per la lettura dei comandi da terminale
        InputStreamReader streamReader = new InputStreamReader(System.in);
        BufferedReader bufferedReader = new BufferedReader(streamReader);
        try {
            // Prendo il riferimento all'oggetto remoto del server
            Registry registry = LocateRegistry.getRegistry(registryPort);
            serverStub = (WorthInterface) registry.lookup("WORTH-SERVER");
            // Apertura del socket channel
            socketChannel = SocketChannel.open();
            // Messaggio di benvenuto
            System.out.println("****\tBenvenuto in WORTH!");
            System.out.println("\n****\t\tAccedi per utilizzare WORTH. Se non hai un account registrati!");
            String operazione;
            boolean isLoggedIn = false;
            // Finché l'utente non esegue un login con successo
            while (!isLoggedIn) {
                System.out.println("****\t\tDigita \"help\" per vedere la lista dei comandi disponibili.");
                System.out.print("****\t\t> ");
                // Lettura dell'operazione
                operazione = bufferedReader.readLine();
                // Suddivisione dell'operazione nelle singole parole di cui è composta
                String[] words = operazione.split(" ");
                // A seconda del tipo di comando (contenuto in words[0]) faccio delle diverse operazioni
                switch (words[0]) {
                    case "help" -> {
                        if (words.length != 1) {
                            displayLine("Il comando help non deve avere argomenti.");
                            break;
                        }
                        // Stampa dei comandi disponibili con la relativa descrizione
                        help();
                    }

                    case "register" -> {
                        if (words.length != 3) {
                            displayLine("Il comando register deve avere due argomenti.");
                            break;
                        }
                        String nickname = words[1], password = words[2];
                        // Fase di registrazione gestita dall'oggetto remoto del server. Restituisce un valore
                        // di Replies che informa sull'esito della registrazione
                        Replies reply = serverStub.register(nickname, password);
                        if (reply == Replies.OK)
                            displayLine("Utente " + nickname + " registrato con successo!");
                        else
                            displayLine("Impossibile registrarsi: l'utente " + nickname + " esiste già.");
                    }

                    case "login" -> {
                        if (words.length != 3) {
                            displayLine("Il comando login deve avere due argomenti.");
                            break;
                        }
                        // Potrebbe essersi già connesso nel caso di una login non riuscita
                        if (!socketChannel.isConnected())
                            socketChannel.connect(new InetSocketAddress("127.0.0.1", TCPport));
                        String nickname = words[1], password = words[2];
                        // Il metodo di login restituisce l'oggetto user
                        user = login(nickname, password);
                        // Se l'oggetto restituito dalla login è diverso da null, allora l'operazione è andata a buon fine
                        if (user != null) {
                            // esco dal loop
                            isLoggedIn = true;
                            // esportazione dell'oggetto remoto del client
                            stub = (NotifyEventInterface) UnicastRemoteObject.exportObject(callbackObj, 0);
                            // registrazione alle callback per ricevere gli aggiornamenti sulla lista di utenti registrati
                            serverStub.registerForCallback(stub);
                        }
                    }

                    default -> displayLine("Comando non disponibile.");
                }
            }
            /* Login effettuata */
            // Finché l'utente è online (ovvero finché non esegue un'operazione di logout)
            while (user.isOnline()) {
                System.out.println("****\t\tDigita \"help\" per vedere una lista dei comandi disponibili.");
                System.out.print("****\t\t> ");
                // Lettura dell'operazione
                operazione = bufferedReader.readLine();
                // Suddivisione dell'operazione nelle singole parole di cui è composta
                String[] words = operazione.split(" ");
                // A seconda del tipo di comando (contenuto in words[0]) faccio delle diverse operazioni
                switch (words[0]) {
                    case "help" -> {
                        if (words.length != 1) {
                            displayLine("Il comando help non deve avere argomenti.");
                            break;
                        }
                        // Stampa dei comandi disponibili con la relativa descrizione (diversi dalla fase precedente)
                        help();
                    }

                    case "logout" -> {
                        if (words.length != 2) {
                            displayLine("Il comando logout deve avere un argomento.");
                            break;
                        }
                        // Logout, con conseguente uscita dal ciclo e terminazione del programma
                        logout(words[1]);
                    }

                    case "list_users" -> {
                        if (words.length != 1) {
                            displayLine("Il comando list_users non deve avere argomenti.");
                            break;
                        }
                        // Stampa della lista degli utenti registrati a Worth
                        listUsers();
                    }

                    case "list_online_users" -> {
                        if (words.length != 1) {
                            displayLine("Il comando list_online_users non deve avere argomenti.");
                            break;
                        }
                        // Stampa della lista degli utenti online
                        listOnlineUsers();
                    }

                    case "list_projects" -> {
                        if (words.length != 1) {
                            displayLine("Il comando list_projects non deve avere argomenti.");
                            break;
                        }
                        // Stampa della lista di cui l'utente è membro
                        listProjects();
                    }

                    case "create_project" -> {
                        if (words.length != 2) {
                            displayLine("Il comando create_project deve avere un argomento.");
                            break;
                        }
                        String projectName = words[1];
                        // Creazione di un nuovo progetto
                        createProject(projectName);
                    }

                    case "add_member" -> {
                        if (words.length != 3) {
                            displayLine("Il comando add_member deve avere due argomenti.");
                            break;
                        }
                        String projectName = words[1], nickname = words[2];
                        // Aggiunta di un nuovo membro ad un progetto
                        addMember(projectName, nickname);
                    }

                    case "show_members" -> {
                        if (words.length != 2) {
                            displayLine("Il comando show_members deve avere un argomento.");
                            break;
                        }
                        String projectName = words[1];
                        // Stampa della lista dei membri del progetto
                        showMembers(projectName);
                    }

                    case "show_cards" -> {
                        if (words.length != 2) {
                            displayLine("Il comando show_cards deve avere un argomento.");
                            break;
                        }
                        String projectName = words[1];
                        // Stampa della lista delle carte del progetto
                        showCards(projectName);
                    }

                    case "show_card" -> {
                        if (words.length != 3) {
                            displayLine("Il comando show_card deve avere due argomenti.");
                            break;
                        }
                        String projectName = words[1], cardName = words[2];
                        // Stampa delle informazioni sulla carta scelta
                        showCard(projectName, cardName);
                    }

                    case "add_card" -> {
                        if (words.length != 4) {
                            displayLine("Il comando add_card deve avere tre argomenti.");
                            break;
                        }
                        String projectName = words[1], cardName = words[2], description = words[3];
                        // Aggiunta di una carta al progetto
                        addCard(projectName, cardName, description);
                    }

                    case "move_card" -> {
                        if (words.length != 5) {
                            displayLine("Il comando move_card deve avere quattro argomenti.");
                            break;
                        }
                        String projectName = words[1], cardName = words[2], sourceList = words[3], destList = words[4];
                        // Spostamento di una carta da una lista del progetto a un'altra
                        moveCard(projectName, cardName, sourceList, destList);
                    }

                    case "get_card_history" -> {
                        if (words.length != 3) {
                            displayLine("Il comando get_card_history deve avere due argomenti.");
                            break;
                        }
                        String projectName = words[1], cardName = words[2];
                        // Stampa della storia della carta
                        getCardHistory(projectName, cardName);
                    }

                    case "send" -> {
                        if (words.length < 3) {
                            displayLine("Il comando send deve avere come argomenti il nome del progetto seguito dal messaggio.");
                            break;
                        }
                        String projectName = words[1];
                        StringBuilder message = new StringBuilder();
                        for (int i = 2; i < words.length; i++) {
                            message.append(words[i]);
                            if (i < words.length - 1)
                                message.append(" ");
                        }
                        // Invio di un messaggio sulla chat del progetto
                        sendChatMsg(projectName, message.toString());
                    }

                    case "receive" -> {
                        if (words.length != 2) {
                            displayLine("Il comando receive deve avere un argomento.");
                            break;
                        }
                        String projectName = words[1];
                        // Lettura dei messaggi arrivati sulla chat del progetto
                        readChat(projectName);
                    }

                    case "cancel_project" -> {
                        if (words.length != 2) {
                            displayLine("Il comando show_cards deve avere un argomento.");
                            break;
                        }
                        String projectName = words[1];
                        // Cancellazione di un progetto
                        cancelProject(projectName);
                    }

                    default -> displayLine("Comando non disponibile.");
                }
            }
            /* Logout effettuata */
            System.out.println("****\t\tGrazie per aver usato WORTH. Arrivederci!");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NotBoundException e) {
            System.err.println("\nErrore nella connessione al registry del server\n");
            e.printStackTrace();
        } finally {     // L'operazione di logout deve essere eseguita in ogni caso alla terminazione del programma
            userLock.writeLock().lock();
                if (user != null && user.isOnline())
                    logout(user.getNickname());
            userLock.writeLock().unlock();
        }
    }

    /**
     * Metodo getter
     *
     * @return il riferimento all'oggetto di tipo User dell'utente che
     *         ha fatto la login
     */
    public static User getUser() {
        return user;
    }

    /**
     * Metodo getter
     *
     * @return il riferimento alla lock per l'utente
     */
    public static ReentrantReadWriteLock getUserLock() {
        return userLock;
    }

    /**
     * Operazione di login: effettua la richiesta al server, il quale risponde
     * con un valore di Replies che indica se l'operazione è andata a buon fine oppure
     * che tipo di errore si è verificato, e con l'oggetto di tipo User relativo
     * all'utente che ha richiesto l'operazione. Stampa a schermo un messaggio che
     * dipende dall'esito dell'operazione (e quindi dal valore di Replies ricevuto).
     *
     * @param nickname identificativo dell'utente (il nome con cui si è registrato)
     * @param password la password collegata al nickname nel momento della registrazione
     * @return il riferimento all'oggetto di tipo User dell'utente in caso di successo,
     *         null in caso di fallimento
     */
    private static User login(String nickname, String password) {
        try {
            // Costruzione del messaggio da inviare al server
            ClientServerMessage message = new ClientServerMessage(Commands.LOGIN);
            message.setNickname(nickname);
            message.setPassword(password);
            // Invio del messaggio
            sendToServer(message);
            // Ricezione del messaggio di risposta del server
            ClientServerMessage receivedMsg = receiveFromServer();
            // Interpretazione della reply ricevuta dal server
            switch (receivedMsg.getReply()) {
                // Login riuscita
                case OK -> {
                    displayLine("Accesso completato con successo, buon lavoro!");
                    return receivedMsg.getUser();
                }
                // Utente non registrato
                case NOT_REGISTERED -> displayLine("L'utente " + nickname + " non è registrato.");
                // Password errata
                case WRONG_PASSW -> displayLine("Password errata.");
                // L'utente risulta già online
                case ALREADY_ONLINE -> displayLine("L'utente " + nickname + " è già collegato.");
                // Default
                default -> System.err.println("\nErrore: error code sbagliato.\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("\nErrore sconosciuto.\n");
        }
        // Se reply è diversa da OK ritorna null
        return null;
    }

    /**
     * Operazione di logout: Effettua la richiesta al server, inviandogli nel
     * messaggio, oltre al comando, l'oggetto di tipo User. Successivamente,
     * se il valore di Replies nella risposta del server indica che l'operazione è
     * andata a buon fine, setta l'oggetto di tipo User a offline, cancella la
     * registrazione dalle callback, rimuove l'oggetto remoto che aveva esportato
     * e interrompe tutti i threads sniffer che si occupavano di catturare i
     * messaggi delle chat. Stampa a schermo un messaggio che dipende dall'esito
     * dell'operazione (e quindi dal valore di Replies ricevuto).
     *
     * @param nickname identificativo dell'utente
     */
    private static void logout(String nickname) {
        if (!user.getNickname().equals(nickname)) {
            displayLine("Nickname errato.");
            return;
        }
        if (user.isOnline()) {
            try {
                // Costruzione del messaggio da inviare al server
                ClientServerMessage message = new ClientServerMessage(Commands.LOGOUT);
                message.setUser(user);
                // Invio del messaggio
                sendToServer(message);
                // Ricezione del messaggio di risposta del server
                ClientServerMessage receivedMsg = receiveFromServer();
                // Interpretazione della reply ricevuta dal server
                switch (receivedMsg.getReply()) {
                    case OK ->  {
                        // Cancellazione della registrazione alle callback
                        serverStub.unregisterForCallback(stub);
                        // Rimozione dell'oggetto remoto
                        UnicastRemoteObject.unexportObject(callbackObj, false);
                        // Interruzione dei threads sniffer
                        interruptAllSniffers();
                        // Utente offline
                        user.setOnline(false);
                    }
                    case UNKNOWN_ERROR -> System.err.println("\nErrore nella fase di logout.\n");
                    default -> System.err.println("\nErrore: error code sbagliato.\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("\nErrore sconosciuto.\n");
            }
        }
    }

    /**
     * Stampa la lista degli utenti registrati al servizio.
     */
    private static void listUsers() {
        System.out.println("\n< Lista degli utenti registrati a Worth:");
        // Acquisizione della read lock
        userLock.readLock().lock();
        // Stampa della lista degli utenti
        printList(user.getUsersList());
        // Rilascio della read lock
        userLock.readLock().unlock();
        System.out.println('\n');
    }

    /**
     * Stampa la lista degli utenti online
     */
    private static void listOnlineUsers() {
        System.out.println("\n< Lista degli utenti online:");
        // Acquisizione della read lock
        userLock.readLock().lock();
        // Stampa della lista degli utenti online
        printList(user.getOnlineUsersList());
        // Rilascio della read lock
        userLock.readLock().unlock();
        System.out.println('\n');
    }

    /**
     * Manda la richiesta al server per ottenere la lista dei progetti di cui fa parte
     * l'utente. Infine, se il valore di Replies nella risposta del server indica che 
     * l'operazione è andata a buon fine, li stampa. Altrimenti stampa un messaggio di
     * errore.
     */
    private static void listProjects() {
        try {
            // Costruzione del messaggio da inviare al server
            ClientServerMessage message = new ClientServerMessage(Commands.LIST_PROJECTS);
            message.setNickname(user.getNickname());
            // Invio del messaggio
            sendToServer(message);
            // Ricezione del messaggio di risposta del server
            ClientServerMessage receivedMsg = receiveFromServer();
            // Interpretazione della reply ricevuta dal server
            switch (receivedMsg.getReply()) {
                case OK -> {
                    // Stampa della lista dei progetti
                    System.out.println("\n< Lista dei progetti di cui fai parte:");
                    printList(receivedMsg.getProjects());
                    System.out.println('\n');
                }
                case UNKNOWN_ERROR -> System.err.println("\nErrore nel server.\n");
                default -> System.err.println("\nErrore: error code sbagliato.\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("\nErrore sconosciuto.\n");
        }
    }

    /**
     * Manda la richiesta al server di creare un nuovo progetto. Il server risponde
     * con un valore di Replies che indica se il progetto è stato creato con successo, 
     * oppure se si è verificato un errore. Stampa un messaggio che dipende dall'esito
     * dell'operazione (e quindi dal valore di Replies ricevuto).
     * 
     * @param projectName il nome del progetto da creare
     */
    private static void createProject(String projectName) {
        try {
            // Costruzione del messaggio da inviare al server
            ClientServerMessage message = new ClientServerMessage(Commands.CREATE_PROJECT);
            message.setNickname(user.getNickname());
            message.setProjectName(projectName);
            // Invio del messaggio
            sendToServer(message);
            // Ricezione del messaggio di risposta del server
            ClientServerMessage receivedMsg = receiveFromServer();
            // Interpretazione della reply ricevuta dal server
            switch (receivedMsg.getReply()) {
                case OK -> displayLine("Progetto creato con successo!");
                case UNABLE_CREATE_PROJECT -> System.err.println("\nErrore del server.\n");
                case PROJECT_EXISTS -> displayLine("Impossibile creare il progetto: esiste già un progetto con questo nome.");
                default -> System.err.println("\nErrore: error code sbagliato.\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("\nErrore sconosciuto.\n");
        }
    }

    /**
     * Permette di aggiungere un nuovo membro a un progetto. Per farlo manda la 
     * richiesta al server, il quale risponde con un valore di Replies che indica se 
     * è stato aggiunto con successo o se si è verificato un errore. Stampa a schermo
     * un messaggio che dipende dall'esito dell'operazione (e quindi dal valore di 
     * Replies ricevuto).
     * 
     * @param projectName il nome del progetto a cui aggiungere un membro
     * @param nickUser    il nickname del nuovo membro da aggiungere
     */
    private static void addMember(String projectName, String nickUser) {
        try {
            // Costruzione del messaggio da inviare al server
            ClientServerMessage message = new ClientServerMessage(Commands.ADD_MEMBER);
            message.setProjectName(projectName);
            message.setNickname(user.getNickname());
            message.setNewMember(nickUser);
            // Invio del messaggio
            sendToServer(message);
            // Ricezione del messaggio di risposta del server
            ClientServerMessage receivedMsg = receiveFromServer();
            // Interpretazione della reply ricevuta dal server
            switch (receivedMsg.getReply()) {
                case OK -> displayLine("Membro aggiunto correttamente!");
                case NOT_REGISTERED -> displayLine("L'utente " + nickUser + " non esiste.");
                case ALREADY_MEMBER -> displayLine("L'utente " + nickUser + " è già membro del progetto.");
                case NONEXISTENT_PROJECT -> displayLine("Non sei membro di un progetto di nome " + projectName + ".");
                default -> System.err.println("\nErrore: error code sbagliato.\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("\nErrore sconosciuto.\n");
        }
    }

    /**
     * Stampa la lista dei membri di un progetto. Per farlo manda la richiesta al
     * server, il quale risponde con un valore di Replies (che indica l'esito 
     * dell'operazione) e, in caso di successo, con la lista dei membri del progetto. 
     * Stampa a schermo quest'ultima in caso di successo oppure un messaggio di errore 
     * che dipende dal valore di Replies ricevuto.
     * 
     * @param projectName il nome del progetto di cui stampare la lista dei membri
     */
    private static void showMembers(String projectName) {
        try {
            // Costruzione del messaggio da inviare al server
            ClientServerMessage message = new ClientServerMessage(Commands.SHOW_MEMBERS);
            message.setProjectName(projectName);
            message.setNickname(user.getNickname());
            // Invio del messaggio
            sendToServer(message);
            // Ricezione del messaggio di risposta del server
            ClientServerMessage receivedMsg = receiveFromServer();
            // Interpretazione della reply ricevuta dal server
            switch (receivedMsg.getReply()) {
                case OK -> {
                    // Stampa della lista dei membri
                    System.out.println("\n< Lista dei membri del progetto " + projectName + ":");
                    printList(receivedMsg.getMembers());
                    System.out.println('\n');
                }
                case NONEXISTENT_PROJECT -> displayLine("Non sei membro di un progetto di nome " + projectName + ".");
                default -> System.err.println("\nErrore: error code sbagliato.\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("\nErrore sconosciuto.\n");
        }
    }

    /**
     * Stampa la lista delle carte di un progetto. Per farlo manda la richiesta al 
     * server, il quale risponde con un valore di Replies (che indica l'esito dell'operazione)
     * e, in caso di successo, con la lista delle carte del progetto. Stampa a schermo la 
     * lista delle carte in caso di successo oppure un messaggio di errore che dipende dal 
     * valore di Replies ricevuto.
     * 
     * @param projectName il nome del progetto di cui stampare la lista delle carte
     */
    private static void showCards(String projectName) {
        try {
            // Costruzione del messaggio da inviare al server
            ClientServerMessage message = new ClientServerMessage(Commands.SHOW_CARDS);
            message.setProjectName(projectName);
            message.setNickname(user.getNickname());
            // Invio del messaggio
            sendToServer(message);
            // Ricezione del messaggio di risposta del server
            ClientServerMessage receivedMsg = receiveFromServer();
            // Interpretazione della reply ricevuta dal server
            switch (receivedMsg.getReply()) {
                case OK -> {
                    // Stampa della lista delle carte
                    System.out.println("\n< Lista delle carte del progetto " + projectName + ":");
                    printList(receivedMsg.getCards());
                    System.out.println('\n');
                }
                case NONEXISTENT_PROJECT -> displayLine("Non sei membro di un progetto di nome " + projectName + ".");
                default -> System.err.println("\nErrore: error code sbagliato.\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("\nErrore sconosciuto.\n");
        }
    }

    /**
     * Stampa le informazioni di una carta (nome, descrizione e lista). Per farlo
     * manda la richiesta al server, il quale risponde con un valore di Replies (che 
     * indica l'esito dell'operazione) e, in caso di successo, con l'oggetto di 
     * tipo Card relativo alla carta. Stampa a schermo le informazioni della carta 
     * in caso di successo oppure un messaggio di errore che dipende dal valore di
     * Replies ricevuto.
     * 
     * @param projectName nome del progetto di cui la carta fa parte
     * @param cardName    nome della carta di cui stampare le informazioni
     */
    private static void showCard(String projectName, String cardName) {
        try {
            // Costruzione del messaggio da inviare al server
            ClientServerMessage message = new ClientServerMessage(Commands.SHOW_CARD);
            message.setProjectName(projectName);
            message.setNickname(user.getNickname());
            message.setCardName(cardName);
            // Invio del messaggio
            sendToServer(message);
            // Ricezione del messaggio di risposta del server
            ClientServerMessage receivedMsg = receiveFromServer();
            // Interpretazione della reply ricevuta dal server
            switch (receivedMsg.getReply()) {
                case OK -> displayLine("" + receivedMsg.getCard());   // Stampa della carta
                case NONEXISTENT_PROJECT -> displayLine("Non sei membro di un progetto di nome " + projectName + ".");
                case NONEXISTENT_CARD -> displayLine("Non esiste nessuna carta di nome " + cardName + " nel progetto" + projectName + ".");
                default -> System.err.println("\nErrore: error code sbagliato.\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("\nErrore sconosciuto.\n");
        }
    }

    /**
     * Crea una nuova carta e la aggiunge a un progetto. Per farlo manda la 
     * richiesta al server, il quale risponde con un valore di Replies che indica 
     * se la carta è stata creata e aggiunta con successo, oppure se si è verificato 
     * un errore. Stampa un messaggio che dipende dall'esito dell'operazione (e 
     * quindi dal valore di Replies ricevuto).
     * 
     * @param projectName il nome del progetto a cui aggiungere la nuova carta
     * @param cardName    il nome della carta da creare
     * @param description la descrizione della carta da creare
     */
    private static void addCard(String projectName, String cardName, String description) {
        try {
            // Costruzione del messaggio da inviare al server
            ClientServerMessage message = new ClientServerMessage(Commands.ADD_CARD);
            message.setProjectName(projectName);
            message.setCardName(cardName);
            message.setDescrizione(description);
            message.setNickname(user.getNickname());
            // Invio del messaggio
            sendToServer(message);
            // Ricezione del messaggio di risposta del server
            ClientServerMessage receivedMsg = receiveFromServer();
            // Interpretazione della reply ricevuta dal server
            switch (receivedMsg.getReply()) {
                case OK -> displayLine("Card aggiunta correttamente!");
                case NONEXISTENT_PROJECT -> displayLine("Non sei membro di un progetto di nome " + projectName + ".");
                case CARD_EXISTS -> displayLine("La card " + cardName + " esiste già nel progetto " + projectName + ".");
                default -> System.err.println("\nErrore: error code sbagliato.\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("\nErrore sconosciuto.\n");
        }
    }

    /**
     * Sposta una carta da una lista a un'altra all'interno dello stesso progetto, 
     * a patto che vengano rispettati i vincoli. Per farlo manda la richiesta al 
     * server, il quale risponde con un valore di Replies che indica se la carta è stata 
     * spostata con successo, oppure se si è verificato un errore. Stampa un messaggio 
     * che dipende dall'esito dell'operazione (e quindi dal valore di Replies ricevuto).
     * 
     * @param projectName il nome del progetto nel quale si trova la carta
     * @param cardName    il nome della carta
     * @param sourceList  la lista in cui si trova la carta
     * @param destList    la lista di destinazione
     */
    private static void moveCard(String projectName, String cardName, String sourceList, String destList) {
        try {
            // Costruzione del messaggio da inviare al server
            ClientServerMessage message = new ClientServerMessage(Commands.MOVE_CARD);
            message.setProjectName(projectName);
            message.setCardName(cardName);
            message.setListaPartenza(sourceList);
            message.setListaDestinazione(destList);
            message.setNickname(user.getNickname());
            // Invio del messaggio
            sendToServer(message);
            // Ricezione del messaggio di risposta del server
            ClientServerMessage receivedMsg = receiveFromServer();
            // Interpretazione della reply ricevuta dal server
            switch (receivedMsg.getReply()) {
                case OK -> displayLine("Card spostata correttamente da " + sourceList.toUpperCase() + " a " + destList.toUpperCase() + ".");
                case NONEXISTENT_PROJECT -> displayLine("Non sei membro di un progetto di nome " + projectName + ".");
                case NONEXISTENT_LIST -> displayLine("Almeno una delle liste non esiste. Liste disponibili: TODO, INPROGRESS, TOBEREVISED, DONE.");
                case NONEXISTENT_CARD -> displayLine("La card " + cardName + " non è presente nella lista " + sourceList.toUpperCase() + ".");
                case MOVE_FORBIDDEN -> displayLine("Vietato spostare la card da " + sourceList.toUpperCase() + " a " + destList.toUpperCase() + ".\n");
                case CARD_EXISTS -> displayLine("La card " + cardName + " è già nella lista " + destList.toUpperCase() + ".");
                default -> System.err.println("\nErrore: error code sbagliato.\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("\nErrore sconosciuto.\n");
        }
    }

    /**
     * Stampa la storia di una carta. Per farlo manda la richiesta al server, il 
     * quale risponde con un valore di Replies (che indica l'esito dell'operazione) e, 
     * in caso di successo, con la storia della carta. Stampa quest'ultima in caso di
     * successo, oppure un messaggio di errore che dipende dal valore di Replies 
     * ricevuto.
     * 
     * @param projectName il nome del progetto nel quale si trova la carta
     * @param cardName    il nome della carta
     */
    private static void getCardHistory(String projectName, String cardName) {
        try {
            // Costruzione del messaggio da inviare al server
            // Chiedo al server di restituirmi la carta (comando SHOW_CARD), e la history la prendo da lì
            ClientServerMessage message = new ClientServerMessage(Commands.SHOW_CARD);
            message.setProjectName(projectName);
            message.setNickname(user.getNickname());
            message.setCardName(cardName);
            // Invio del messaggio
            sendToServer(message);
            // Ricezione del messaggio di risposta del server
            ClientServerMessage receivedMsg = receiveFromServer();
            // Interpretazione della reply ricevuta dal server
            switch (receivedMsg.getReply()) {
                case OK -> displayLine("Storia: " + receivedMsg.getCard().getHistory());  // stampa la storia della carta
                case NONEXISTENT_PROJECT -> displayLine("Non sei membro di un progetto di nome " + projectName + ".");
                case NONEXISTENT_CARD -> displayLine("Non esiste nessuna carta di nome " + cardName + " nel progetto " + projectName + ".");
                default -> System.err.println("\nErrore: error code sbagliato.\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("\nErrore sconosciuto.\n");
        }
    }

    /**
     * Manda un messaggio sulla chat di un progetto di cui l'utente fa parte. Il 
     * messaggio sarà visibile ai membri che sono online al momento dell'invio. 
     * Stampa a schermo un messaggio che informa sull'esito dell'operazione.
     * 
     * @param projectName il nome del progetto a cui appartiene la chat
     * @param message     il messaggio da inviare
     */
    private static void sendChatMsg(String projectName, String message) {
        // Acquisizione della read lock
        userLock.readLock().lock();
        // Se l'utente non appartiene al progetto stampa un messaggio di errore
        if (!user.getChatsList().contains(new Chat(projectName))) {
            // Rilascio della read lock
            userLock.readLock().unlock();
            // Stampa esito operazione
            displayLine("Non sei membro di un progetto di nome " + projectName + ".");
            return;
        }
        // Invio del messaggio sulla chat
        user.sendChatMsg(projectName, message);
        // Rilascio della read lock
        userLock.readLock().unlock();
        // Stampa esito operazione
        displayLine("Messaggio inviato!");
    }

    /**
     * Stampa a schermo i messaggi arrivati su una chat di un progetto di cui 
     * l'utente fa parte, mentre l'utente era online. Se l'utente non fa parte 
     * del progetto di cui intende leggere la chat, stampa a schermo un messaggio
     * di errore.
     * 
     * @param projectName il nome del progetto di cui l'utente vuole leggere la chat
     */
    private static void readChat(String projectName) {
        // Acquisizione della read lock
        userLock.readLock().lock();
        // Se l'utente non appartiene al progetto stampa un messaggio di errore
        if (!user.getChatsList().contains(new Chat(projectName))) {
            // Rilascio della read lock
            userLock.readLock().unlock();
            // Stampa messaggio d'errore
            displayLine("Non sei membro di un progetto di nome " + projectName + ".");
            return;
        }
        // Lettura dei messaggi arrivati sulla chat
        System.out.println();
        // Lettura della chat
        user.readChat(projectName);
        // Rilascio della read lock
        userLock.readLock().unlock();
        System.out.println();
    }

    /**
     * Cancella un progetto di cui l'utente è membro, a patto che tutte le carte si 
     * trovino nella lista DONE. Per farlo manda una richiesta al server, il quale 
     * risponde con un valore di Replies che indica l'esito dell'operazione. Stampa 
     * a schermo un messaggio che dipende dal valore di Replies ricevuto.
     * 
     * @param projectName il nome del progetto da cancellare
     */
    private static void cancelProject(String projectName) {
        try {
            // Costruzione del messaggio da inviare al server
            ClientServerMessage message = new ClientServerMessage(Commands.CANCEL_PROJECT);
            message.setProjectName(projectName);
            message.setNickname(user.getNickname());
            // Invio del messaggio
            sendToServer(message);
            // Ricezione del messaggio di risposta del server
            ClientServerMessage receivedMsg = receiveFromServer();
            // Interpretazione della reply ricevuta dal server
            switch (receivedMsg.getReply()) {
                case OK -> displayLine("Progetto cancellato correttamente.");
                case NONEXISTENT_PROJECT -> displayLine("Non sei membro di un progetto di nome " + projectName + ".");
                case CANCEL_FORBIDDEN -> displayLine("Impossibile cancellare il progetto: le carte non sono tutte nella lista DONE.");
                default -> System.err.println("\nErrore: error code sbagliato.\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("\nErrore sconosciuto.\n");
        }
    }

    /**
     * Metodo ausiliario per l'invio di un messaggio al server. I messaggi sono 
     * oggetti di tipo ClientServerMessage che contengono tutte le informazioni 
     * che servono al server per soddisfare una richiesta del client (anch'essa
     * contenuta in un campo del messaggio). Il messaggio viene serializzato, e 
     * vengono inviati al server i bytes contenuti in due byte buffers: uno contenente
     * i byte della stringa ottenuta con la serializzazione del messaggio, l'altro 
     * contenente il numero di bytes del precedente (il quale servirà al server per 
     * allocare un byte buffer grande abbastanza per leggere tutto il messaggio).
     * 
     * @param message il messaggio da inviare
     * @throws IOException in caso di errori di I/O durante la chiamata alla write()
     */
    private static void sendToServer(ClientServerMessage message) throws IOException {
        Gson gson = new Gson();
        // Serializzazione del messaggio
        String str = gson.toJson(message);
        // Codifica della stringa in un array di byte
        byte[] byteArray = str.getBytes(StandardCharsets.UTF_8);
        // Allocazione del byte buffer utilizzato per inviare il numero di bytes del messaggio serializzato
        ByteBuffer sizeBuffer = ByteBuffer.allocate(Integer.BYTES);
        // Inizializzazione con la lunghezza dell'array di byte
        sizeBuffer.putInt(byteArray.length);
        // Preparazione del buffer alla scrittura dopo la lettura
        sizeBuffer.flip();
        // Creazione del buffer contenente i bytes del messaggio serializzato
        ByteBuffer dataBuffer = ByteBuffer.wrap(byteArray);
        // Invio dei bytes contenuti nei buffers
        socketChannel.write(new ByteBuffer[]{sizeBuffer, dataBuffer});
    }

    /**
     * Metodo ausiliario per la ricezione di un messaggio dal server. I messaggi 
     * sono oggetti di tipo ClientServerMessage che contengono tutte le informazioni
     * che servono al client, in risposta ad una sua richiesta. Per prima cosa 
     * vengono letti dei bytes in un byte buffer, i quali sono la codifica della 
     * dimensione del vero e proprio messaggio. Viene quindi allocato un nuovo byte 
     * buffer della dimensione ricevuta, per contenere tutto il messaggio. Infine, si 
     * leggono i bytes sul bytebuffer, si ricostruisce la stringa che corrisponde al 
     * messaggio serializzato e si fa un'operazione di deserializzazione, in modo da 
     * ottenere il messaggio.
     * 
     * @return il messaggio, ovvero un oggetto di tipo ClientServerMessage
     * @throws IOException in caso di errori di I/O durante una chiamata alla read()
     */
    private static ClientServerMessage receiveFromServer() throws IOException {
        // Allocazione del byte buffer utilizzato per la lettura della dimensione del messaggio in bytes
        ByteBuffer sizeBuffer = ByteBuffer.allocate(Integer.BYTES);
        // Lettura della dimensione del messaggio in bytes
        socketChannel.read(sizeBuffer);
        // Preparazione del buffer alla scrittura dopo la lettura
        sizeBuffer.flip();
        // Decodifica della dimensione
        int size = sizeBuffer.getInt();
        // Allocazione del byte buffer utilizzato per la lettura del messaggio, con capacità uguale alla dimensione letta
        ByteBuffer dataBuffer = ByteBuffer.allocate(size);
        // Lettura del messaggio nel byte buffer
        socketChannel.read(dataBuffer);
        // Preparazione del buffer alla scrittura dopo la lettura
        dataBuffer.flip();
        // Ricostruzione della stringa corrispondente al messaggio serializzato
        StringBuilder stringBuilder = new StringBuilder();
        while (dataBuffer.hasRemaining()) {
            stringBuilder.append(StandardCharsets.UTF_8.decode(dataBuffer).toString());
        }
        String received = stringBuilder.toString();
        // Deserializzazione del messaggio
        Gson gson = new Gson();
        return gson.fromJson(received, ClientServerMessage.class);
    }

    /**
     * Metodo per stampare gli elementi di una lista
     * 
     * @param list la lista contenente gli elementi da stampare
     * @param <T>  tipo generico
     */
    public static <T> void printList(ArrayList<T> list) {
        if (list == null || list.isEmpty()) {
            System.out.println("* Vuoto *");
            return;
        }
        // Stampa degli elementi
        int counter = 0;
        for (T elem : list)
            System.out.println("    " + ++counter + ". " +  elem);
    }

    /**
     * Metodo che stampa i comandi disponibili all'utente in un dato momento
     */
    private static void help() {
        System.out.println("\n****\tCOMANDI DISPONIBILI:\n");
        if (user == null)               // Prima del login
            printInitialCommands();
        else                            // Dopo il login
            printCommands();
        System.out.println();
    }

    /**
     * Metodo ausiliario utilizzato in help. Stampa i comandi 
     * disponibili all'utente quando non ha ancora fatto la login
     */
    private static void printInitialCommands() {
        display("register [nickname] [password] : Registra l'utente con le credenziali fornite.");
        display("login [nickname] [password] : Effettua il login dell'utente al servizio.");
    }

    /**
     * Metodo ausiliario utilizzato in help. Stampa i comandi 
     * disponibili all'utente quando ha già fatto la login
     */
    private static void printCommands() {
        display("logout [nickname] : Effettua il logout dal servizio.");
        display("list_users : Mostra la lista degli utenti registrati.");
        display("list_online_users : Mostra la lista degli utenti online.");
        display("list_projects : Mostra la lista dei progetti di cui fai parte.");
        display("create_project [project_name] : Crea un progetto \"project_name\" di cui sarai automaticamente membro.");
        display("add_member [project_name] [nickname] : Aggiunge l'utente \"nickname\" ai membri del progetto \"project_name\".");
        display("show_members [project_name] : Mostra la lista dei membri del progetto \"project_name\".");
        display("show_cards [project_name] : Mostra tutte le card del progetto \"project_name\".");
        display("show_card [project_name] [card_name] : Recupera le informazioni della card \"card_name\" del progetto \"project_name\".");
        display("add_card [project_name] [card_name] [description] : Aggiunge la card \"card_name\" con descrizione \"description\" al progetto \"project_name\" (description non deve contenere spazi).");
        display("move_card [project_name] [card_name] [source_list] [dest_list] : Sposta la card \"card_name\" dalla lista di partenza \"source_list\" alla lista di destinazione \"dest_list\" del progetto \"project_name\".");
        display("get_card_history [project_name] [card_name] : Mostra tutti gli spostamenti della card \"card_name\" all'interno delle liste del progetto \"project_name\".");
        display("send [project_name] [message] : Invia il messaggio \"message\" alla chat del progetto \"project_name\" (il messaggio può contenere spazi).");
        display("receive [project_name] : Visualizza i messaggi della chat del progetto \"project_name\".");
        display("cancel_project [project_name] : Cancella il progetto \"project_name\" (possibile solo se tutte le card si trovano nella lista DONE).");
    }

    /**
     * Crea il thread sniffer incaricato di catturare i messaggi della chat
     * 
     * @param chat la chat per cui si crea il thread
     */
    public static void startSniffer(Chat chat) {
        Thread snifferThread = new Thread(new ChatSniffer(chat));
        // Nome del thread uguale a quello del progetto
        snifferThread.setName(chat.getProject());
        // Il thread viene messo nella lista dei threads sniffer
        sniffers.add(snifferThread);
        // Esecuzione del thread
        snifferThread.start();
    }

    /**
     * Interrompe e cancella il thread sniffer della chat
     * 
     * @param chat la chat per cui si cancella il thread
     */
    public static void interruptSniffer(Chat chat) {
        // Ricerca del thread da interrompere e cancellare
        for (Thread thread : sniffers) {
            // Identificazione del thread grazie al nome uguale a quello del progetto
            if (thread.getName().equals(chat.getProject())) {
                // Interruzione del thread
                thread.interrupt();
                // Rimozione dalla lista di threads
                sniffers.remove(thread);
                break;
            }
        }
    }

    /**
     * Interrompe tutti i threads sniffer delle chat dell'utente (utilizzata 
     * nell'operazione di logout)
     */
    private static void interruptAllSniffers() {
        // Interruzione di tutti i thread sniffer
        for (Thread thread : sniffers) {
            thread.interrupt();
        }
    }

    /**
     * Metodo ausiliario per visualizzare stringhe sullo schermo con un opportuno 
     * formato.
     * 
     * @param string la stringa da stampare
     */
    private static void display(String string) {
        System.out.println("< " + string);
    }

    /**
     * Metodo ausiliario per visualizzare stringhe sullo schermo con un opportuno 
     * formato. È come display() ma con un newline ad inizio e fine stampa.
     * 
     * @param string la stringa da stampare
     */
    private static void displayLine(String string) {
        System.out.println("\n< " + string + "\n");
    }

}