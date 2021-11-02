import java.io.Serial;
import java.io.Serializable;
import java.net.InetAddress;
import java.util.ArrayList;

/**
 * Implementazione della chat di un progetto
 */
public class Chat implements Serializable {
    @Serial
    private static final long serialVersionUID = -3061886736036510444L;

    /** Indirizzo multicast della chat */
    private InetAddress address;

    /** Porta per il multicast */
    private int port;

    /** Nome del progetto a cui appartiene la chat */
    private final String project;

    /** 
     * Una lista contenente tutti i messaggi arrivati sulla chat e non ancora 
     * letti 
     */
    private ArrayList<String> messages;

    /**
     * Costruttore
     * 
     * @param address indirizzo multicast della chat
     * @param port    porta per il multicast
     * @param project il nome del progetto a cui appartiene la chat
     */
    public Chat(InetAddress address, int port, String project) {
        this.address = address;
        this.port = port;
        this.project = project;
        this.messages = new ArrayList<>();
    }

    /**
     * Costruttore
     * 
     * @param project il nome del progetto a cui appartiene la chat
     */
    public Chat(String project) {
        this.project = project;
        this.messages = new ArrayList<>();
    }

    public InetAddress getAddress() {
        return this.address;
    }

    public int getPort() {
        return this.port;
    }

    public String getProject() {
        return this.project;
    }

    public ArrayList<String> getMessages() {
        return this.messages;
    }

    @Override
    public String toString() {
        // Creazione di un'unica stringa, con un formato adeguato
        StringBuilder str = new StringBuilder();
        for (String message : this.messages)
            str.append("< ").append(message).append("\n");
        str.append("< " + "Non ci sono altri messaggi");
        // Tutti i messaggi sono stati letti, azzeramento della lista dei messaggi
        this.messages = new ArrayList<>();
        return str.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Chat))
            return false;
        // Due chat sono uguali se appartengono allo stesso progetto (nome del progetto a cui appartengono è uguale)
        return this.project.equals(((Chat) obj).project);
    }
}