import java.io.Serial;
import java.io.Serializable;

/**
 * Implementazione di una carta di un progetto. Le carte sono identificate 
 * univocamente dal loro nome.
 */
public class Card implements Serializable {
    @Serial
    private static final long serialVersionUID = -9102823308447854792L;

    /** Nome della carta */
    private final String name;

    /** Descrizione della carta */
    private final String description;

    /** Storia della carta */
    private String history;

    /**
     * Costruttore
     * 
     * @param name        il nome della carta
     * @param description la descrizione della carta
     */
    public Card(String name, String description) {
        this.name = name;
        this.description = description;
        // Una carta viene creata nella lista TODO
        this.history = "TODO";
    }

    public String getName() {
        return this.name;
    }

    public String getHistory() {
        return this.history;
    }

    /**
     * Metodo per l'aggiornamento della storia della carta, utilizzata nel momento 
     * in cui la carta viene spostata in una nuova lista.
     * 
     * @param newList la nuova lista in cui è stata spostata la carta
     */
    public void updateHistory(String newList) {
        // Concatenazione alla stringa history di una freccia seguita dal nome della nuova lista
        this.history += " -> " + newList.toUpperCase();
    }

    /**
     * Metodo getter
     * 
     * @return la lista in cui si trova la carta
     */
    public String getLocation() {
        // Array contenente tutti i nomi delle liste in cui è stata spostata la carta, in ordine cronologico
        String[] locations = history.split(" -> ");
        // La lista corrente si trova nell'ultima posizione
        return locations[locations.length - 1];
    }

    @Override
    public String toString() {
        return "Nome: " + this.name + ", Descrizione: " + this.description + ", Stato: " + this.getLocation();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Card))
            return false;
        // Due carte sono uguali se hanno lo stesso nome (il nome è un identificativo univoco)
        return this.name.equals(((Card) obj).getName());
    }

}