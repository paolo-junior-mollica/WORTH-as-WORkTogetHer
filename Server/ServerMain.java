import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.google.gson.Gson;

/**
 * Server class
 * 
 * @author Paolo Junior Mollica
 */
public class ServerMain {

    /** Lista degli utenti registrati a Worth */
    private static final ArrayList<User> registeredUsers = new ArrayList<>();

    /** Lock per la lista degli utenti registrati */
    private static final ReentrantReadWriteLock usersLock = new ReentrantReadWriteLock();

    /** Lista di tutti i progetti creati */
    private static final ArrayList<Project> createdProjects = new ArrayList<>();

    /** Lock per la lista dei progetti creati */
    private static final ReentrantReadWriteLock projectsLock = new ReentrantReadWriteLock();

    /**
     * Lista di oggetti remoti dei client, per tenere traccia degli utenti
     * registrati al servizio di callback
     */
    private static final ArrayList<NotifyEventInterface> clientsRegisteredForCallback = new ArrayList<>();

    /** Lock per la lista dei client registrati al servizio di RMI callback */
    private static final ReentrantReadWriteLock callbackLock = new ReentrantReadWriteLock();

    /**
     * Thread pool di thread worker, incaricati di gestire le richieste dei client
     */
    private static final ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(8);

    /** Porta del registry */
    private static final int registryPort = 6789;

    /** Porta per le connessioni TCP */
    private static final int TCPport = 7890;

    /**
     * Indirizzo multicast di partenza, da cui assegnare gli indirizzi per le chat
     */
    private static String multicastAddress = "239.0.0.0";

    /**
     * Coda contenente indirizzi multicast già utilizzati da processi ora
     * cancellati, e quindi riutilizzabili
     */
    private static final LinkedBlockingQueue<String> addressesToBeReallocated = new LinkedBlockingQueue<>();

    /** Porta per il multicast, da assegnare alle chat */
    private static final int multicastPort = 10000;

    /** Dimensione per i buffer */
    private static final int BUFFER_SIZE = (int) Math.pow(2, 10);

    /** Nome della directory contenente lo stato del sistema */
    private static final String stateDirName = "state";

    /** Nome del file contenente lo stato degli utenti registrati */
    private static final String usersFilename = "usersState.json";

    /**
     * Nome del file contenente i nomi dei membri di un progetto (uno per ogni
     * directory relativa a un progetto)
     */
    private static final String projectMembersFilename = "projectMembers.json";

