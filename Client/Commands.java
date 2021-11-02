/**
 * Le richieste che il client può fare al server, includendole in un messaggio di 
 * tipo ClientServerMessage
 */
public enum Commands {
    LOGIN,
    LOGOUT,
    LIST_PROJECTS,
    CREATE_PROJECT,
    ADD_MEMBER,
    SHOW_MEMBERS,
    SHOW_CARDS,
    SHOW_CARD,
    ADD_CARD,
    MOVE_CARD,
    CANCEL_PROJECT
}