import java.io.Serial;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

/**
 * Implementazione di un progetto in Worth. I progetti sono identificati
 * univocamente da loro nome.
 */
public class Project implements Serializable {
    @Serial
    private static final long serialVersionUID = -6249673315807452565L;

    /** Il nome del progetto */
    private final String name;

    /** Lista TODO */
    private final ArrayList<Card> toDo;

    /** Lista INPROGRESS */
    private final ArrayList<Card> inProgress;

    /** Lista TOBEREVISED */
    private final ArrayList<Card> toBeRevised;

    /** Lista DONE */
    private final ArrayList<Card> done;

    /**
     * Una lista contenente una copia di tutte le carte che appartengono al 
     * progetto
     */
    private final ArrayList<Card> cards;

    /** I nickname dei membri del progetto */
    private final ArrayList<String> members;

    /** Indirizzo (multicast) della chat */
    private InetAddress chatAddress;

    /** Rappresentazione testuale dell'indirizzo IP multicast */
    private String multicastAddress;

    /** Porta per la chat */
    private int chatPort;

    /**
     * Costruttore
     * 
     * @param name            il nome del progetto
     * @param nickFirstMember il nickname del primo membro del progetto, ovvero
     *                        l'utente che ne ha richiesto la creazione
     */
    public Project(String name, String nickFirstMember) {
        this.name = name;
        this.toDo = new ArrayList<>();
        this.inProgress = new ArrayList<>();
        this.toBeRevised = new ArrayList<>();
        this.done = new ArrayList<>();
        this.cards = new ArrayList<>();
        this.members = new ArrayList<>();
        this.members.add(nickFirstMember);
    }

    public String getName() {
        return this.name;
    }

    public ArrayList<String> getMembers() {
        return this.members;
    }

    public ArrayList<Card> getAllCards() {
        return this.cards;
    }

    /**
     * Metodo per ottenere il riferimento ad una delle 4 liste della chat (TODO,
     * INPROGRESS, TOBEREVISED, DONE)
     * 
     * @param listName il nome della lista
     * @return il riferimento alla lista
     */
    public ArrayList<Card> getList(String listName) {
        ArrayList<Card> list;
        switch (listName.toUpperCase()) {
            case "TODO" -> list = this.getToDo();
            case "INPROGRESS" -> list = this.getInProgress();
            case "TOBEREVISED" -> list = this.getToBeRevised();
            case "DONE" -> list = this.getDone();
            default -> list = null;
        }
        return list;
    }

    public ArrayList<Card> getToDo() {
        return this.toDo;
    }

    public ArrayList<Card> getInProgress() {
        return this.inProgress;
    }

    public ArrayList<Card> getToBeRevised() {
        return this.toBeRevised;
    }

    public ArrayList<Card> getDone() {
        return this.done;
    }

    public InetAddress getChatAddress() {
        return this.chatAddress;
    }

    public String getMulticastAddress() {
        return this.multicastAddress;
    }

    public void setChatAddress(String multicastAddress) {
        this.multicastAddress = multicastAddress;
        try {
            this.chatAddress = InetAddress.getByName(multicastAddress);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public int getChatPort() {
        return this.chatPort;
    }

    public void setChatPort(int chatPort) {
        this.chatPort = chatPort;
    }

    @Override
    public String toString() {
        return this.name;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Project))
            return false;
        // Due progetti sono uguali se hanno lo stesso nome
        return this.name.equals(((Project) obj).getName());
    }
}