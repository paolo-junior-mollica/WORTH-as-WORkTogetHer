import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interfaccia dell'oggetto remoto esportato dal server
 */
public interface WorthInterface extends Remote {

    /**
     * Metodo remoto chiamato da un client per registrarsi a Worth: controlla 
     * che non sia già stato registrato un utente con lo stesso nickname, e 
     * nel caso effettua la registrazione.
     * 
     * @param nickname l'identificativo univoco dell'utente
     * @param password la password dell'utente
     * @return un valore della enum Replies, che informa il client sull'esito 
     *         dell'operazione
     * @throws RemoteException metodo remoto
     */
    Replies register(String nickname, String password) throws RemoteException;

    /**
     * Metodo remoto chiamato da un client per registrarsi al servizio di callback: 
     * controlla che non sia già registrato al servizio. Successivamente lo registra 
     * e manda aggiornamenti ai client sulle liste di utenti e chats (cosicché
     * tutti gli utenti, compreso quello che ha appena fatto la login, riceveranno 
     * le liste di utenti e chats aggiornate).
     * 
     * @param clientStub lo stub dell'oggetto remoto del client
     * @throws RemoteException metodo remoto
     */
    void registerForCallback(NotifyEventInterface clientStub) throws RemoteException;

    /**
     * Metodo remoto chiamato da un client per cancellarsi dal servizio di callback 
     * dopo una logout: cancella la registrazione e invia a tutti gli utenti online 
     * la nuova lista degli utenti (dove l'utente che ha effettuato la cancellazione 
     * dal servizio sarà offline).
     * 
     * @param clientStub lo stub dell'oggetto remoto del client
     * @throws RemoteException metodo remoto
     */
    void unregisterForCallback(NotifyEventInterface clientStub) throws RemoteException;
}
