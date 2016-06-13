package sotechat.service;


import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Service;
import sotechat.controller.SubscribeEventListener;
import sotechat.data.ChatLogger;
import sotechat.domain.Conversation;
import sotechat.domain.Message;
import sotechat.domain.Person;
import sotechat.domainService.ConversationService;
import sotechat.domainService.PersonService;
import sotechat.service.QueueService;
import sotechat.wrappers.MsgToClient;
import sotechat.wrappers.ProStateResponse;
import sotechat.wrappers.UserStateResponse;
import sotechat.data.Mapper;
import sotechat.data.SessionRepo;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.security.Principal;
import java.util.Date;
import java.util.List;

import static sotechat.util.Utils.get;

/**
 * Kayttajan tilan kasittelyyn liittyva logiikka.
 */
@Service
public class StateService {


    /** Mapperilta voi esim. kysya "mika username on ID:lla x?". */
    private final Mapper mapperService;

    /** QueueService. */
    private final QueueService queueService;

    /** Chat Log Service. */
    private final ChatLogger chatLogger;

    /** SubScribeEventHandler. */
    private final SubscribeEventListener subscribeEventListener;

    /** Session Repository. */
    private final SessionRepo sessionRepo;

    /** Database service */
    private final DatabaseService databaseService;

    /** Channel where queue status is broadcasted. */
    public static final String QUEUE_BROADCAST_CHANNEL = "QBCC";

    /** Konstruktori.
     * @param pMapper mapper
     * @param subscribeEventListener subscribeEventListener
     * @param pQueueService queueService
     * @param pChatLogger chatLogger
     * @param pSessionRepo sessionRepo
     */
    @Autowired
    public StateService(
            final Mapper pMapper,
            /* HUOM: Spring ei salli "pSubsc..." tyyppista nimentaa tuolle. */
            final SubscribeEventListener subscribeEventListener,
            final QueueService pQueueService,
            final ChatLogger pChatLogger,
            final SessionRepo pSessionRepo,
            final DatabaseService pDatabaseService
    ) {
        this.mapperService = pMapper;
        this.subscribeEventListener = subscribeEventListener;
        this.queueService = pQueueService;
        this.chatLogger = pChatLogger;
        this.sessionRepo = pSessionRepo;
        this.databaseService = pDatabaseService;
    }

    /** Logiikka miten vastataan customerClientin state requestiin.
     * @param req session tiedot saadaan taalta
     * @param professional kirjautumistiedot - kirjautumaton on null
     * @return JSON-muotoon paketoitu UserStateResponse
     */
    public final synchronized UserStateResponse respondToUserStateRequest(
            final HttpServletRequest req,
            final Principal professional
    ) {
        HttpSession session = mapSession("State req from customer ", req,
                professional);

        /** Kaivetaan sessionista tiedot muuttujiin. */
        String state = get(session, "state");
        String username = get(session, "username");
        String userId = get(session, "userId");
        String categ = get(session, "category");
        String channel = get(session, "channelId");
        /** Note: state on muuttujista ainoa, joka on AINA relevantti.
         Esim. username voi aluksi olla "UNKNOWN" tms. */

        /** Paketoidaan muuttujat StateResponseen, joka kaannetaan JSONiksi. */
        return new UserStateResponse(state, username, userId, categ, channel);
    }


    /**
     * Logiikka miten vastataan proClientin state requestiin.
     * @param req taalta saadaan session tiedot
     * @param professional taalta saadaan autentikaatiotiedot
     * @return JSON-muotoon paketoitu ProStateResponse
     */
    public final synchronized ProStateResponse respondToProStateRequest(
            final HttpServletRequest req,
            final Principal professional
    ) {

        HttpSession session = mapSession("State request from proClient ", req,
                professional);

        /** Kaivetaan sessionista tiedot muuttujiin. */
        String state = get(session, "state");
        String username = get(session, "username");
        String userId = get(session, "userId");
        String qbcc = QUEUE_BROADCAST_CHANNEL;
        String online = "true"; // TODO
        String channelIds = get(session, "channelIds");
        /** Note: ammattilaisella kaikki attribuutit relevantteja aina. */

        System.out.println("Hoitajan kanavat: " + channelIds);

        /** Paketoidaan muuttujat StateResponseen, joka kaannetaan JSONiksi. */
        return new ProStateResponse(
                state, username, userId, qbcc, online, channelIds);
    }

    /**
     * Mappaa Http session sessionId:hen ja varmistaa, että sessionissa on
     * asianmukaiset attribuutit päivittämällä niitä. Palauttaa parametrina
     * annetun HttpServletRequestin Http session.
     * @param print Viesti joka printataan session id:n yhteydessä. Ilmaisee
     *              onko tilapyyntö asiakkaalta vai ammattilaiselta.
     * @param req HttpServletRequest, josta saadaan session tiedot
     * @param professional Principalista saadaan autentikaatiotiedot
     * @return Http sessio
     */
    private final HttpSession mapSession(String print, HttpServletRequest req,
                                         Principal professional){

        HttpSession session = req.getSession();

        System.out.println(print + session.getId());

        sessionRepo.mapHttpSessionToSessionId(session.getId(), session);

        /** Varmistetaan, etta sessionissa on asianmukaiset attribuutit. */
        sessionRepo.updateSessionAttributes(session, professional);

        return session;
    }


