import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;

/**
 * Implementazione dei messaggi che vengono scambiati tra client e server nella 
 * comunicazione socket. Un messaggio all'interno contiene molti campi che possono 
 * essere settati da client e server per scambiarsi informazioni.
 */
public class ClientServerMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = -6264504071060402139L;

    /** La richiesta che il client fa al server */
    private Commands comando;

    /** 
     * La risposta che il server include nei messaggi per il client, per 
     * informarlo sull'esito dell'operazione 
     */
    private Replies reply;

    /** Un campo per un oggetto di tipo User */
    private User user;

    /** Una lista di progetti */
    private ArrayList<Project> projects;

    /** 
     * Una lista di nickname (utilizzata per inviare i membri di un progetto 
     * dal server al client) 
     */
    private ArrayList<String> members;

    /** Una lista di nomi di carte */
    private ArrayList<String> cards;

    /** Un oggetto di tipo Card */
    private Card card;

    /** Il nickname di chi richiede l'operazione (da client a server) */
    private String nickname;

    /** La password di chi richiede l'operazione (da client a server) */
    private String password;

    /** Il nome di un progetto */
    private String projectName;

    /** Il nome di un membro da aggiungere ad un progetto */
    private String newMember;

    /** Il nome di una carta */
    private String cardName;

    /** La descrizione di una carta */
    private String descrizione;

    /** 
     * Il nome della lista di partenza di un progetto, nel momento in cui 
     * si vuole spostare una carta 
     */
    private String listaPartenza;

    /** 
     * Il nome della lista di destinazione di un progetto, nel momento in cui 
     * si vuole spostare una carta 
     */
    private String listaDestinazione;

    /**
     * Costruttore del server
     */
    public ClientServerMessage() {
    }

    /**
     * Costruttore del client
     * @param comando la richiesta per il server
     */
    public ClientServerMessage(Commands comando) {
        this.comando = comando;
    }

    // ----------------------------------   METODI GETTER E SETTER

    public Commands getComando() {
        return this.comando;
    }

    public Replies getReply() {
        return this.reply;
    }

    public void setReply(Replies reply) {
        this.reply = reply;
    }

    public User getUser() {
        return this.user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public ArrayList<Project> getProjects() {
        return projects;
    }

    public void setProjects(ArrayList<Project> projects) {
        this.projects = projects;
    }

    public ArrayList<String> getMembers() {
        return members;
    }

    public void setMembers(ArrayList<String> members) {
        this.members = members;
    }

    public ArrayList<String> getCards() {
        return cards;
    }

    public void setCards(ArrayList<String> cards) {
        this.cards = cards;
    }

    public Card getCard() {
        return card;
    }

    public void setCard(Card card) {
        this.card = card;
    }

    public String getNickname() {
        return this.nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getProjectName() {
        return this.projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getNewMember() {
        return this.newMember;
    }

    public void setNewMember(String newMember) {
        this.newMember = newMember;
    }

    public String getCardName() {
        return this.cardName;
    }

    public void setCardName(String cardName) {
        this.cardName = cardName;
    }

    public String getDescrizione() {
        return this.descrizione;
    }

    public void setDescrizione(String descrizione) {
        this.descrizione = descrizione;
    }

    public String getListaPartenza() {
        return this.listaPartenza;
    }

    public void setListaPartenza(String listaPartenza) {
        this.listaPartenza = listaPartenza;
    }

    public String getListaDestinazione() {
        return this.listaDestinazione;
    }

    public void setListaDestinazione(String listaDestinazione) {
        this.listaDestinazione = listaDestinazione;
    }
}