    /**
     * Main method
     */
    public static void main(String[] args) {
        // Hook per effettuare il salvataggio dello stato in caso di un'interruzione,
        // come un CTRL-C
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                saveState();
            }
        });
        // Ripristino dello stato del sistema
        restoreState();
        // Inizializzazione dell'oggetto esportato dal server
        WorthImpl server = new WorthImpl();
        try {
            // Esportazione dell'oggetto
            WorthInterface stub = (WorthInterface) UnicastRemoteObject.exportObject(server, 0);
            // Creazione del registry
            Registry registry = LocateRegistry.createRegistry(registryPort);
            // Associazione del nome all'oggetto remoto
            registry.rebind("WORTH-SERVER", stub);
            System.out.println("Server: registry pronto sulla porta " + registryPort);
            // Apertura del socket channel
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            // Riferimento al server socket
            ServerSocket serverSocket = serverSocketChannel.socket();
            // Associazione dell'indirizzo al server socket
            serverSocket.bind(new InetSocketAddress(TCPport));
            // Modalità non bloccante
            serverSocketChannel.configureBlocking(false);
            // Apertura selettore
            Selector selector = Selector.open();
            // Registrazione del socket channel nel selettore. Interest set: accept(). Viene
            // aggiornato il key set del
            // selettore con l'aggiunta della chiave relativa al canale
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            // Ciclo infinito
            while (true) {
                // Selezione tra i canali registrati di quelli pronti ad operazioni di I/O del
                // rispettivo interest set
                selector.select();
                // Riferimento all'insieme di chiavi precedentemente registrate (che si trovano
                // nel key set del
                // selettore), per le quali una delle operazioni dell'interest set della chiave
                // si trova anche nel ready
                // set (il canale è pronto per l'esecuzione di tale operazione)
                Set<SelectionKey> readyKeys = selector.selectedKeys();
                // Iteratore per il set di ready keys
                Iterator<SelectionKey> keyIterator = readyKeys.iterator();
                // Scansione del set
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();
                    // Se il canale relativo alla chiave è pronto per accettare una connessione
                    // socket (operazione di
                    // accept())
                    if (key.isAcceptable()) {
                        // Esecuzione di accept(). L'unico canale registrato con l'operazione di
                        // accept() nell'interest
                        // set è serverSocketChannel
                        SocketChannel client = serverSocketChannel.accept();
                        // Modalità non bloccante
                        client.configureBlocking(false);
                        // Byte buffers da utilizzare come attachment
                        ByteBuffer sizeBuffer = ByteBuffer.allocate(Integer.BYTES), dataBuffer = null;
                        // Registrazione del socket channel per la comunicazione con il client. Interest
                        // set: read().
                        // Attachment: Due byte buffer, il primo per leggere la dimensione del
                        // messaggio, il
                        // secondo per leggere il messaggio, allocato con la dimensione letta dal primo.
                        client.register(selector, SelectionKey.OP_READ, new ByteBuffer[] { sizeBuffer, dataBuffer });
                    } else if (key.isReadable()) { // Se il canale relativo alla chiave è pronto per una operazione di
                                                   // read()
                        // Riferimento al canale pronto
                        SocketChannel client = (SocketChannel) key.channel();
                        // Riferimento all'attachment della chiave del canale
                        ByteBuffer[] buffers = (ByteBuffer[]) key.attachment();
                        // Lettura della dimensione del messaggio
                        client.read(buffers[0]);
                        // Se non ha finito di leggere non fa nulla, continua a leggere dopo la prossima
                        // select(),
                        // altrimenti entra nel ramo if
                        if (!buffers[0].hasRemaining()) {
                            // Preparazione alla scrittura dopo la lettura
                            buffers[0].flip();
                            // Decodifica della dimensione del messaggio a intero
                            int size = buffers[0].getInt();
                            // Se il buffer per la lettura del messaggio è null allora lo alloco, altrimenti
                            // significa
                            // che non aveva finito di leggere i dati dopo la select() precedente, e quindi
                            // non lo
                            // alloco per non perdere i dati precedentemente letti e permettergli di
                            // continuare
                            if (buffers[1] == null)
                                buffers[1] = ByteBuffer.allocate(size);
                            // Lettura del messaggio
                            client.read(buffers[1]);
                            // Se ha letto tutto il messaggio entra nel ramo if, altrimenti non fa niente e
                            // continuerà a leggere dopo la prossima select()
                            if (buffers[1].position() == size) {
                                // Preparazione alla scrittura dopo la lettura
                                buffers[1].flip();
                                // Task worker per soddisfare la richiesta del client
                                WorkerTask task = new WorkerTask(client, buffers[1]);
                                // Passaggio del task al thread pool
                                threadPool.execute(task);
                                // Resetto il buffer per la dimensione del messaggio, in modo che possa leggere
                                // la
                                // dimensione del prossimo
                                buffers[0].clear();
                                // Buffer per il messaggio a null, in modo che possa essere allocato con la
                                // dimensione
                                // del prossimo messaggio da leggere
                                buffers[1] = null;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Salvataggio dello stato
            saveState();
        }
    }

    /**
     * Metodo per il salvataggio dello stato del sistema. Lo stato viene salvato
     * all'interno di una directory, il cui nome è contenuto nella variabile
     * d'istanza stateDirName. All'interno della directory c'è un file contenente lo
     * stato degli utenti registrati al servizio (il cui nome è contenuto nella
     * variabile d'istanza usersFilename) e una directory per ogni progetto creato
     * in Worth. Infine, all'interno di ogni directory realtiva a un progetto, si
     * trova un file che contiene i nomi dei membri di tale progetto (il cui nome è
     * contenuto nella variabile d'istanza projectMembersFilename) e un file per
     * ogni carta, al cui interno viene salvato lo stato della carta corrispondente.
     */
    private static void saveState() {
        // Oggetto di tipo Path relativo al filename della root directory dello stato
        Path statePath = Paths.get(stateDirName);
        try {
            // Se la directory esiste già viene eliminata con tutto ciò che contiene (butto
            // via il vecchio stato)
            if (Files.isDirectory(statePath))
                deleteDirectory(stateDirName);
            // Creazione di una nuova root directory per il salvataggio dello stato
            Files.createDirectory(statePath);
            // Scrittura del file per il salvataggio dello stato degli utenti registrati,
            // all'interno della root
            // directory
            writeUsersFile();
            // Acquisizione della read lock dei progetti
            projectsLock.readLock().lock();
            // Per ogni progetto creato in Worth
            for (Project project : createdProjects)
                // Creazione di una nuova directory per il salvataggio dello stato del progetto,
                // all'interno della
                // root directory
                createProjectDirectory(project);
            // Rilascio della read lock dei progetti
            projectsLock.readLock().unlock();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Metodo ricorsivo per cancellare una directory e ciò che contiene (files e
     * directories). Può essere utilizzata anche per cancellare un singolo file.
     * 
     * @param filename il nome della directory da cancellare
     */
    private static void deleteDirectory(String filename) {
        // Creazione di un oggetto di tipo File
        File file = new File(filename);
        // Se è una directory cancello prima il contenuto
        if (file.isDirectory()) {
            // Creazione di un array contenente i nomi di tutti i files/directories al suo
            // interno
            String[] files = file.list();
            assert files != null;
            // Per ognuno di quei file/directory nell'array
            for (String newFilename : files) {
                // Chiamata ricorsiva alla funzione per cancellare il file/directory
                deleteDirectory(filename + File.separator + newFilename);
            }
        }
        // Cancellazione del file/directory relativo a filename
        file.delete();
    }

    /**
     * Metodo per scrivere il file contenente il salvataggio dello stato degli
     * utenti registrati al servizio.
     * 
     * @throws IOException in caso di errori di I/O durante una chiamata alla
     *                     write()
     */
    private static void writeUsersFile() throws IOException {
        // Aquisizione della write lock della lista degli utenti registrati
        usersLock.writeLock().lock();
        // Per ogni utente azzero le liste degli utenti e delle chat (non serve
        // scriverle nel file)
        for (User user : registeredUsers) {
            user.setUsersList(new ArrayList<>());
            user.setChatsList(new ArrayList<>());
        }
        // Rilascio della write lock
        usersLock.writeLock().unlock();
        // Aquisizione della read lock della lista degli utenti registrati
        usersLock.readLock().lock();
        // Scrittura del file
        writeFile(stateDirName + File.separator + usersFilename, registeredUsers);
        // Rilascio della read lock
        usersLock.readLock().unlock();
    }

    /**
     * Metodo per la creazione di una directory per il salvataggio dello stato di un
     * progetto. La directory avrà lo stesso nome del progetto. Al suo interno ci
     * sono un file contenente i nomi dei membri, e un file per ogni carta. Ciascun
     * file relativo a una carta ha lo stesso nome della carta in questione, e al
     * suo interno vi è salvato il suo stato.
     * 
     * @param project il nome del progetto per cui va creata una directory
     * @throws IOException in caso di errori di I/O durante una chiamata alla
     *                     write()
     */
    private static void createProjectDirectory(Project project) throws IOException {
        // Creazione della directory del progetto
        Path projectPath = Paths.get(stateDirName + File.separator + project.getName());
        Files.createDirectory(projectPath);
        // Creazione del file contenente tutti i nickname dei membri del progetto
        writeFile(projectPath.toString() + File.separator + projectMembersFilename, project.getMembers());
        // Creazione dei file delle carte del progetto
        for (Card card : project.getAllCards())
            writeFile(projectPath.toString() + File.separator + card.getName() + ".json", card);
    }

    /**
     * Metodo per il ripristino dello stato dopo il riavvio del server. Se la
     * directory contenente lo stato del sistema non esiste, allora non fa niente.
     * Altrimenti ricrea la lista degli utenti registrati e la lista dei progetti
     * creati, leggendo la prima dal relativo file e ricostruendo la seconda
     * leggendo per ogni progetto la relativa directory.
     */
    private static void restoreState() {
        // Oggetto di tipo File relativo alla root directory dello stato
        File stateDirectory = new File(stateDirName);
        // Se la root directory non esiste non fa nulla (non c'è nessuno stato da
        // ripristinare)
        if (!stateDirectory.isDirectory())
            return;
        try {
            // Ripristina la lista degli utenti registrati
            restoreUsersState();
            // Lista dei nomi delle directories dei progetti, più il nome del file degli
            // utenti
            String[] files = stateDirectory.list();
            assert files != null;
            // Per ogni filename trovato, se è il nome di una directory (e quindi di un
            // progetto) ripristino il relativo
            // progetto
            for (String filename : files) {
                File file = new File(stateDirName + File.separator + filename);
                if (file.isDirectory())
                    // ripristina il progetto e lo aggiunge alla lista dei progetti creati
                    restoreProject(file);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Metodo ausiliario per ripristinare la lista degli utenti registrati, leggendo
     * la lista serializzata dal file apposito (quello il cui nome è memorizzato
     * nella variabile d'istanza usersFilename) e deserializzandola.
     * 
     * @throws IOException in caso di errori di I/O durante una chiamata alla read()
     */
    private static void restoreUsersState() throws IOException {
        // Lettura del file in un'unica stringa
        String str = readFile(stateDirName + File.separator + usersFilename);
        // Deserializzazione
        Gson gson = new Gson();
        User[] users = gson.fromJson(str, User[].class);
        // Ricostruzione della lista
        for (User user : users) {
            // A prescindere da come era il loro stato sul file, nel momento in cui viene
            // fatto partire il server
            // tutti gli utenti saranno offline
            user.setOnline(false);
            // Aggiunta dell'utente alla lista degli utenti registrati
            usersLock.writeLock().lock();
            registeredUsers.add(user);
            usersLock.writeLock().unlock();
        }
    }

    /**
     * Metodo che ripristina un progetto a partire dalla sua directory, e lo
     * aggiunge alla lista dei progetti creati.
     * 
     * @param projectDirectory la directory relativa ad un progetto da ripristinare
     * @throws IOException in caso di errori di I/O durante una chiamata alla read()
     */
    private static void restoreProject(File projectDirectory) throws IOException {
        // Creazione del progetto con il nome della directory
        Project project = new Project(projectDirectory.getName(), null);
        // Tolgo il membro a null dalla lista
        project.getMembers().remove(null);
        // Path della directory del progetto
        String projectPathName = stateDirName + File.separator + projectDirectory.getName();
        // Path del file dei membri del progetto (che si trova all'interno della
        // directory del progetto)
        String membersPathName = projectPathName + File.separator + projectMembersFilename;
        // Lettura dei membri del progetto in un'unica stringa
        String str = readFile(membersPathName);
        // Deserializzazione
        Gson gson = new Gson();
        String[] members = gson.fromJson(str, String[].class);
        // Ogni membro trovato lo riaggiungo alla lista dei membri del progetto
        for (String member : members) {
            project.getMembers().add(member);
        }
        // Lista dei nomi dei files all'interno della directory del progetto (files
        // delle carte + file dei membri)
        String[] files = projectDirectory.list();
        assert files != null;
        // Per ogni file trovato
        for (String filename : files) {
            // Se non è il file dei membri, e quindi è un file di una carta
            if (!filename.equals(projectMembersFilename)) {
                // Lettura del file
                str = readFile(projectPathName + File.separator + filename);
                // Deserializzazione
                Card card = gson.fromJson(str, Card.class);
                // Aggiunta della carta alla lista di appartenenza del progetto
                project.getList(card.getLocation().toLowerCase()).add(card);
                // Aggiunta della carta alla lista del progetto che contiene tutte le carte
                project.getAllCards().add(card);
            }
        }
        // Assegnazione della porta e dell'indirizzo multicast per la chat
        bindChatAddress(project);
        // Aggiunta del progetto alla lista dei progetti creati
        projectsLock.writeLock().lock();
        createdProjects.add(project);
        projectsLock.writeLock().unlock();
    }

    /**
     * Metodo la scrittura di un oggetto su un file. Se il file non esiste lo crea.
     * Successivamente serializza l'oggetto e scrive il risultato sul file.
     *
     * @param pathName   path del file su cui scrivere l'oggetto
     * @param objToWrite l'oggetto da scrivere sul file
     * @throws IOException in caso di errori di I/O durante una chiamata alla
     *                     write()
     */
    private static void writeFile(String pathName, Object objToWrite) throws IOException {
        // Oggetto di tipo Path per aprire il file
        Path membersPath = Paths.get(pathName);
        // Apertura del file. Se non esiste lo crea, altrimenti lo sovrascrive
        FileChannel fileChannel = FileChannel.open(membersPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
        // Serializzazione
        Gson gson = new Gson();
        String str = gson.toJson(objToWrite);
        // Allocazione del byte buffer, inizializzato con la codifica in bytes della
        // stringa risultato della serializzazione
        ByteBuffer byteBuffer = ByteBuffer.wrap(str.getBytes(StandardCharsets.UTF_8));
        // Scrittura sul file
        while (byteBuffer.hasRemaining())
            fileChannel.write(byteBuffer);
    }

    /**
     * Metodo per la lettura di un file. Apre il file in lettura, lo legge e
     * restituisce la stringa letta.
     * 
     * @param filename il file da leggere
     * @return la stringa letta dal file
     * @throws IOException in caso di errori di I/O durante una chiamata alla read()
     */
    private static String readFile(String filename) throws IOException {
        // Oggetto di tipo Path per aprire il file
        Path path = Paths.get(filename);
        // Apertura dei file in lettura
        FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.READ);
        // Allocazione dei byte buffer per leggere dal file
        ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        // string builder per ricostruire la stringa letta
        StringBuilder stringBuilder = new StringBuilder();
        // Finché non ha finito di leggere
        while (fileChannel.read(byteBuffer) != -1) {
            // Preparazione alla scrittura dopo la lettura
            byteBuffer.flip();
            // Finché ci sono bytes letti nel byte buffer
            while (byteBuffer.hasRemaining()) {
                // Concateno i caratteri letti alla stringa tramite lo string builder
                stringBuilder.append(StandardCharsets.UTF_8.decode(byteBuffer).toString());
            }
            // Preparazione alla lettura dopo la scrittura
            byteBuffer.clear();
        }
        // Ritorna la stringa costruita
        return stringBuilder.toString();
    }

    /**
     * Metodo per gestire la richiesta di login: controlla se l'utente può fare una
     * login verificando che si sia registrato, che la password sia corretta e che
     * non sia già online. Una volta verificato il tutto setta l'utente a online.
     * Genera un messaggio di risposta per il client. Il metodo è thread safe.
     * 
     * @param nickname il nome con cui si è registrato l'utente
     * @param password la password fornita al momento della registrazione
     * @return un messaggio di risposta per il client, contenente un valore di
     *         Replies che notifichi il client sull'esito dell'operazione e, nel
     *         caso sia positivo, l'oggetto di tipo User relativo all'utente
     */
    public static ClientServerMessage login(String nickname, String password) {
        ClientServerMessage message = new ClientServerMessage();
        // Utente fittizio per la ricerca nella lista degli utenti registrati
        // (ridefinita la equals: due utenti sono
        // uguali se hanno lo stesso nickname)
        User tmp = new User(nickname, password);
        // Acquisizione della write lock degli utenti
        usersLock.writeLock().lock();
        // Indice dell'utente nella lista degli utenti registrati del server
        int index = registeredUsers.indexOf(tmp);
        // Controllo dei possibili errori
        if (index == -1) { // Se non è nella lista significa che l'utente non è registrato
            // Rilascio della write lock degli utenti
            usersLock.writeLock().unlock();
            message.setReply(Replies.NOT_REGISTERED);
        } else if (!registeredUsers.get(index).getPassword().equals(password)) { // Password sbagliata
            // Rilascio della write lock degli utenti
            usersLock.writeLock().unlock();
            message.setReply(Replies.WRONG_PASSW);
        } else if (registeredUsers.get(index).isOnline()) { // Utente già online
            // Rilascio della write lock degli utenti
            usersLock.writeLock().unlock();
            message.setReply(Replies.ALREADY_ONLINE);
        } else { // Ok
            // Setta l'utente come online
            registeredUsers.get(index).setOnline(true);
            // Riferimento all'utente
            User user = registeredUsers.get(index);
            // Rilascio della write lock degli utenti
            usersLock.writeLock().unlock();
            // Setta un campo del messaggio con il riferimento all'utente
            message.setUser(user);
            // Setta la reply a OK
            message.setReply(Replies.OK);
        }
        return message;
    }

    /**
     * Metodo per gestire la richiesta di logout: setta l'utente (l'oggetto passato
     * per argomento) a offline e controlla che sia nella lista degli utenti
     * registrati. Se non c'è non fa niente, altrimenti lo aggiorna nella lista
     * degli utenti registrati. Genera un messaggio di risposta per il client. Il
     * metodo è thread safe.
     * 
     * @param user l'utente che vuole fare la logout
     * @return un messaggio di risposta per il client, contenente un valore di
     *         Replies che notifichi il client sull'esito dell'operazione
     */
    public static ClientServerMessage logout(User user) {
        ClientServerMessage message = new ClientServerMessage();
        // Utente offline
        user.setOnline(false);
        // Cancellazione della lista degli utenti per non avere riferimenti circolari in
        // json (nella lista degli utenti registrati del server, per ogni elemento non 
        // riporto mai la rispettiva lista degli utenti)
        user.setUsersList(new ArrayList<>());
        // Lista delle chat inutile da mantenere
        user.setChatsList(new ArrayList<>());
        // Acquisizione write lock degli utenti
        usersLock.writeLock().lock();
        // Indice dell'utente nella lista degli utenti registrati
        int index = registeredUsers.indexOf(user);
        if (index != -1) {
            // Aggiornamento dell'utente nella lista
            registeredUsers.set(index, user);
            // Rilascio della write lock degli utenti
            usersLock.writeLock().unlock();
            // Ok
            message.setReply(Replies.OK);
            return message;
        }
        // Rilascio della write lock degli utenti
        usersLock.writeLock().unlock();
        // Utente non trovato nella lista
        message.setReply(Replies.UNKNOWN_ERROR);
        return message;
    }

    /**
     * Metodo per gestire la richiesta di visualizzare la lista dei progetti: cerca
     * nella lista dei progetti creati tutti i progetti di cui l'utente è membro.
     * Genera un messaggio di risposta per il client. Il metodo è thread safe.
     * 
     * @param nickname il nome con cui l'utente si è registrato
     * @return un messaggio di risposta per il client, contenente un valore di
     *         Replies che notifichi il client sull'esito dell'operazione e, nel
     *         caso sia positivo, la lista di progetti di cui l'utente è membro
     */
    public static ClientServerMessage listProjects(String nickname) {
        ClientServerMessage message = new ClientServerMessage();
        ArrayList<Project> userProjects = new ArrayList<>();
        // Acquisizione della read lock
        projectsLock.readLock().lock();
        // Per ogni progetto creato controllo se l'utente è membro
        for (Project project : createdProjects) {
            if (project.getMembers().contains(nickname))
                userProjects.add(project);
        }
        // Rilascio della read lock
        projectsLock.readLock().unlock();
        message.setReply(Replies.OK);
        message.setProjects(userProjects);
        return message;
    }

    /**
     * Metodo per gestire la richiesta di creazione di un progetto: controlla se è
     * possibile creare un progetto e, se lo è, lo aggiunge alla lista dei progetti
     * creati e manda una callback agli utenti per aggiornare le liste delle chat.
     * Genera un messaggio di risposta per il client. Il metodo è thread safe.
     * 
     * @param nickname    il nome con cui l'utente si è registrato
     * @param projectName il nome del progetto da creare
     * @return un messaggio di risposta per il client, contenente un valore di
     *         Replies che notifichi il client sull'esito dell'operazione
     */
    public static ClientServerMessage createProject(String nickname, String projectName) {
        ClientServerMessage message = new ClientServerMessage();
        // Creazione del progetto
        Project project = new Project(projectName, nickname);
        // Controllo di disponibilità di indirizzi multicast
        if (!bindChatAddress(project)) {
            message.setReply(Replies.UNABLE_CREATE_PROJECT);
            return message;
        }
        // Se non esiste già un progetto con quel nome lo aggiungo (equals per i
        // progetti ridefinita per nome). Controllo e modifica atomici
        projectsLock.writeLock().lock();
        if (createdProjects.contains(project)) {
            // Rilascio della write lock
            projectsLock.writeLock().unlock();
            message.setReply(Replies.PROJECT_EXISTS);
        } else {
            // Aggiorno la lista di tutti i progetti
            createdProjects.add(project);
            // Rilascio della write lock
            projectsLock.writeLock().unlock();
            message.setReply(Replies.OK);
            // Callback per le liste delle chat
            updateAllChatsLists();
            sendChatMsg(project, nickname + " ha creato il progetto " + projectName);
        }
        return message;
    }

    /**
     * Metodo per gestire la richiesta di aggiunta di un membro a un progetto:
     * controlla se è possibile effettuare l'operazione verificando che il progetto
     * esista, che l'utente ne faccia parte, che il nuovo membro sia un utente
     * registrato e che non sia già membro del progetto. Se è possibile lo aggiunge
     * ai membri del progetto, il quale si trova nella lista dei progetti creati.
     * Genera un messaggio di risposta per il client. Il metodo è thread safe.
     * 
     * @param nickname      il nickname dell'utente che richiede l'operazione
     * @param projectName   il nome del progetto a cui aggiungere un nuovo membro
     * @param nickNewMember il nickname dell'utente da aggiungere al progetto
     * @return un messaggio di risposta per il client, contenente un valore di
     *         Replies che notifichi il client sull'esito dell'operazione
     */
    public static ClientServerMessage addMember(String nickname, String projectName, String nickNewMember) {
        ClientServerMessage message = new ClientServerMessage();
        // Acquisizione della write lock dei progetti
        projectsLock.writeLock().lock();
        // Indice del progetto nella lista dei progetti creati del server
        int projectIndex = createdProjects.indexOf(new Project(projectName, null));
        // Controllo dell'esistenza del progetto
        if (projectIndex == -1) {
            // Rilascio della write lock dei progetti
            projectsLock.writeLock().unlock();
            message.setReply(Replies.NONEXISTENT_PROJECT);
            return message;
        }
        // Riferimento al progetto
        Project project = createdProjects.get(projectIndex);
        // Controllo dell'appartenenza al progetto dell'utente che ha richiesto
        // l'operazione
        if (!project.getMembers().contains(nickname)) {
            // Rilascio della write lock dei progetti
            projectsLock.writeLock().unlock();
            message.setReply(Replies.NONEXISTENT_PROJECT);
            return message;
        }
        // Acquisizione della read lock degli utenti
        usersLock.readLock().lock();
        // Indice del nuovo membro del progetto nella lista degli utenti registrati
        int newMemberIndex = registeredUsers.indexOf(new User(nickNewMember, null));
        // Controllo che il nuovo membro sia un utente registrato
        if (newMemberIndex == -1) {
            // Rilascio della read lock degli utenti
            usersLock.readLock().unlock();
            // Rilascio della write lock dei progetti
            projectsLock.writeLock().unlock();
            message.setReply(Replies.NOT_REGISTERED);
            return message;
        }
        // Rilascio della read lock degli utenti
        usersLock.readLock().unlock();
        // Controllo che l'utente da aggiungere non sia già membro del progetto
        if (project.getMembers().contains(nickNewMember)) {
            // Rilascio della write lock dei progetti
            projectsLock.writeLock().unlock();
            message.setReply(Replies.ALREADY_MEMBER);
            return message;
        }
        // Aggiunta del nuovo membro al progetto (modificando il progetto nella lista
        // dei progetti creati)
        project.getMembers().add(nickNewMember);
        // Rilascio della write lock dei progetti
        projectsLock.writeLock().unlock();
        // Ok
        message.setReply(Replies.OK);
        // Callback per le liste delle chat
        updateAllChatsLists();
        // Messaggio sulla chat del progetto che notifica l'aggiunta del nuovo membro
        sendChatMsg(project, nickname + " ha aggiunto un nuovo membro: " + nickNewMember);
        return message;
    }

    /**
     * Metodo per gestire la richiesta di visualizzare la lista dei membri di un
     * progetto: controlla se è possibile effettuare l'operazione verificando che il
     * progetto esista e che l'utente ne faccia parte. Genera un messaggio di
     * risposta per il client. Include infine nel messaggio la lista dei membri. Il
     * metodo è thread safe.
     * 
     * @param nickname    il nome con cui l'utente si è registrato
     * @param projectName il nome del progetto
     * @return un messaggio di risposta per il client, contenente un valore di
     *         Replies che notifichi il client sull'esito dell'operazione e, nel
     *         caso sia positivo, la lista dei membri del progetto
     */
    public static ClientServerMessage showMembers(String nickname, String projectName) {
        ClientServerMessage message = new ClientServerMessage();
        // Acquisizione della read lock
        projectsLock.readLock().lock();
        // Indice del progetto nella lista dei progetti creati del server
        int projectIndex = createdProjects.indexOf(new Project(projectName, null));
        // Controllo dell'esistenza del progetto
        if (projectIndex == -1) {
            // Rilascio della read lock
            projectsLock.readLock().unlock();
            message.setReply(Replies.NONEXISTENT_PROJECT);
            return message;
        }
        // Riferimento al progetto
        Project project = createdProjects.get(projectIndex);
        // Controllo dell'appartenenza al progetto dell'utente che ha richiesto
        // l'operazione
        if (!project.getMembers().contains(nickname)) {
            // Rilascio della read lock
            projectsLock.readLock().unlock();
            message.setReply(Replies.NONEXISTENT_PROJECT);
            return message;
        }
        // Aggiungo la lista dei membri del progetto al messaggio
        message.setMembers(project.getMembers());
        // Rilascio della read lock
        projectsLock.readLock().unlock();
        // Ok
        message.setReply(Replies.OK);
        return message;
    }

    /**
     * Metodo per gestire la richiesta di visualizzare le carte di un progetto:
     * controlla se è possibile effettuare l'operazione verificando che il progetto
     * esista e che l'utente ne faccia parte. Genera un messaggio di risposta per il
     * client. Include infine nel messaggio la lista delle carte del progetto. Il
     * metodo è thread safe.
     * 
     * @param nickname    il nome con cui si è registrato l'utente
     * @param projectName il nome del progetto
     * @return un messaggio di risposta per il client, contenente un valore di
     *         Replies che notifichi il client sull'esito dell'operazione e, nel
     *         caso sia positivo, la lista delle carte del progetto
     */
    public static ClientServerMessage showCards(String nickname, String projectName) {
        ClientServerMessage message = new ClientServerMessage();
        // Acquisizione della read lock
        projectsLock.readLock().lock();
        // Indice del progetto nella lista dei progetti creati del server
        int projectIndex = createdProjects.indexOf(new Project(projectName, null));
        // Controllo dell'esistenza del progetto
        if (projectIndex == -1) {
            // Rilascio della read lock
            projectsLock.readLock().unlock();
            message.setReply(Replies.NONEXISTENT_PROJECT);
            return message;
        }
        // Riferimento al progetto
        Project project = createdProjects.get(projectIndex);
        // Controllo dell'appartenenza al progetto dell'utente che ha richiesto
        // l'operazione
        if (!project.getMembers().contains(nickname)) {
            // Rilascio della read lock
            projectsLock.readLock().unlock();
            message.setReply(Replies.NONEXISTENT_PROJECT);
            return message;
        }
        // Lista di carte del progetto
        ArrayList<String> cardNames = new ArrayList<>();
        // Metto in lista tutte le carte del progetto
        for (Card card : project.getAllCards()) {
            cardNames.add(card.getName());
        }
        // Rilascio della read lock
        projectsLock.readLock().unlock();
        // Ok
        message.setReply(Replies.OK);
        // Aggiungo la lista di carte al messaggio
        message.setCards(cardNames);
        return message;
    }

    /**
     * Metodo per gestire la richiesta di visualizzare le informazioni di una carta:
     * controlla se è possibile effettuare l'operazione verificando che il progetto
     * esista, che l'utente ne faccia parte e che la carta esista. Genera un
     * messaggio di risposta per il client. Include infine la carta nel messaggio.
     * Il metodo è thread safe.
     * 
     * @param nickname    il nome con cui l'utente si è registrato
     * @param projectName il nome del progetto
     * @param cardName    il nome della carta
     * @return un messaggio di risposta per il client, contenente un valore di
     *         Replies che notifichi il client sull'esito dell'operazione e, nel
     *         caso sia positivo, la carta richiesta
     */
    public static ClientServerMessage showCard(String nickname, String projectName, String cardName) {
        ClientServerMessage message = new ClientServerMessage();
        // Acquisizione della read lock
        projectsLock.readLock().lock();
        // Indice del progetto nella lista dei progetti creati del server
        int projectIndex = createdProjects.indexOf(new Project(projectName, null));
        // Controllo dell'esistenza del progetto
        if (projectIndex == -1) {
            // Rilascio della read lock
            projectsLock.readLock().unlock();
            message.setReply(Replies.NONEXISTENT_PROJECT);
            return message;
        }
        // Riferimento al progetto
        Project project = createdProjects.get(projectIndex);
        // Controllo dell'appartenenza al progetto dell'utente che ha richiesto
        // l'operazione
        if (!project.getMembers().contains(nickname)) {
            // Rilascio della read lock
            projectsLock.readLock().unlock();
            message.setReply(Replies.NONEXISTENT_PROJECT);
            return message;
        }
        int cardIndex = project.getAllCards().indexOf(new Card(cardName, null));
        // Controllo dell'appartenenza della carta al progetto
        if (cardIndex == -1) {
            // Rilascio della read lock
            projectsLock.readLock().unlock();
            message.setReply(Replies.NONEXISTENT_CARD);
            return message;
        }
        // Includo la carta nel messaggio
        message.setCard(project.getAllCards().get(cardIndex));
        // Rilascio della read lock
        projectsLock.readLock().unlock();
        // Ok
        message.setReply(Replies.OK);
        return message;
    }

    /**
     * Metodo per gestire la richiesta di aggiungere una carta ad un progetto:
     * controlla se è possibile effettuare l'operazione verificando che il progetto
     * esista, che l'utente ne faccia parte e che non esista già una carta con lo
     * stesso nome all'interno del progetto. Una volta verificato che è possibile
     * aggiungere la carta al progetto, la aggiunge. Genera un messaggio di risposta
     * per il client. Il metodo è thread safe.
     * 
     * @param nickname    il nome con cui l'utente si è registrato
     * @param projectName il nome del progetto
     * @param cardName    il nome della carta
     * @param description la descrizione della carta
     * @return un messaggio di risposta per il client, contenente un valore di
     *         Replies che notifichi il client sull'esito dell'operazione
     */
    public static ClientServerMessage addCard(String nickname, String projectName, String cardName,
            String description) {
        ClientServerMessage message = new ClientServerMessage();
        // Acquisizione della write lock
        projectsLock.writeLock().lock();
        // Indice del progetto nella lista dei progetti creati del server
        int projectIndex = createdProjects.indexOf(new Project(projectName, null));
        // Controllo dell'esistenza del progetto
        if (projectIndex == -1) {
            // Rilascio della write lock
            projectsLock.writeLock().unlock();
            message.setReply(Replies.NONEXISTENT_PROJECT);
            return message;
        }
        // Riferimento al progetto
        Project project = createdProjects.get(projectIndex);
        // Controllo dell'appartenenza al progetto dell'utente che ha richiesto
        // l'operazione
        if (!project.getMembers().contains(nickname)) {
            // Rilascio della write lock
            projectsLock.writeLock().unlock();
            message.setReply(Replies.NONEXISTENT_PROJECT);
            return message;
        }
        // Creazione della carta
        Card card = new Card(cardName, description);
        // Controllo se esiste già una carta con lo stesso nome del progetto (ridefinito
        // equals per uguaglianza in
        // base al nome)
        if (project.getAllCards().contains(card)) {
            // Rilascio della write lock
            projectsLock.writeLock().unlock();
            message.setReply(Replies.CARD_EXISTS);
            return message;
        }
        // Aggiungo la carta al progetto (sia nella lista delle carte totali, sia nella
        // lista TODO)
        project.getAllCards().add(card);
        project.getToDo().add(card);
        // Rilascio della write lock
        projectsLock.writeLock().unlock();
        // Ok
        message.setReply(Replies.OK);
        // Manda una notifica per gli utenti sulla chat del progetto
        sendChatMsg(project, nickname + " ha aggiunto la carta " + cardName);
        return message;
    }

    /**
     * Metodo per gestire la richiesta di spostamento di una carta da una lista a
     * un'altra all'interno del progetto: controlla se è possibile effettuare
     * l'operazione verificando che il progetto esista, che l'utente ne faccia
     * parte, che la lista di partenza e destinazione non siano uguali, che siano
     * rispettati i vincoli sugli spostamenti e che la carta da spostare sia
     * effettivamente nella lista di partenza. Infine sposta la carta da una lista
     * all'altra modificando il progetto che si trova nella lista dei progetti
     * creati del server. Genera un messaggio di risposta per il client. Il metodo è
     * thread safe.
     * 
     * @param nickname    il nome con cui l'utente si è registrato
     * @param projectName il nome del progetto
     * @param cardName    il nome della carta
     * @param sourceList  la lista in cui si trova la carta
     * @param destList    la lista in cui spostare la carta
     * @return un messaggio di risposta per il client, contenente un valore di
     *         Replies che notifichi il client sull'esito dell'operazione
     */
    public static ClientServerMessage moveCard(String nickname, String projectName, String cardName, String sourceList,
            String destList) {
        ClientServerMessage message = new ClientServerMessage();
        // Prendo entrambi i nomi delle liste maiuscoli
        String sourceListName = sourceList.toUpperCase();
        String destListName = destList.toUpperCase();
        // Controllo di esistenza delle due liste
        if (isNotList(sourceListName) || isNotList(destListName)) {
            message.setReply(Replies.NONEXISTENT_LIST);
            return message;
        }
        // Controllo che lista di partenza e di destinazione non siano uguali
        if (sourceListName.equals(destListName)) {
            message.setReply(Replies.CARD_EXISTS);
            return message;
        }
        // Controllo che siano rispettati i vincoli sullo spostamento
        switch (destListName) {
            case "TODO" -> {
                // Nessuna carta può essere spostata in TODO
                message.setReply(Replies.MOVE_FORBIDDEN);
                return message;
            }
            case "INPROGRESS" -> {
                // Controllo che non venga spostata in INPROGRESS da DONE (vietato)
                if (sourceListName.equals("DONE")) {
                    message.setReply(Replies.MOVE_FORBIDDEN);
                    return message;
                }
            }
            case "TOBEREVISED" -> {
                // Controllo che venga spostata in TOBEREVISED da INPROGRESS (unica lista di
                // partenza permessa)
                if (!sourceListName.equals("INPROGRESS")) {
                    message.setReply(Replies.MOVE_FORBIDDEN);
                    return message;
                }
            }
            case "DONE" -> {
                // Controllo che non venga spostata in DONE direttamente da TODO (vietato)
                if (sourceListName.equals("TODO")) {
                    message.setReply(Replies.MOVE_FORBIDDEN);
                    return message;
                }
            }
            default -> {
            }
        }
        // Acquisizione della write lock
        projectsLock.writeLock().lock();
        // Indice del progetto nella lista dei progetti creati del server
        int projectIndex = createdProjects.indexOf(new Project(projectName, null));
        // Controllo dell'esistenza del progetto
        if (projectIndex == -1) {
            // Rilascio della write lock
            projectsLock.writeLock().unlock();
            message.setReply(Replies.NONEXISTENT_PROJECT);
            return message;
        }
        // Riferimento al progetto
        Project project = createdProjects.get(projectIndex);
        // Controllo dell'appartenenza al progetto dell'utente che ha richiesto
        // l'operazione
        if (!project.getMembers().contains(nickname)) {
            // Rilascio della write lock
            projectsLock.writeLock().unlock();
            message.setReply(Replies.NONEXISTENT_PROJECT);
            return message;
        }
        // Riferimento alla lista di partenza
        ArrayList<Card> sList = getList(sourceListName, projectIndex);
        // Riferimento alla lista di destinazione
        ArrayList<Card> dList = getList(destListName, projectIndex);
        // Indice della carta nella lista di partenza
        int cardIndex = sList.indexOf(new Card(cardName, null));
        // Controllo che la carta da spostare sia effettivamente nella lista di partenza
        if (cardIndex == -1) {
            // Rilascio della write lock
            projectsLock.writeLock().unlock();
            message.setReply(Replies.NONEXISTENT_CARD);
            return message;
        }
        // Spostamento della carta da sourceList a destList, e aggiornamento della sua
        // storia
        Card card = sList.remove(cardIndex);
        card.updateHistory(destListName);
        dList.add(card);
        // Indice della carta nella lista di tutte le carte del progetto
        int cardIndex2 = project.getAllCards().indexOf(card);
        // Aggiornamento della carta nella lista di tutte le carte del progetto
        project.getAllCards().set(cardIndex2, card);
        // Rilascio della write lock
        projectsLock.writeLock().unlock();
        // Ok
        message.setReply(Replies.OK);
        // Notifica dello spostamento agli altri utenti, con un messaggio sulla chat
        sendChatMsg(project, nickname + " ha spostato la carta " + cardName + " dalla lista " + sourceListName
                + " alla lista " + destListName + ".");
        return message;
    }

    /**
     * Metodo ausiliario: prende una stringa e restituisce true se non è il nome di
     * una delle 4 liste dei progetti
     * 
     * @param listName la stringa in ingresso
     * @return true se non è il nome di una lista dei progetti, false altrimenti
     */
    private static boolean isNotList(String listName) {
        boolean isList;
        switch (listName) {
            case "TODO", "INPROGRESS", "TOBEREVISED", "DONE" -> isList = true;
            default -> isList = false;
        }
        return !isList;
    }

    /**
     * Metodo ausiliario: dato il nome maiuscolo di una lista di un progetto, e dato
     * l'indice del progetto nella lista dei progetti creati del server, restituisce
     * il riferimento alla lista del progetto in questione.
     * 
     * @param listName     nome della lista maiuscolo
     * @param projectIndex indice del progetto nella lista dei progetti del server
     * @return il riferimento alla lista del progetto, oppure null se non esiste
     */
    private static ArrayList<Card> getList(String listName, int projectIndex) {
        ArrayList<Card> list;
        switch (listName) {
            case "TODO" -> list = createdProjects.get(projectIndex).getToDo();
            case "INPROGRESS" -> list = createdProjects.get(projectIndex).getInProgress();
            case "TOBEREVISED" -> list = createdProjects.get(projectIndex).getToBeRevised();
            case "DONE" -> list = createdProjects.get(projectIndex).getDone();
            default -> list = null;
        }
        return list;
    }

    /**
     * Metodo per gestire la richiesta di cancellazione di un progetto: controlla se
     * è possibile effettuare l'operazione verificando che il progetto esista, che
     * l'utente ne faccia parte e che tutte le carte del progetto siano nella lista
     * DONE. Successivamente cancella il progetto dalla lista dei progetti del
     * server e aggiorna le liste delle chat dei membri del progetto. Genera un
     * messaggio di risposta per il client. Il metodo è thread safe.
     * 
     * @param nickname    il nome con cui l'utente si è registrato
     * @param projectName il nome del progetto
     * @return un messaggio di risposta per il client, contenente un valore di
     *         Replies che notifichi il client sull'esito dell'operazione
     */
    public static ClientServerMessage cancelProject(String nickname, String projectName) {
        ClientServerMessage message = new ClientServerMessage();
        // Acquisizione della write lock
        projectsLock.writeLock().lock();
        // Indice del progetto nella lista dei progetti creati del server
        int projectIndex = createdProjects.indexOf(new Project(projectName, null));
        // Controllo dell'esistenza del progetto
        if (projectIndex == -1) {
            // Rilascio della write lock
            projectsLock.writeLock().unlock();
            message.setReply(Replies.NONEXISTENT_PROJECT);
            return message;
        }
        // Riferimento al progetto
        Project project = createdProjects.get(projectIndex);
        // Controllo dell'appartenenza al progetto dell'utente che ha richiesto
        // l'operazione
        if (!project.getMembers().contains(nickname)) {
            // Rilascio della write lock
            projectsLock.writeLock().unlock();
            message.setReply(Replies.NONEXISTENT_PROJECT);
            return message;
        }
        // Controllo che tutte le carte siano nella lista DONE
        boolean ok = true;
        for (Card card : project.getAllCards()) {
            if (!card.getLocation().equals("DONE")) {
                ok = false;
                break;
            }
        }
        if (!ok) {
            // Rilascio della write lock
            projectsLock.writeLock().unlock();
            message.setReply(Replies.CANCEL_FORBIDDEN);
            return message;
        }
        // Indirizzo multicast della chat del progetto
        String chatAddress = project.getMulticastAddress();
        // Cancellazione del progetto
        createdProjects.remove(project);
        // Rilascio della write lock
        projectsLock.writeLock().unlock();
        // Aggiungo l'indirizzo multicast del progetto cancellato alla lista degli
        // indirizzi da riutilizzare
        try {
            addressesToBeReallocated.put(chatAddress);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // Ok
        message.setReply(Replies.OK);
        // Aggiornamento della lista delle chat dei membri del progetto
        updateAllChatsLists();
        return message;
    }

    /**
     * Metodo ausiliario che manda un messaggio sulla chat di un progetto.
     * 
     * @param project il nome del progetto
     * @param message il messaggio da inviare
     */
    public static void sendChatMsg(Project project, String message) {
        // Composizione del messaggio da inviare
        String chatMsg = "Messaggio da Worth: " + "\"" + message + "\"";
        // Codifica del messaggio in bytes
        byte[] msgBytes = chatMsg.getBytes();
        // Creazione del datagram packet da inviare
        DatagramPacket packet = new DatagramPacket(msgBytes, msgBytes.length, project.getChatAddress(),
                project.getChatPort());
        try (DatagramSocket socket = new DatagramSocket()) {
            // Invio
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Metodo utilizzato dall'oggetto remoto. Controlla se il client è registrato
     * al servizio di RMI callback, e in caso contrario lo aggiunge. Il metodo è 
     * thread safe.
     * 
     * @param clientStub lo stub dell'oggetto remoto del client
     * @return true se era già registrato, false se lo ha registrato adesso
     */
    public static boolean putIfAbsent(NotifyEventInterface clientStub) {
        boolean contains = true;
        // Acquisizione della write lock 
        callbackLock.writeLock().lock();
        // Se non è nella lista lo aggiunge
        if (!clientsRegisteredForCallback.contains(clientStub)) {
            contains = false;
            clientsRegisteredForCallback.add(clientStub);
        }
        // Rilascio della write lock
        callbackLock.writeLock().unlock();
        return contains;
    }

    /**
     * Cancella un client dal servizio di RMI callback. Il metodo è thread safe.
     * 
     * @param clientStub lo stub dell'oggetto remoto del client
     */
    public static void removeStub(NotifyEventInterface clientStub) {
        callbackLock.writeLock().lock();
        clientsRegisteredForCallback.remove(clientStub);
        callbackLock.writeLock().unlock();
    }


    /**
     * Metodo utilizzato dall'oggetto remoto nella fase di registrazione. Controlla
     * se l'utente si trova nella lista degli utenti registrati, e in caso contrario
     * lo aggiunge. Il metodo è thread safe.
     * 
     * @param user l'oggetto di tipo User relativo all'utente da registrare
     * @return true se era già registrato, false se lo ha registrato adesso
     */
    public static boolean putIfAbsent(User user) {
        boolean contains = true;
        // Acquisizione della write lock degli utenti
        usersLock.writeLock().lock();
        // Se non è già registrato lo aggiunge alla lista
        if (!registeredUsers.contains(user)) {
            contains = false;
            registeredUsers.add(user);
        }
        // Rilascio della write lock degli utenti
        usersLock.writeLock().unlock();
        return contains;
    }

    /**
     * Metodo per l'assegnazione di un indirizzo multicast e di una porta per le
     * chat ai progetti. Parte dall'indirizzo multicast contenuto nella variabile
     * d'istanza multicastAddress, assegna al progetto l'indirizzo successivo e poi
     * setta multicastAddress con quest'ultimo. La porta viene assegnata con la
     * variabile d'istanza multicastPort. Il metodo è thread safe.
     * 
     * @param project il progetto a cui assegnare l'indirizzo multicast e la porta
     * @return true se ha avuto successo, false altrimenti
     */
    private static synchronized boolean bindChatAddress(Project project) {
        // Se la lista degli indirizzi da riassegnare non è vuota assegno uno di quelli
        if (!addressesToBeReallocated.isEmpty()) {
            project.setChatAddress(addressesToBeReallocated.remove());
        } else { // Altrimenti assegno l'indirizzo successivo a quello contenuto nella variabile multicastAddress
            // Divisione di multicastAddress nei singoli numeri che lo compongono. Si usa "\\." invece di "." perché con
            // il punto divide ogni carattere. I numeri dell'indirizzo sono ancora in forma di stringhe.
            String[] strAddress = multicastAddress.split("\\.");
            // Array di interi per fare il parsing delle componenti dell'indirizzo a int
            int[] address = new int[strAddress.length];
            // Per ogni componente dell'indirizzo faccio il parsing a intero
            for (int i = 0; i < address.length; i++)
                address[i] = Integer.parseInt(strAddress[i]);
            // Se la componente più a destra dell'indirizzo è minore di 255 la incremento e ho trovato il nuovo indirizzo
            if (address[3] < 255) {
                address[3]++;
            } else if (address[2] < 255) { // Altrimenti incremento quella precedente e metto quella più a destra a 0
                address[2]++;
                address[3] = 0;
            } else if (address[1] < 255) { // Altrimenti incremento quella ancora precedente e metto le ultime due a 0
                address[1]++;
                address[2] = 0;
                address[3] = 0;
            } else return false;    // Altrimenti ho finito gli indirizzi multicast da assegnare
            // String builder per la costruzione del nuovo indirizzo
            StringBuilder chatAddress = new StringBuilder();
            // Costruzione dell'indirizzo
            for (int i = 0; i < address.length - 1; i++) {
                chatAddress.append(address[i]).append(".");
            }
            chatAddress.append(address[address.length - 1]);
            // Assegnamento al progetto dell'indirizzo IP multicast
            project.setChatAddress(chatAddress.toString());
            // Aggiornamento della variabile d'istanza multicastAddress con l'indirizzo multicast assegnato
            multicastAddress = chatAddress.toString();
        }
        // Assegnamento della porta per il multicast
        project.setChatPort(multicastPort);
        return true;
    }

    /**
     * Metodo che notifica a tutti gli utenti un cambiamento nella lista degli utenti registrati del server,
     * mediante una callback. Chiama quindi l'apposito metodo di ogni stub degli oggetti remoti dei client registrati
     * al servizio di callback, in modo da inviargli la lista aggiornata. Il metodo è thread safe.
     */
    public static void updateAllUsersLists() {
        usersLock.readLock().lock();
        callbackLock.readLock().lock();
        // Per ogni utente registrato al servizio di callback
        for (NotifyEventInterface client : clientsRegisteredForCallback)
            try {
                // Invio la lista aggiornata
                client.notifyUsersEvent(registeredUsers);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        callbackLock.readLock().unlock();
        usersLock.readLock().unlock();
    }

    /**
     * Metodo per l'aggiornamento delle liste di chat degli utenti tramite callback. Utilizzata quando viene aggiunto un
     * membro ad un progetto o quando un progetto viene cancellato. Il metodo è thread safe.
     */
    public static void updateAllChatsLists() {
        projectsLock.readLock().lock();
        callbackLock.readLock().lock();
        // Per ogni client iscritto al servizio di callback
        for (NotifyEventInterface client : clientsRegisteredForCallback)
            try {
                // Chiamata al metodo remoto del client, passandogli la lista dei progetti creati
                client.notifyChatsEvent(createdProjects);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        callbackLock.readLock().unlock();
        projectsLock.readLock().unlock();
    }

}