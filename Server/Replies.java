/**
 * Risposte del server per notificare al client l'esito di una operazione 
 * richiesta da quest'ultimo. Vengono inclusi dal server in un ClientServerMessage 
 * che viene inviato al client.
 */
public enum Replies {
    OK,                     // Operazione completata con successo
    ALREADY_REGISTERED,     // Utente già registrato
    NOT_REGISTERED,         // Utente non registrato
    WRONG_PASSW,            // Password errata
    ALREADY_ONLINE,         // Utente già online
    PROJECT_EXISTS,         // Il progetto esiste già
    ALREADY_MEMBER,         // L'utente è già membro del progetto
    NONEXISTENT_PROJECT,    // Progetto non esistente tra quelli di cui l'utente è membro
    NONEXISTENT_CARD,       // La carta non esiste nel progetto
    NONEXISTENT_LIST,       // Lista inesistente (non è stata scelta una delle 4 liste dei progetti)
    CARD_EXISTS,            // La carta esiste già
    MOVE_FORBIDDEN,         // Impossibile spostare la carta nella lista scelta (vincolo)
    UNKNOWN_ERROR,          // Errore che non dovrebbe verificarsi mai
    CANCEL_FORBIDDEN,       // Non è possibile cancellare il progetto (non tutte le carte sono nella lista DONE)
    UNABLE_CREATE_PROJECT   // Impossibile creare un progetto perché sono esauriti gli indirizzi multicast a disposizione
}