    /** Logiikka mita tehdaan, kun tulee pyynto liittya jonoon.
     * @param request taalta saadaan session tiedot
     * @return String "Denied..." tai "OK..."
     * @throws IOException mika poikkeus
     */
    public final synchronized String respondToJoinPoolRequest(
            final HttpServletRequest request
            ) throws Exception {

        HttpSession session = request.getSession();
        /** Tehdaan JSON-objekti clientin lahettamasta JSONista. */
        String jsonString = request.getReader().readLine();
        JsonParser parser = new JsonParser();
        JsonObject payload = parser.parse(jsonString).getAsJsonObject();
        String username = payload.get("username").getAsString();
        String startMessage = payload.get("startMessage").getAsString();
        String channelId = get(session, "channelId");

        /** Debug/devaus tuloste. */
        System.out.println("id(joinPool) = " + session.getId()
                + " , username = " + username);

        /** Tarkistetaan etta aiempi tila on "start". */
        if (!get(session, "state").equals("start")) {
            /** String (ei JSON), jotta AngularJS osaa ohjata fail-metodille. */
            return "Denied join pool request due to bad state.";
        }

        /** Tarkistetaan, ettei nimimerkki ole rekisteroity ammattilaiselle. */
        String id = "" + mapperService.getIdFromRegisteredName(username);
        if (!id.isEmpty() && mapperService.isUserProfessional(id)) {
            /** String (ei JSON) (AngularJS varten) */
            return "Denied join pool request due to reserved username.";
        }
        /** Tarkistetaan, ettei kanavalla ole toista kayttajaa samalla
         * nimimerkilla (olennainen vasta vertaistukichatissa). */
        String channelIdWithPath = "/toClient/chat/" + channelId;
        List<HttpSession> list = subscribeEventListener
                .getSubscribers(channelIdWithPath);
        for (HttpSession other : list) {
            if (get(other, "username").equals(username)) {
                /** String (ei JSON) (AngularJS varten) */
                return "Denied join pool request. Username already on channel.";
            }
        }

        /** Hyvaksytaan kayttajan valitsema nimimerkki. */
        session.setAttribute("username", username);

        /** Mapataan nimimerkki ja kayttajatunnus. */
        String userId = get(session, "userId");
        mapperService.mapUsernameToId(userId, username);

        /** Asetetaan kayttaja jonoon odottamaan palvelua. */
        String category = get(session, "category");
        queueService.addToQueue(channelId, category, username);
        session.setAttribute("state", "queue");

        /** Luodaan tietokantaan uusi keskustelu */
        databaseService.createConversation(startMessage, username, channelId,
                category);

        /** Kirjatataan aloitusviesti kanavan lokeihin. Viestia
         * ei tarvitse viela lahettaa, koska kanavalla ei ole ketaan.
         * Kun kanavalle liittyy joku, lokit lahetetaan sille. */
        String timeStamp = new DateTime().toString();
        MsgToClient msg = new MsgToClient(
                username, channelId, timeStamp, startMessage);
        chatLogger.log(msg);

        /** JSON-muodossa, jotta AngularJS osaa ohjata success-metodille. */
        return "OK, please request new state now.";
    }

    /** Kun meille saapuu pyynto nostaa jonosta chatti.
     * @param channelId kanavaId
     * @param accessor taalta autentikaatiotiedot
     * @return null jos poppaus epaonnistuu,
     *          JSON {"content":"channel activated."} jos poppaus onnistuu.
     */
    public final synchronized String popQueue(
            final String channelId,
            final SimpMessageHeaderAccessor accessor
    ) throws Exception {
        if (queueService.removeFromQueue(channelId) == null) {
            /** Poppaus epaonnistui. Ehtiko joku muu popata samaan aikaan? */
            return "";
        }
        String sessionId =  accessor.getSessionAttributes()
                .get("SPRING.SESSION.ID").toString();
        HttpSession session = sessionRepo.getHttpSession(sessionId);

        /** Lisataan popattu kanava poppaajan kanaviin. */
        addToPopped(sessionId, session, channelId);

        /** Muutetaan popattavan kanavan henkiloiden tilaa. */
        changeParticipantsState(channelId);

        /** Lisätään poppaaja tietokannassa olevaan keskusteluun */
        databaseService.addPersonToConversation(get(session, "userId"), channelId);

        /** Onnistui, palautetaan JSONi. */
        return "{\"content\":\"channel activated.\"}";
    }

    /**
     * Lisataan popattu kanava poppaajan kanaviin
     * @param sessionId session id
     * @param session Http sessio
     * @param channelId kanavan id
     */
    private final void addToPopped(String sessionId, HttpSession session,
                                   String channelId) throws Exception{
        System.out.println("Getting session ID " + sessionId);
        System.out.println("Session is null ? " + (session == null));
        sessionRepo.addChannel(session, channelId);
    }

    /**
     * Muutetaan popattavan kanavan henkiloiden tilaa
     * @param channelId kanavan id
     */
    private final void changeParticipantsState(String channelId)
            throws Exception {
        String channelIdWithPath = "/toClient/queue/" + channelId;

        List<HttpSession> list = subscribeEventListener.
                getSubscribers(channelIdWithPath);

        for (HttpSession member : list) {
            member.setAttribute("state", "chat");
        }
    }

}