import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Implementazione di un utente in Worth. Gli utenti sono identificati 
 * univocamente dal loro nome (nickname).
 */
public class User implements Serializable {
    @Serial
    private static final long serialVersionUID = -7433639455777417860L;

    /** Il nickname dell'utente */
    private final String nickname;

    /** La password dell'utente */
    private final String password;

    /** True se l'utente è online, false se è offline */
    private boolean online;

    /** 
     * Una copia della lista degli utenti reigstrati del server, aggiornata 
     * mediante il servizio di callback 
     */
    private ArrayList<User> users;

    /** 
     * La lista delle chat dei progetti dell'utente, aggiornata mediante il 
     * servizio di callback 
     */
    private ArrayList<Chat> chats;

    /**
     * Costruttore
     * 
     * @param nickname il nickname dell'utente
     * @param password la password dell'utente
     */
    public User(String nickname, String password) {
        this.nickname = nickname;
        this.password = password;
        this.online = false;
        this.users = new ArrayList<>();
        this.chats = new ArrayList<>();
    }

    public String getNickname() {
        return this.nickname;
    }

    public String getPassword() {
        return this.password;
    }

    public ArrayList<User> getUsersList() {
        return this.users;
    }

    /**
     * Controlla la sua copia della lista degli utenti registrati, e restituisce 
     * la lista degli utenti online.
     * 
     * @return la lista degli utenti online
     */
    public ArrayList<String> getOnlineUsersList() {
        ArrayList<String> onlineUsers = new ArrayList<>();
        // Aggiungo alla lista da restituire gli utenti online
        for (User user : this.users) {
            if (user.isOnline())
                onlineUsers.add(user.getNickname());
        }
        return onlineUsers;
    }

    public void setUsersList(ArrayList<User> usersList) {
        this.users = usersList;
    }

    public ArrayList<Chat> getChatsList() {
        return this.chats;
    }

    /**
     * Metodo utilizzato dal server per azzerare la lista delle chat dell'utente,
     * in fase di logout o in fase di salvataggio dello stato
     * 
     * @param chatsUpdate la nuova lista delle chat
     */
    public void setChatsList(ArrayList<Chat> chatsUpdate) {
            this.chats = chatsUpdate;
    }

    public boolean isOnline() {
        return this.online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }


    /**
     * Lettura di una chat di un progetto.
     * 
     * @param projectName il nome del progetto
     */
    public void readChat(String projectName) {
        System.out.println(this.chats.get(this.chats.indexOf(new Chat(projectName))));
    }

    /**
     * Invio di un messaggio sulla chat di un progetto.
     * 
     * @param projectName il nome del progetto
     * @param message     il messaggio da inviare
     */
    public void sendChatMsg(String projectName, String message) {
        // Composizione del messaggio
        String chatMsg = this.nickname + " ha detto: " + "\"" + message + "\"";
        // Codifica del messaggio in bytes
        byte[] msgBytes = chatMsg.getBytes(StandardCharsets.UTF_8);
        // Indice della chat nella lista delle chats dell'utente
        int chatIndex = this.chats.indexOf(new Chat(projectName));
        // Riferimento alla chat su cui inviare il messaggio
        Chat chat = this.chats.get(chatIndex);
        // Creazione del datagram packet da inviare
        DatagramPacket datagramPacket = new DatagramPacket(msgBytes, msgBytes.length, chat.getAddress(), chat.getPort());
        try (DatagramSocket socket = new DatagramSocket()) {
            // Invio
            socket.send(datagramPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof User))
            return false;
        // Due utenti sono uguali se hanno lo stesso nome
        return this.nickname.equals(((User) obj).nickname);
    }

    @Override
    public String toString() {
        String state = (this.online ? "online" : "offline");
        return this.nickname + ": " + state;
    }
}